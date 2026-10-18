package com.aeriotv.android.core.playback.teletext

import org.junit.Assert.assertEquals
import org.junit.Test

/** Teletext built by hand, as a broadcaster sends it (EN 300 472 / EN 300 706). */
class TeletextPageDecoderTest {
    private fun reverse(b: Int): Int {
        var r = 0
        for (bit in 0 until 8) if (b and (1 shl bit) != 0) r = r or (1 shl (7 - bit))
        return r
    }

    /** The four data bits at bits 1, 3, 5, 7 (the protection bits are left 0; unread). */
    private fun ham(nibble: Int): Int =
        ((nibble and 1) shl 1) or (((nibble shr 1) and 1) shl 3) or (((nibble shr 2) and 1) shl 5) or (((nibble shr 3) and 1) shl 7)

    private fun unit(magazine: Int, packet: Int, data: IntArray): ByteArray {
        val address = (magazine and 7) or (packet shl 3)
        val raw = IntArray(44)
        raw[0] = 0x00
        raw[1] = 0x27
        raw[2] = ham(address and 0x0F)
        raw[3] = ham(address shr 4)
        data.copyInto(raw, 4)
        return byteArrayOf(0x03, 44) + ByteArray(44) { reverse(raw[it]).toByte() }
    }

    private fun header(magazine: Int, page: Int, erase: Boolean = true, charset: Int = 0, serial: Boolean = false): ByteArray {
        val data = IntArray(40) { 0x20 }
        data[0] = ham(page and 0x0F)
        data[1] = ham(page shr 4)
        for (i in 2..7) data[i] = ham(0)
        data[3] = ham(if (erase) 0x08 else 0)
        data[7] = ham(((charset shl 1) and 0x0E) or (if (serial) 1 else 0))
        return unit(magazine, 0, data)
    }

    private fun row(magazine: Int, number: Int, text: String, boxed: Boolean = true): ByteArray {
        val data = IntArray(40) { 0x20 }
        var at = 0
        if (boxed) { data[at++] = 0x0B; data[at++] = 0x0B }
        for (c in text) data[at++] = c.code
        if (boxed && at < 40) data[at] = 0x0A
        return unit(magazine, number, data)
    }

    private fun pes(vararg units: ByteArray) = byteArrayOf(0x10) + units.fold(ByteArray(0)) { all, u -> all + u }

    @Test
    fun aPageIsFinishedByTheNextHeaderOfItsMagazine() {
        val decoder = TeletextPageDecoder(8, 0x88)
        assertEquals(emptyList<List<String>>(), decoder.feed(pes(header(8, 0x88), row(8, 20, "Het is een oude"), row(8, 22, "betonnen bak."))))
        assertEquals(listOf(listOf("Het is een oude", "betonnen bak.")), decoder.feed(pes(header(8, 0x00))))
    }

    @Test
    fun anEmptyPageClearsAndTheSameTextIsNotSentTwice() {
        val decoder = TeletextPageDecoder(8, 0x88)
        decoder.feed(pes(header(8, 0x88), row(8, 22, "Hallo")))
        assertEquals(listOf(listOf("Hallo")), decoder.feed(pes(header(8, 0x88, erase = false))))
        // The same page again without erasing: its rows stand, nothing new to show
        assertEquals(emptyList<List<String>>(), decoder.feed(pes(header(8, 0x88, erase = false))))
        // Erased and nothing written: the subtitle goes
        decoder.feed(pes(header(8, 0x88, erase = true)))
        assertEquals(listOf(emptyList<String>()), decoder.feed(pes(header(8, 0x00))))
    }

    @Test
    fun otherPagesAndMagazinesAreLeftAlone() {
        val decoder = TeletextPageDecoder(8, 0x88)
        decoder.feed(pes(header(1, 0x00), row(1, 22, "Nieuws"), header(8, 0x00), row(8, 22, "Index")))
        assertEquals(emptyList<List<String>>(), decoder.feed(pes(header(8, 0x01))))
    }

    @Test
    fun onlyTheBoxedTextShowsOnASubtitlePage() {
        val decoder = TeletextPageDecoder(7, 0x77)
        val data = IntArray(40) { 0x20 }
        "junk".forEachIndexed { i, c -> data[i] = c.code }
        data[10] = 0x0B; data[11] = 0x0B
        "Buongiorno".forEachIndexed { i, c -> data[12 + i] = c.code }
        data[22] = 0x0A
        decoder.feed(pes(header(7, 0x77), unit(7, 21, data)))
        assertEquals(listOf(listOf("Buongiorno")), decoder.feed(pes(header(7, 0x00))))
    }

    @Test
    fun theNationalCharacterSet() {
        val german = TeletextPageDecoder(1, 0x50)
        german.feed(pes(header(1, 0x50, charset = 1), row(1, 22, "Gr}~e aus K|ln")))
        assertEquals(listOf(listOf("Grüße aus Köln")), german.feed(pes(header(1, 0x00))))
        val french = TeletextPageDecoder(8, 0x88)
        french.feed(pes(header(8, 0x88, charset = 4), row(8, 22, "Th`atre")))
        assertEquals(listOf(listOf("Thèatre")), french.feed(pes(header(8, 0x00))))
    }

    @Test
    fun brokenDataIsSkipped() {
        val decoder = TeletextPageDecoder(8, 0x88)
        assertEquals(emptyList<List<String>>(), decoder.feed(byteArrayOf(0x10, 0x03, 44, 1, 2, 3)))
        assertEquals(emptyList<List<String>>(), decoder.feed(byteArrayOf(0x20, 0x03)))
        assertEquals(emptyList<List<String>>(), decoder.feed(ByteArray(0)))
    }

    @Test
    fun theDescriptorNamesTheSubtitlePages() {
        // tag 0x56: "dut" initial page 100 (type 1), "dut" subtitles 888 (type 2), "deu" HI 150 (type 5)
        val descriptor = byteArrayOf(0x56, 15) +
            "dut".toByteArray() + byteArrayOf(((1 shl 3) or 1).toByte(), 0x00) +
            "dut".toByteArray() + byteArrayOf(((2 shl 3) or 0).toByte(), 0x88.toByte()) +
            "deu".toByteArray() + byteArrayOf(((5 shl 3) or 1).toByte(), 0x50)
        val pages = teletextSubtitlePages(byteArrayOf(0x0A, 4, 1, 2, 3, 4) + descriptor)
        assertEquals(listOf(TeletextPage("dut", 8, 0x88, false), TeletextPage("deu", 1, 0x50, true)), pages)
        assertEquals("888", pages[0].number)
        assertEquals(emptyList<TeletextPage>(), teletextSubtitlePages(null))
    }
}
