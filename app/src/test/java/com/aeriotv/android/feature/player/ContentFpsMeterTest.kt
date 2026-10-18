package com.aeriotv.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** A measured rate reads as the broadcast rate it is (ContentFpsMeter.snap). */
class ContentFpsMeterTest {
    @Test
    fun aMeasuredRateSnapsToTheNearestStandardOne() {
        assertEquals(59.94f, ContentFpsMeter.snap(59.81f), 0.001f)
        assertEquals(50f, ContentFpsMeter.snap(49.9f), 0.001f)
        assertEquals(25f, ContentFpsMeter.snap(25.2f), 0.001f)
        assertEquals(23.976f, ContentFpsMeter.snap(23.9f), 0.001f)
    }

    @Test
    fun anythingFarFromOneStaysAsMeasuredAndNothingIsZero() {
        assertEquals(15f, ContentFpsMeter.snap(15f), 0.001f)
        assertEquals(0f, ContentFpsMeter.snap(0f), 0.001f)
        assertEquals(0f, ContentFpsMeter.snap(Float.NaN), 0.001f)
    }
}
