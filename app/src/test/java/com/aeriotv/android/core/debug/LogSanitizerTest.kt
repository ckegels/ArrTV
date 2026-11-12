package com.aeriotv.android.core.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What goes into the shareable debug log keeps no Xtream login (LogSanitizer). */
class LogSanitizerTest {
    @Test
    fun anXtreamStreamUrlKeepsItsKindAndIdButNotTheLogin() {
        assertEquals(
            "GET http://line.example.cc/live/***/***/12345.ts",
            LogSanitizer.redact("GET http://line.example.cc/live/tivibridge2/s3cret/12345.ts"),
        )
    }

    @Test
    fun lookBackAndOnDemandUrlsToo() {
        for (kind in listOf("movie", "series", "timeshift")) {
            val out = LogSanitizer.redact("http://h/$kind/someuser/pw/120/2026-10-18:20-00/1.ts")
            assertFalse(out, out.contains("someuser"))
            assertFalse(out, out.contains("/pw/"))
        }
    }
}
