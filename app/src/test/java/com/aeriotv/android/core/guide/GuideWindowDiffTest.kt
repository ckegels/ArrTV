package com.aeriotv.android.core.guide

import com.aeriotv.android.core.data.EPGProgramme
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideWindowDiffTest {
    private val h = 3_600_000L
    private fun p(ch: String, title: String, start: Long, end: Long, sub: String? = null, placeholder: Boolean = false) =
        EPGProgramme(
            channelId = ch, title = title, description = "", startMillis = start, endMillis = end,
            category = "", subTitle = sub, isPlaceholder = placeholder,
        )

    private val current = mapOf(
        "disp:a" to listOf(p("disp:a", "News", 0, h), p("disp:a", "Film", h, 3 * h)),
        "disp:b" to listOf(p("disp:b", "Match", 0, 2 * h)),
    )

    @Test
    fun identicalScheduleIsUnchanged() {
        val fresh = mapOf("disp:a" to current.getValue("disp:a").map { it.copy(description = "new text") })
        assertEquals(emptySet<String>(), GuideWindowDiff.changedChannels(current, fresh, 0, 4 * h))
    }

    @Test
    fun movedRetitledOrAddedProgrammesAreChanges() {
        val fresh = mapOf(
            "disp:a" to listOf(p("disp:a", "News", 0, h), p("disp:a", "Film", h, 2 * h), p("disp:a", "Late", 2 * h, 3 * h)),
            "disp:b" to listOf(p("disp:b", "Match (Live)", 0, 2 * h)),
        )
        assertEquals(setOf("disp:a", "disp:b"), GuideWindowDiff.changedChannels(current, fresh, 0, 4 * h))
    }

    @Test
    fun subtitleChangeIsAChange() {
        val fresh = mapOf("disp:b" to listOf(p("disp:b", "Match", 0, 2 * h, sub = "Final")))
        assertEquals(setOf("disp:b"), GuideWindowDiff.changedChannels(current, fresh, 0, 4 * h))
    }

    @Test
    fun placeholdersOnScreenDoNotCount() {
        val withHoles = mapOf("disp:a" to current.getValue("disp:a") + p("disp:a", "No info", 3 * h, 4 * h, placeholder = true))
        val fresh = mapOf("disp:a" to current.getValue("disp:a"))
        assertEquals(emptySet<String>(), GuideWindowDiff.changedChannels(withHoles, fresh, 0, 4 * h))
    }

    @Test
    fun programmesStraddlingTheWindowStartAreIgnored() {
        // The fetch cut the running programme differently; only its start moved.
        val onScreen = mapOf("disp:a" to listOf(p("disp:a", "Running", -h, h), p("disp:a", "Next", h, 2 * h)))
        val fresh = mapOf("disp:a" to listOf(p("disp:a", "Running", -2 * h, h), p("disp:a", "Next", h, 2 * h)))
        assertEquals(emptySet<String>(), GuideWindowDiff.changedChannels(onScreen, fresh, 0, 4 * h))
    }

    @Test
    fun channelsTheFetchReturnedNothingForAreNotReported() {
        val fresh = mapOf("disp:a" to emptyList<EPGProgramme>())
        assertEquals(emptySet<String>(), GuideWindowDiff.changedChannels(current, fresh, 0, 4 * h))
    }

    @Test
    fun aChannelWithoutGuideOnScreenThatNowHasOneIsChanged() {
        val fresh = mapOf("disp:c" to listOf(p("disp:c", "Show", 0, h)))
        assertEquals(setOf("disp:c"), GuideWindowDiff.changedChannels(current, fresh, 0, 4 * h))
    }

    @Test
    fun programmesPastTheComparedRangeDoNotCount() {
        // The guide on screen ends at 2h; the fetch reaches 4h. Compared over the overlap only.
        val onScreen = mapOf("disp:a" to listOf(p("disp:a", "News", 0, h), p("disp:a", "Film", h, 2 * h)))
        val fresh = mapOf("disp:a" to onScreen.getValue("disp:a") + p("disp:a", "Late", 3 * h, 4 * h))
        assertEquals(emptySet<String>(), GuideWindowDiff.changedChannels(onScreen, fresh, 0, 2 * h))
    }
}
