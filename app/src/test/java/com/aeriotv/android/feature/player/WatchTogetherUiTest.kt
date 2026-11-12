package com.aeriotv.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTogetherUiTest {
    @Test
    fun aDeviceIsNamedWithWhoseItIs() {
        assertEquals("Yves · Pixel 9", deviceOfPerson("Pixel 9", "Yves"))
        // One's own device has no person in front
        assertEquals("SHIELD", deviceOfPerson("SHIELD", ""))
    }

    @Test
    fun anUnansweredPopUpWaitsTenSeconds() {
        assertEquals(10, ANSWER_SECONDS)
    }
}
