package com.aeriotv.android.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InStepCatchUpTest {
    private val minute = 60_000L

    @Test
    fun fiveSecondsApartIsInStep() {
        val c = InStepCatchUp()
        assertFalse(c.decide("ch", 5_000L, 0L))
        assertFalse(c.decide("ch", 5_500L, 10_000L))
    }

    @Test
    fun aStallIsCaughtUpOnce() {
        val c = InStepCatchUp()
        assertTrue(c.decide("ch", 30_000L, 0L))
        // Afterwards the device is where a fresh connection puts it: in step
        assertFalse(c.decide("ch", 2_000L, 30_000L))
    }

    @Test
    fun aDelayOfTheDevicesOwnIsNotChasedEveryThirtySeconds() {
        // The Pixel: every fresh tune lands 7 s behind again
        val c = InStepCatchUp()
        assertTrue(c.decide("ch", 7_000L, 0L))
        assertFalse(c.decide("ch", 7_000L, 30_000L))
        assertFalse(c.decide("ch", 7_000L, 10 * minute))
        // A new channel looks afresh
        assertTrue(c.decide("other", 7_000L, 11 * minute))
    }

    @Test
    fun neverTwiceWithinTwoMinutes() {
        val c = InStepCatchUp()
        assertTrue(c.decide("ch", 30_000L, 0L))
        assertFalse(c.decide("ch", 2_000L, 30_000L))
        assertFalse(c.decide("ch", 20_000L, 60_000L))
        assertTrue(c.decide("ch", 20_000L, 3 * minute))
    }
}
