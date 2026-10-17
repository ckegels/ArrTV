package com.aeriotv.android.core.playback.teletext

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real teletext, decoded: a recorded TS file given with -Dteletext.sample=<path> (and
 * -Dteletext.pid, default 0x102). Skipped without one: broadcast recordings are not kept in the
 * repository. Used to check the decoder against ffmpeg/libzvbi on NPO 1 (2026-10-01).
 */
class TeletextSampleTest {
    @Test
    fun decodesARecording() {
        val path = System.getProperty("teletext.sample") ?: System.getenv("TELETEXT_SAMPLE")
        assumeTrue(path != null && File(path).exists())
        val pid = (System.getProperty("teletext.pid") ?: System.getenv("TELETEXT_PID") ?: "258").toInt()
        val page = (System.getProperty("teletext.page") ?: System.getenv("TELETEXT_PAGE") ?: "888")
        val decoder = TeletextPageDecoder(page[0].digitToInt(), page.substring(1).toInt(16))
        val bytes = File(path!!).readBytes()
        var pes = java.io.ByteArrayOutputStream()
        var pages = 0
        fun flush() {
            val p = pes.toByteArray()
            if (p.size > 9 && p[0].toInt() == 0 && p[1].toInt() == 0 && p[2].toInt() == 1) {
                val headerLength = 9 + (p[8].toInt() and 0xFF)
                for (lines in decoder.feed(p, headerLength, p.size - headerLength)) {
                    pages++
                    println("PAGE $pages: " + lines.joinToString(" / "))
                }
            }
            pes = java.io.ByteArrayOutputStream()
        }
        var at = bytes.indexOf(0x47.toByte())
        while (at + 188 <= bytes.size) {
            if (bytes[at] != 0x47.toByte()) { at++; continue }
            val packetPid = ((bytes[at + 1].toInt() and 0x1F) shl 8) or (bytes[at + 2].toInt() and 0xFF)
            if (packetPid == pid) {
                val start = bytes[at + 1].toInt() and 0x40 != 0
                val adaptation = (bytes[at + 3].toInt() shr 4) and 0x03
                var payload = at + 4
                if (adaptation == 2 || adaptation == 3) payload += 1 + (bytes[at + 4].toInt() and 0xFF)
                if (adaptation != 2 && payload < at + 188) {
                    if (start) flush()
                    pes.write(bytes, payload, at + 188 - payload)
                }
            }
            at += 188
        }
        flush()
        println("PAGES: $pages")
    }
}
