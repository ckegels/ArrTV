package com.aeriotv.android.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InStepTest {

    /** One 188-byte TS packet starting a PES of [streamId] with [pts]. */
    private fun pesPacket(pid: Int, streamId: Int, pts: Long): ByteArray {
        val p = ByteArray(188) { 0xFF.toByte() }
        p[0] = 0x47
        p[1] = (0x40 or (pid shr 8)).toByte()
        p[2] = pid.toByte()
        p[3] = 0x10 // payload only
        val pes = byteArrayOf(0, 0, 1, streamId.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5,
            (0x21 or ((pts shr 29).toInt() and 0x0E)).toByte(),
            (pts shr 22).toByte(),
            (((pts shr 14).toInt() and 0xFE) or 1).toByte(),
            (pts shr 7).toByte(),
            (((pts shl 1).toInt() and 0xFE) or 1).toByte())
        System.arraycopy(pes, 0, p, 4, pes.size)
        return p
    }

    private fun nullPacket(): ByteArray = ByteArray(188).also { it[0] = 0x47; it[1] = 0x1F; it[2] = 0xFF.toByte(); it[3] = 0x10 }

    @Test
    fun theFirstMediaTimestampIsFoundAcrossReads() {
        val stream = nullPacket() + nullPacket() + pesPacket(0x100, 0xE0, 8_589_000_000L) + pesPacket(0x101, 0xC0, 5L) + nullPacket()
        val finder = FirstPtsFinder()
        // Arrives in pieces, mid-packet
        assertNull(finder.feed(stream, 0, 300))
        assertEquals(8_589_000_000L, finder.feed(stream, 300, stream.size - 300))
    }

    @Test
    fun foundWhenTheStreamStartsMidPacketAndArrivesInTinyReads() {
        val stream = ByteArray(77) { 0x11 } + nullPacket() + nullPacket() + nullPacket() +
            pesPacket(0x100, 0xE0, 123_456_789L) + nullPacket()
        val finder = FirstPtsFinder()
        var found: Long? = null
        var i = 0
        while (i < stream.size && found == null) {
            val n = minOf(50, stream.size - i)
            found = finder.feed(stream, i, n)
            i += n
        }
        assertEquals(123_456_789L, found)
    }

    @Test
    fun givesUpAfterAMegabyteWithoutATimestamp() {
        val nothing = ByteArray(188 * 1000).also { b -> for (k in b.indices step 188) { b[k] = 0x47; b[k + 1] = 0x1F; b[k + 2] = 0xFF.toByte() } }
        val finder = FirstPtsFinder()
        repeat(6) { finder.feed(nothing, 0, nothing.size) }
        assertEquals(true, finder.done)
    }

    @Test
    fun aheadIsHowMuchFurtherThisDeviceShows() {
        // Both measured at the same moment, this one 30 s further on
        assertEquals(30_000L, InStep.aheadMs(90_000L * 100, 1_000L, 90_000L * 70, 1_000L))
        // The other measured 2 s earlier: projected forward
        assertEquals(28_000L, InStep.aheadMs(90_000L * 100, 3_000L, 90_000L * 70, 1_000L))
        assertEquals(-30_000L, InStep.aheadMs(90_000L * 70, 1_000L, 90_000L * 100, 1_000L))
    }

    @Test
    fun theTimestampWrapEveryTwentySixHoursIsNoJump() {
        val wrap = 1L shl 33
        assertEquals(2_000L, InStep.aheadMs(90L * 1_000, 0L, wrap - 90L * 1_000, 0L))
        assertEquals(90L * 500, InStep.shown(wrap - 90L * 500, 1_000L))
    }
}
