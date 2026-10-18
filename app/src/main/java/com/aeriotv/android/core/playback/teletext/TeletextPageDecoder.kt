package com.aeriotv.android.core.playback.teletext

/**
 * Turns the teletext carried in a DVB stream (EN 300 472) into the text of one subtitle page
 * (EN 300 706): page 888 on Dutch and Belgian channels, 777 on Italian, 150 on German and
 * Austrian ones, and whichever page the stream's teletext descriptor names as a subtitle page.
 *
 * Media3 decodes DVB subtitles and CEA-608/708 captions, but has no teletext decoder; without
 * this, subtitles most European broadcasters send were simply not shown (arrTV, 2026-10-01).
 *
 * No Android here: a PES payload goes in, finished pages come out, so it is tested on plain
 * bytes. A page is finished when the next page header of its magazine arrives (that is how
 * teletext ends a page); a page that comes back empty clears the subtitle.
 */
class TeletextPageDecoder(
    /** 1..8 (0 in the descriptor is magazine 8). */
    private val magazine: Int,
    /** The page within the magazine as its two BCD digits, 0x88 for page 888. */
    private val page: Int,
) {
    private val rows = arrayOfNulls<String>(25)
    private var collecting = false
    private var charset = 0
    private var lastEmitted: List<String>? = null

    fun reset() {
        rows.fill(null)
        collecting = false
        lastEmitted = null
    }

    /**
     * Feed one PES payload: the data_identifier byte, then 46-byte data units. Returns the
     * pages it finished, each as its lines top to bottom (empty: the subtitle is cleared).
     */
    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<List<String>> {
        val finished = mutableListOf<List<String>>()
        val end = offset + length
        if (length < 1) return finished
        val identifier = data[offset].toInt() and 0xFF
        if (identifier !in 0x10..0x1F) return finished
        var at = offset + 1
        while (at + 2 <= end) {
            val unitId = data[at].toInt() and 0xFF
            val unitLength = data[at + 1].toInt() and 0xFF
            val unitStart = at + 2
            if (unitStart + unitLength > end) break
            if ((unitId == 0x02 || unitId == 0x03) && unitLength == 44) {
                packet(data, unitStart, finished)
            }
            at = unitStart + unitLength
        }
        return finished
    }

    private fun packet(data: ByteArray, start: Int, finished: MutableList<List<String>>) {
        // Transmitted least significant bit first: every byte reversed
        fun byteAt(i: Int) = REVERSED[data[start + i].toInt() and 0xFF]
        if (byteAt(1) != 0x27) return // the framing code
        val address = (unham(byteAt(3)) shl 4) or unham(byteAt(2))
        val packetMagazine = (address and 0x07).let { if (it == 0) 8 else it }
        val packetNumber = (address shr 3) and 0x1F
        if (packetNumber == 0) {
            header(packetMagazine, ::byteAt, finished)
        } else if (packetNumber in 1..23 && packetMagazine == magazine && collecting) {
            rows[packetNumber] = row { byteAt(4 + it) }
        }
    }

    private fun header(packetMagazine: Int, byteAt: (Int) -> Int, finished: MutableList<List<String>>) {
        val units = unham(byteAt(4))
        val tens = unham(byteAt(5))
        val headerPage = (tens shl 4) or units
        val serial = unham(byteAt(11)) and 0x01 == 1 // C11: magazines sent one after another
        // A header ends the page being collected: always in its own magazine, and in every
        // magazine when they are sent one after another
        if (collecting && (packetMagazine == magazine || serial)) {
            complete(finished)
        }
        if (packetMagazine != magazine) return
        if (headerPage == page) {
            collecting = true
            if (unham(byteAt(7)) and 0x08 != 0) rows.fill(null) // C4: erase page
            val c = unham(byteAt(11))
            charset = ((c and 0x08) or (c and 0x04) or (c and 0x02)) shr 1 // C12-C14
        }
    }

    private fun complete(finished: MutableList<List<String>>) {
        collecting = false
        val lines = (1..23).mapNotNull { rows[it]?.takeIf(String::isNotBlank) }
        if (lines != lastEmitted) {
            finished += lines
            lastEmitted = lines
        }
    }

    /** One row of 40 characters. On a subtitle page only what is inside a box shows. */
    private fun row(at: (Int) -> Int): String {
        val chars = IntArray(40) { at(it) and 0x7F } // odd parity bit dropped
        val boxed = chars.indexOf(START_BOX) >= 0
        val text = StringBuilder()
        var inBox = !boxed
        for (c in chars) {
            when {
                c == START_BOX -> inBox = true
                c == END_BOX -> { if (boxed) inBox = false }
                c < 0x20 -> if (inBox) text.append(' ')
                inBox -> text.append(character(c))
            }
        }
        return text.toString().replace(Regex(" {2,}"), " ").trim()
    }

    private fun character(c: Int): Char {
        val position = NATIONAL_POSITIONS.indexOf(c)
        if (position >= 0) {
            val set = NATIONAL_SETS[charset] ?: NATIONAL_SETS[0]!!
            return set[position]
        }
        return if (c == 0x7F) ' ' else c.toChar()
    }

    companion object {
        private const val START_BOX = 0x0B
        private const val END_BOX = 0x0A

        private val REVERSED = IntArray(256) { b ->
            var r = 0
            for (bit in 0 until 8) if (b and (1 shl bit) != 0) r = r or (1 shl (7 - bit))
            r
        }

        /** Hamming 8/4: the four data bits sit at bits 1, 3, 5 and 7. */
        fun unham(b: Int): Int =
            ((b shr 1) and 1) or (((b shr 3) and 1) shl 1) or (((b shr 5) and 1) shl 2) or (((b shr 7) and 1) shl 3)

        /** The 13 places where the national option sub-sets differ from ASCII (EN 300 706 table 36). */
        private val NATIONAL_POSITIONS = intArrayOf(0x23, 0x24, 0x40, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F, 0x60, 0x7B, 0x7C, 0x7D, 0x7E)

        /** By C12-C14; Dutch broadcasters use the English set, Belgian ones English or French. */
        private val NATIONAL_SETS: Map<Int, CharArray> = mapOf(
            0 to "£\$@←½→↑#—¼‖¾÷".toCharArray(), // English
            1 to "#\$§ÄÖÜ^_°äöüß".toCharArray(), // German
            2 to "#¤ÉÄÖÅÜ_éäöåü".toCharArray(), // Swedish / Finnish / Hungarian
            3 to "£\$é°ç→↑#ùàòèì".toCharArray(), // Italian
            4 to "éïàëêùî#èâôûç".toCharArray(), // French
            5 to "ç\$¡áéíóú¿üñèà".toCharArray(), // Portuguese / Spanish
        )
    }
}
