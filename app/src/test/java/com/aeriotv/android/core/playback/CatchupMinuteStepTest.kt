package com.aeriotv.android.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CatchupMinuteStepTest {
    // A programme from 18:35:00, an hour long (epoch ms at 18:35:00 UTC, 2026-10-16)
    private val start = 1_792_175_700_000L
    private val hour = 3_600_000L
    private fun s(seconds: Long) = seconds * 1000

    @Test
    fun aPressForwardPlaysTheNextMinuteNotTheSameOneAgain() {
        // At 18:49:12 (852 s), +30 s used to ask for 18:49:42, and the provider played 18:49 again
        assertEquals(s(900), CatchupMinuteStep.snap(start, s(852), s(882), hour))
        // Right after a step, at 18:50:05, the next press is 18:51
        assertEquals(s(960), CatchupMinuteStep.snap(start, s(905), s(935), hour))
    }

    @Test
    fun aPressBackPlaysThePreviousMinute() {
        assertEquals(s(840), CatchupMinuteStep.snap(start, s(905), s(875), hour))
        assertEquals(s(0), CatchupMinuteStep.snap(start, s(20), s(0), hour))
    }

    @Test
    fun aLongerScrubKeepsItsWholeMinutes() {
        assertEquals(s(1200), CatchupMinuteStep.snap(start, s(852), s(1230), hour))
        assertEquals(s(600), CatchupMinuteStep.snap(start, s(905), s(625), hour))
    }

    @Test
    fun neverPastTheEndOrBeforeTheStart() {
        assertEquals(hour - 5_000L, CatchupMinuteStep.snap(start, hour - 20_000L, hour, hour))
        assertEquals(0L, CatchupMinuteStep.snap(start + 30_000L, s(10), s(0), hour))
    }
}
