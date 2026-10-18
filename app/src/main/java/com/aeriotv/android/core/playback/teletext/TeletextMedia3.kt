@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.aeriotv.android.core.playback.teletext

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.ElementaryStreamReader
import androidx.media3.extractor.ts.PesReader
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsPayloadReader
import com.google.common.collect.ImmutableList

/**
 * Teletext subtitles for Media3, which has no teletext decoder of its own (see
 * TeletextPageDecoder). Three parts, each built like Media3's own DVB subtitle support:
 *
 * - [TeletextPayloadReaderFactory] recognises a teletext stream in the PMT (stream type 6 with a
 *   teletext descriptor, tag 0x56) and gives each subtitle page it lists (types 2 and 5) a text
 *   track; every other stream goes to Media3's own factory, unchanged.
 * - [TeletextReader] passes each PES payload of that stream on as one sample.
 * - [TeletextSubtitleParserFactory] turns those samples into cues while the stream is read
 *   (Media3's "parse subtitles during extraction", which TsExtractor does for every text track),
 *   so the text renderer shows them as it shows DVB subtitles.
 *
 * [teletextTsExtractor] builds the TsExtractor exactly as DefaultExtractorsFactory does in
 * Media3 1.4.1 (single PMT, text transcoding on, its own subtitle parsers, the same timestamp
 * search), with these two added.
 */

const val TELETEXT_MIME = "application/x-arrtv-teletext"
private const val TELETEXT_DESCRIPTOR = 0x56
private const val STREAM_TYPE_PRIVATE = 0x06
// What DefaultExtractorsFactory gives TsExtractor (Media3 1.4.1)
private const val TS_TIMESTAMP_SEARCH_BYTES = 112800

/** A subtitle page a teletext descriptor lists. */
data class TeletextPage(val language: String, val magazine: Int, val page: Int, val hearingImpaired: Boolean) {
    /** As people know it: "888". */
    val number: String get() = "$magazine${"%02X".format(page)}"
}

/** The subtitle pages in an elementary stream's descriptor loop (EN 300 468 6.2.43). */
fun teletextSubtitlePages(descriptors: ByteArray?): List<TeletextPage> {
    val bytes = descriptors ?: return emptyList()
    val pages = mutableListOf<TeletextPage>()
    var at = 0
    while (at + 2 <= bytes.size) {
        val tag = bytes[at].toInt() and 0xFF
        val length = bytes[at + 1].toInt() and 0xFF
        val end = minOf(bytes.size, at + 2 + length)
        if (tag == TELETEXT_DESCRIPTOR) {
            var entry = at + 2
            while (entry + 5 <= end) {
                val language = String(bytes, entry, 3, Charsets.ISO_8859_1)
                val typeAndMagazine = bytes[entry + 3].toInt() and 0xFF
                val type = typeAndMagazine shr 3
                val magazine = (typeAndMagazine and 0x07).let { if (it == 0) 8 else it }
                val page = bytes[entry + 4].toInt() and 0xFF
                if (type == 0x02 || type == 0x05) {
                    pages += TeletextPage(language, magazine, page, hearingImpaired = type == 0x05)
                }
                entry += 5
            }
        }
        at = end
    }
    return pages
}

class TeletextPayloadReaderFactory(
    private val delegate: TsPayloadReader.Factory = DefaultTsPayloadReaderFactory(0, ImmutableList.of()),
) : TsPayloadReader.Factory {
    override fun createInitialPayloadReaders() = delegate.createInitialPayloadReaders()

    override fun createPayloadReader(streamType: Int, esInfo: TsPayloadReader.EsInfo): TsPayloadReader? {
        if (streamType == STREAM_TYPE_PRIVATE) {
            val pages = teletextSubtitlePages(esInfo.descriptorBytes)
            if (pages.isNotEmpty()) return PesReader(TeletextReader(pages))
        }
        return delegate.createPayloadReader(streamType, esInfo)
    }
}

/** One text track per subtitle page; every PES payload of the stream is a sample of each. */
class TeletextReader(private val pages: List<TeletextPage>) : ElementaryStreamReader {
    private val outputs = arrayOfNulls<TrackOutput>(pages.size)
    private var writingSample = false
    private var sampleBytesWritten = 0
    private var sampleTimeUs = C.TIME_UNSET

    override fun seek() {
        writingSample = false
        sampleTimeUs = C.TIME_UNSET
    }

    override fun createTracks(extractorOutput: ExtractorOutput, idGenerator: TsPayloadReader.TrackIdGenerator) {
        pages.forEachIndexed { i, page ->
            idGenerator.generateNewId()
            val output = extractorOutput.track(idGenerator.trackId, C.TRACK_TYPE_TEXT)
            output.format(
                Format.Builder()
                    .setId(idGenerator.formatId)
                    .setSampleMimeType(TELETEXT_MIME)
                    .setLanguage(page.language)
                    .setLabel("Teletext ${page.number}")
                    .setRoleFlags(if (page.hearingImpaired) C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND else C.ROLE_FLAG_SUBTITLE)
                    .setInitializationData(listOf(byteArrayOf(page.magazine.toByte(), page.page.toByte())))
                    .build(),
            )
            outputs[i] = output
        }
    }

    override fun packetStarted(pesTimeUs: Long, flags: Int) {
        // Every teletext PES starts a page's data (EN 300 472), aligned or not
        writingSample = true
        sampleTimeUs = pesTimeUs
        sampleBytesWritten = 0
    }

    override fun consume(data: ParsableByteArray) {
        if (!writingSample || data.bytesLeft() == 0) return
        if (sampleBytesWritten == 0) {
            // A teletext PES payload starts with an EBU data identifier, 0x10-0x1F
            val identifier = data.data[data.position].toInt() and 0xFF
            if (identifier !in 0x10..0x1F) {
                writingSample = false
                return
            }
        }
        val start = data.position
        val available = data.bytesLeft()
        for (output in outputs) {
            data.position = start
            output!!.sampleData(data, available)
        }
        sampleBytesWritten += available
    }

    override fun packetFinished(isEndOfInput: Boolean) {
        if (!writingSample) return
        if (sampleTimeUs != C.TIME_UNSET && sampleBytesWritten > 0) {
            for (output in outputs) {
                output!!.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleBytesWritten, 0, null)
            }
        }
        writingSample = false
    }
}

/** Finished pages as cues: each replaces the one before; an empty page clears the screen. */
class TeletextSubtitleParser(format: Format) : SubtitleParser {
    private val decoder: TeletextPageDecoder = format.initializationData.firstOrNull()
        ?.takeIf { it.size >= 2 }
        ?.let { TeletextPageDecoder(it[0].toInt() and 0xFF, it[1].toInt() and 0xFF) }
        ?: TeletextPageDecoder(8, 0x88)

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        // Damaged or unusual teletext must never stop the picture: a page that cannot be read
        // is skipped, and the channel plays on
        val pages = try {
            decoder.feed(data, offset, length)
        } catch (e: RuntimeException) {
            emptyList()
        }
        for (lines in pages) {
            val cues = if (lines.isEmpty()) emptyList() else listOf(
                Cue.Builder().setText(lines.joinToString("\n")).build(),
            )
            // As Media3's DVB parser: the time is the sample's own
            output.accept(CuesWithTiming(cues, C.TIME_UNSET, C.TIME_UNSET))
        }
    }

    override fun reset() = decoder.reset()

    override fun getCueReplacementBehavior(): Int = Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
}

class TeletextSubtitleParserFactory(
    private val delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
) : SubtitleParser.Factory {
    override fun supportsFormat(format: Format) =
        format.sampleMimeType == TELETEXT_MIME || delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format) =
        if (format.sampleMimeType == TELETEXT_MIME) Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
        else delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        if (format.sampleMimeType == TELETEXT_MIME) TeletextSubtitleParser(format) else delegate.create(format)
}

/** TsExtractor as DefaultExtractorsFactory builds it (Media3 1.4.1), with teletext added.
 *  [adjuster] is passed in so the player can read the stream time it maps samples from
 *  (generated captions are timed by it, AerioExoPlayerHolder.streamTimeNowSeconds). */
fun teletextTsExtractor(adjuster: TimestampAdjuster = TimestampAdjuster(0)): Extractor = TsExtractor(
    TsExtractor.MODE_SINGLE_PMT,
    0, // text transcoding on, as DefaultExtractorsFactory's default
    TeletextSubtitleParserFactory(),
    adjuster,
    TeletextPayloadReaderFactory(),
    TS_TIMESTAMP_SEARCH_BYTES,
)

/** TsExtractor exactly as DefaultExtractorsFactory builds it (Media3 1.4.1), only with the
 *  [adjuster] given -- for generated captions with teletext subtitles switched off. */
fun plainTsExtractor(adjuster: TimestampAdjuster): Extractor = TsExtractor(
    TsExtractor.MODE_SINGLE_PMT,
    0,
    DefaultSubtitleParserFactory(),
    adjuster,
    DefaultTsPayloadReaderFactory(0, ImmutableList.of()),
    TS_TIMESTAMP_SEARCH_BYTES,
)
