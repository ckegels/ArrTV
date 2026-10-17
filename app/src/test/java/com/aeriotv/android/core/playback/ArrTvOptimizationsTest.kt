package com.aeriotv.android.core.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrTvOptimizationsTest {
    @After
    fun reset() {
        ArrTvOptimizations.adaptiveWait = true
        ArrTvOptimizations.waitManySecs = 3
        ArrTvOptimizations.waitOneSecs = 5
        ArrTvOptimizations.waitMaxSecs = 10
    }

    @Test
    fun moreStreamsToGoToMeansAShorterWait() {
        // A channel that usually starts in 2 s
        assertEquals(3_000L, ArrTvOptimizations.pictureWaitMs(2_000L, 4))
        assertEquals(3_000L, ArrTvOptimizations.pictureWaitMs(2_000L, 2))
        assertEquals(5_000L, ArrTvOptimizations.pictureWaitMs(2_000L, 1))
        // Not known: counted as one
        assertEquals(5_000L, ArrTvOptimizations.pictureWaitMs(2_000L, null))
    }

    @Test
    fun aSlowChannelWaitsLongerButNeverPastTheMaximum() {
        assertEquals(9_000L, ArrTvOptimizations.pictureWaitMs(6_000L, 2)) // 1.5x
        assertEquals(10_000L, ArrTvOptimizations.pictureWaitMs(6_000L, 1)) // 2x = 12 s, capped
    }

    @Test
    fun theWaitsFollowTheSettings() {
        ArrTvOptimizations.waitManySecs = 2
        ArrTvOptimizations.waitMaxSecs = 6
        assertEquals(2_000L, ArrTvOptimizations.pictureWaitMs(1_000L, 5))
        assertEquals(6_000L, ArrTvOptimizations.pictureWaitMs(8_000L, 1))
        // Off: the one-other-stream wait for every channel
        ArrTvOptimizations.adaptiveWait = false
        assertEquals(5_000L, ArrTvOptimizations.pictureWaitMs(1_000L, 5))
    }

    @Test
    fun aMaximumBelowATiersMinimumNeverShortensIt() {
        ArrTvOptimizations.waitOneSecs = 8
        ArrTvOptimizations.waitMaxSecs = 5
        assertEquals(8_000L, ArrTvOptimizations.pictureWaitMs(1_000L, 1))
    }
}
