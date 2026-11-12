package com.aeriotv.android.feature.player

import com.aeriotv.android.core.network.LiveCaption
import com.aeriotv.android.core.network.LiveCaptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Generated captions: which channel, which line at which stream time, what to say instead. */
class GeneratedCaptionsTest {
    private val cues = listOf(
        LiveCaption(1, 100.0, 102.0, "Goedenavond"),
        LiveCaption(2, 102.5, 105.0, "en welkom"),
        LiveCaption(3, 110.0, 111.0, "bij het nieuws"),
    )

    @Test
    fun theChannelComesFromTheLiveProxyUrl() {
        assertEquals("e5ed1189-69bb", generatedCaptionsChannelUuid("http://s:9191/proxy/ts/stream/e5ed1189-69bb?x=1"))
        assertNull(generatedCaptionsChannelUuid("http://s/live/u/p/123.ts"))
        assertNull(generatedCaptionsChannelUuid(null))
    }

    @Test
    fun aLineShowsWhileThePictureHasItsTime() {
        assertEquals("Goedenavond", captionLineAt(cues, 101.0))
        // The next line replaces the one before at once, never both
        assertEquals("en welkom", captionLineAt(cues, 102.7))
        assertEquals("en welkom", captionLineAt(cues, 105.9))
        assertEquals("", captionLineAt(cues, 108.0))
        assertEquals("", captionLineAt(emptyList(), 101.0))
    }

    @Test
    fun withoutAStreamTimeTheNewestLine() {
        assertEquals("bij het nieuws", captionLineAt(cues, null))
    }

    @Test
    fun whatIsSaidInstead() {
        assertEquals("", captionStatusLine(LiveCaptions(state = "listening")))
        assertEquals("Captions: loading the speech model…", captionStatusLine(LiveCaptions(state = "loading model")))
        assertEquals("Busy now", captionStatusLine(LiveCaptions(state = "busy", reason = "Busy now")))
        assertEquals("Captions: no sound", captionStatusLine(LiveCaptions(state = "error", error = "no sound")))
    }

    @Test
    fun eachLanguageHasItsOwnMenuEntry() {
        val languages = com.aeriotv.android.core.playback.CaptionLanguage.CHOICES
        assertEquals(GENERATED_CAPTIONS_ID, generatedCaptionsId(0))
        languages.indices.forEach { assertEquals(it, generatedCaptionsLanguageIndex(generatedCaptionsId(it))) }
        // Track ids are 0 and up; other negative ids are not captions
        assertEquals(-1, generatedCaptionsLanguageIndex(0))
        assertEquals(-1, generatedCaptionsLanguageIndex(generatedCaptionsId(languages.size)))
    }
}
