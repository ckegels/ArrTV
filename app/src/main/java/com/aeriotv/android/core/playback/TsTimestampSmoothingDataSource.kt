package com.aeriotv.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Catch-up archives with timestamp jumps, made continuous before the extractor sees them.
 *
 * Some providers' archives are stitched from pieces whose timestamps do not follow on: on the
 * user's server (2026-10-02, Prue Leith's Cotswold Kitchen) the video's PTS jumped from 2,469 s
 * to 95,443 s at 2:01 -- the player then waited a day for the next frame and the picture froze --
 * and later jumped back 45 s three times, which dropped the video while the sound played on (a
 * grey screen with audio). Media3's progressive TS path has no discontinuity handling, and its
 * TimestampAdjuster cannot be extended.
 *
 * This reads the stream as 188-byte TS packets and, whenever a stream's PES timestamp lands
 * more than [JUMP_90K] ahead of or [BACK_90K] behind that stream's previous one, shifts
 * everything after it (PTS, DTS and PCR) so it
 * follows on 40 ms later. The pieces are not joined on packet boundaries either (a partial
 * packet at the seam): after one, the next packet start is found again (three sync bytes 188
 * apart) and the bytes between are passed on as they came. Bytes pass through unchanged
 * otherwise.
 */
@UnstableApi
class TsTimestampSmoothingDataSource(private val upstream: DataSource) : DataSource {

    private val input = ByteArray(PACKET * 256)
    private var inputLen = 0
    private val output = ByteArray(PACKET * 256 + PACKET)
    private var outPos = 0
    private var outLen = 0
    private var ended = false
    private val smoother = TsTimestampSmoother()

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        inputLen = 0; outPos = 0; outLen = 0; ended = false
        smoother.reset()
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (outPos == outLen) {
            if (!fill()) return C.RESULT_END_OF_INPUT
        }
        val n = minOf(length, outLen - outPos)
        System.arraycopy(output, outPos, buffer, offset, n)
        outPos += n
        return n
    }

    /** Reads more and moves what can be passed on to [output]; false at the end. */
    private fun fill(): Boolean {
        outPos = 0; outLen = 0
        if (ended) {
            if (inputLen == 0) return false
            System.arraycopy(input, 0, output, 0, inputLen)  // a partial last packet, as it came
            outLen = inputLen; inputLen = 0
            return true
        }
        val n = upstream.read(input, inputLen, input.size - inputLen)
        if (n == C.RESULT_END_OF_INPUT) { ended = true; return fill() }
        inputLen += n
        // Up to here the bytes are done with: packets smoothed, and whatever lay between
        // packets (a seam can cut one short) passed on as it came
        val usable = smoother.scan(input, 0, inputLen)
        if (usable == 0) return true
        System.arraycopy(input, 0, output, 0, usable)
        outLen = usable
        System.arraycopy(input, usable, input, 0, inputLen - usable)
        inputLen -= usable
        return true
    }

    override fun getUri() = upstream.uri

    override fun getResponseHeaders() = upstream.responseHeaders

    override fun close() = upstream.close()

    @UnstableApi
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = TsTimestampSmoothingDataSource(upstream.createDataSource())
    }

    companion object {
        const val PACKET = 188
        const val SYNC = 0x47.toByte()
        /** A jump past this (10 s in 90 kHz ticks) is a seam in the archive, not the programme. */
        const val JUMP_90K = 10L * 90_000L
        /** Going back this far (3 s) is a seam too: B-frames go back well under a second. */
        const val BACK_90K = 3L * 90_000L
    }
}

/** The packet-level work of [TsTimestampSmoothingDataSource], apart for the tests. */
internal class TsTimestampSmoother {
    /** The last timestamp passed on per PID (corrected). Each stream is judged by its own:
     *  audio and video sit up to a second apart, which must not read as a seam. */
    private val lastOut = HashMap<Int, Long>()
    /** Added to every timestamp, in 90 kHz ticks, modulo 2^33; shared, so the streams stay
     *  together. */
    var correction = 0L
        private set

    fun reset() { lastOut.clear(); correction = 0L }

    /**
     * Smooths the packets in b[start, end) and returns how far the bytes are done with: whole
     * packets, and bytes between packets (a seam) passed over. What is left -- a partial packet,
     * or too little to find the next packet start in -- waits for more.
     */
    fun scan(b: ByteArray, start: Int, end: Int): Int {
        val size = TsTimestampSmoothingDataSource.PACKET
        val sync = TsTimestampSmoothingDataSource.SYNC
        var p = start
        while (p + size <= end) {
            if (b[p] == sync) {
                packet(b, p)
                p += size
                continue
            }
            // Out of step: the next place three packets in a row start
            var q = p + 1
            var found = -1
            while (q + 2 * size < end) {
                if (b[q] == sync && b[q + size] == sync && b[q + 2 * size] == sync) { found = q; break }
                q++
            }
            if (found < 0) return maxOf(p, end - 2 * size)
            p = found
        }
        return p
    }

    /** Rewrites the timestamps of the packet at [p] in place. */
    fun packet(b: ByteArray, p: Int) {
        val afc = (b[p + 3].toInt() shr 4) and 3
        var payload = p + 4
        if (afc == 2 || afc == 3) {
            val afLen = b[p + 4].toInt() and 0xFF
            if (afLen > 0 && (b[p + 5].toInt() and 0x10) != 0 && afLen >= 7 && correction != 0L) {
                writePcr(b, p + 6, wrap(readPcr(b, p + 6) + correction))
            }
            payload = p + 5 + afLen
        }
        if (afc == 2 || payload + 14 > p + TsTimestampSmoothingDataSource.PACKET) return
        val unitStart = (b[p + 1].toInt() and 0x40) != 0
        if (!unitStart) return
        if (b[payload].toInt() != 0 || b[payload + 1].toInt() != 0 || b[payload + 2].toInt() != 1) return
        val streamId = b[payload + 3].toInt() and 0xFF
        if (streamId < 0xBD || streamId == 0xBE || streamId == 0xBF) return
        val flags = (b[payload + 7].toInt() shr 6) and 3
        if (flags and 2 == 0) return
        val pid = ((b[p + 1].toInt() and 0x1F) shl 8) or (b[p + 2].toInt() and 0xFF)
        val pts = readTs(b, payload + 9)
        val previous = lastOut[pid]
        if (previous != null) {
            // With the correction so far: a seam leaves this stream more than 10 s ahead or
            // 3 s behind where it was (a B-frame goes back well under a second)
            val step = signed(wrap(pts + correction) - previous)
            if (step > TsTimestampSmoothingDataSource.JUMP_90K || step < -TsTimestampSmoothingDataSource.BACK_90K) {
                // Follow on 40 ms after this stream's last timestamp
                correction = wrap(previous + 3_600L - pts)
            }
        }
        val out = wrap(pts + correction)
        lastOut[pid] = out
        if (correction == 0L) return
        writeTs(b, payload + 9, out)
        if (flags == 3 && payload + 19 <= p + TsTimestampSmoothingDataSource.PACKET) {
            writeTs(b, payload + 14, wrap(readTs(b, payload + 14) + correction))
        }
    }

    companion object {
        private const val WRAP = 1L shl 33

        fun wrap(v: Long) = ((v % WRAP) + WRAP) % WRAP

        /** A 33-bit difference as a signed step. */
        fun signed(d: Long): Long {
            val w = wrap(d)
            return if (w >= WRAP / 2) w - WRAP else w
        }

        fun readTs(b: ByteArray, o: Int): Long =
            (((b[o].toLong() shr 1) and 0x07) shl 30) or
                ((b[o + 1].toLong() and 0xFF) shl 22) or
                (((b[o + 2].toLong() and 0xFF) shr 1) shl 15) or
                ((b[o + 3].toLong() and 0xFF) shl 7) or
                ((b[o + 4].toLong() and 0xFF) shr 1)

        fun writeTs(b: ByteArray, o: Int, v: Long) {
            b[o] = ((b[o].toInt() and 0xF0) or (((v shr 29) and 0x0E).toInt()) or 1).toByte()
            b[o + 1] = ((v shr 22) and 0xFF).toByte()
            b[o + 2] = ((((v shr 14) and 0xFE).toInt()) or 1).toByte()
            b[o + 3] = ((v shr 7) and 0xFF).toByte()
            b[o + 4] = ((((v shl 1) and 0xFE).toInt()) or 1).toByte()
        }

        fun readPcr(b: ByteArray, o: Int): Long =
            ((b[o].toLong() and 0xFF) shl 25) or
                ((b[o + 1].toLong() and 0xFF) shl 17) or
                ((b[o + 2].toLong() and 0xFF) shl 9) or
                ((b[o + 3].toLong() and 0xFF) shl 1) or
                ((b[o + 4].toLong() and 0xFF) shr 7)

        fun writePcr(b: ByteArray, o: Int, v: Long) {
            b[o] = ((v shr 25) and 0xFF).toByte()
            b[o + 1] = ((v shr 17) and 0xFF).toByte()
            b[o + 2] = ((v shr 9) and 0xFF).toByte()
            b[o + 3] = ((v shr 1) and 0xFF).toByte()
            b[o + 4] = (((v and 1).toInt() shl 7) or (b[o + 4].toInt() and 0x7F)).toByte()
        }
    }
}
