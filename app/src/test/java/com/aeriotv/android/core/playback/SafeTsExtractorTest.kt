package com.aeriotv.android.core.playback

import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ts.ElementaryStreamReader
import androidx.media3.extractor.ts.TsPayloadReader
import org.junit.Assert.assertEquals
import org.junit.Test

/** A corrupt AC-3 header costs one frame, not the whole load (SafeTsExtractor.kt). */
class SafeTsExtractorTest {
    private class Reader : ElementaryStreamReader {
        var seeks = 0
        var consumed = 0
        var corrupt = false
        override fun seek() { seeks++ }
        override fun createTracks(output: ExtractorOutput, idGenerator: TsPayloadReader.TrackIdGenerator) {}
        override fun packetStarted(pesTimeUs: Long, flags: Int) {}
        override fun consume(data: ParsableByteArray) {
            // What Media3 1.4.1's Ac3Util does with a frame-size code above 37
            if (corrupt) throw ArrayIndexOutOfBoundsException("length=19; index=30")
            consumed += data.bytesLeft()
            data.setPosition(data.limit())
        }
        override fun packetFinished(isEndOfInput: Boolean) {}
    }

    @Test
    fun aCorruptHeaderIsDroppedAndTheReaderResyncs() {
        val inner = Reader()
        val warned = mutableListOf<String>()
        val reader = ResyncingReader(inner, "AC-3") { warned += it }

        reader.consume(ParsableByteArray(ByteArray(10)))
        inner.corrupt = true
        val damaged = ParsableByteArray(ByteArray(10))
        reader.consume(damaged)
        inner.corrupt = false
        reader.consume(ParsableByteArray(ByteArray(10)))

        assertEquals(20, inner.consumed)
        assertEquals(1, inner.seeks)
        // ...the damaged bytes are passed over, not read again
        assertEquals(0, damaged.bytesLeft())
        assertEquals(1, warned.size)
    }
}
