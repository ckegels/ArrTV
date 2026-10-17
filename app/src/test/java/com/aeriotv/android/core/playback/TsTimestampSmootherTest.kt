package com.aeriotv.android.core.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Catch-up archives with timestamp seams are made continuous (TsTimestampSmoothingDataSource). */
class TsTimestampSmootherTest {
    private fun pesPacket(pts: Long, streamId: Int = 0xE0): ByteArray {
        val b = ByteArray(188) { 0xFF.toByte() }
        b[0] = 0x47; b[1] = 0x41; b[2] = 0x00; b[3] = 0x10  // payload only, unit start, PID 0x100
        b[4] = 0; b[5] = 0; b[6] = 1; b[7] = streamId.toByte()
        b[8] = 0; b[9] = 0; b[10] = 0x80.toByte(); b[11] = 0x80.toByte(); b[12] = 5
        b[13] = 0x21
        TsTimestampSmoother.writeTs(b, 13, pts)
        return b
    }

    private fun ptsOf(b: ByteArray) = TsTimestampSmoother.readTs(b, 13)

    @Test
    fun aJumpIsSmoothedAndWhatFollowsKeepsItsSpacing() {
        val s = TsTimestampSmoother()
        val ticks = listOf(222_000_000L, 222_003_600L, 8_589_000_000L, 8_589_003_600L, 8_585_000_000L, 8_585_003_600L)
        val out = ticks.map { pesPacket(it).also { p -> s.packet(p, 0) }.let(::ptsOf) }
        assertEquals(listOf(222_000_000L, 222_003_600L, 222_007_200L, 222_010_800L, 222_014_400L, 222_018_000L), out)
    }

    @Test
    fun aStreamWithoutJumpsIsUntouched() {
        val s = TsTimestampSmoother()
        val p = pesPacket(900_000L); s.packet(p, 0)
        val q = pesPacket(990_000L); s.packet(q, 0)
        assertEquals(990_000L, ptsOf(q))
        assertEquals(0L, s.correction)
    }

    /** TS_SAMPLE=<in.ts> TS_OUT=<out.ts>: the real archive through the smoother, for ffprobe. */
    @Test
    fun realArchive() {
        val input = System.getenv("TS_SAMPLE")?.let(::File)
        assumeTrue(input != null && input.exists())
        val bytes = input!!.readBytes()
        val s = TsTimestampSmoother()
        var done = 0
        while (done < bytes.size) {
            val next = s.scan(bytes, done, minOf(bytes.size, done + 188 * 256))
            if (next <= done) break
            done = next
        }
        File(System.getenv("TS_OUT")).writeBytes(bytes)
    }
}
