package com.aeriotv.android.core.network

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The start card's keys: OK opens the list, Back puts the card away, the rest pass. */
class WatchTogetherHintTest {
    private val offer = DispatcharrClient.LinkOffer("shield-1", "SHIELD", "u1", "TF1", user = "ckegels")

    @After
    fun reset() {
        WatchTogether.hint.value = null
        WatchTogether.highlight.value = false
        WatchTogether.sheetOpen.value = false
    }

    @Test
    fun okOpensTheListAndItsReleaseGoesNowhere() {
        WatchTogether.showHint(offer)
        assertTrue(WatchTogether.highlight.value)
        assertTrue(WatchTogether.hintKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertNull(WatchTogether.hint.value)
        assertTrue(WatchTogether.sheetOpen.value)
        assertTrue(WatchTogether.hintKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        // The next OK is the player's again
        assertFalse(WatchTogether.hintKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
    }

    @Test
    fun backPutsTheCardAwayWithoutLeavingTheScreen() {
        WatchTogether.showHint(offer)
        assertTrue(WatchTogether.hintKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        assertNull(WatchTogether.hint.value)
        assertFalse(WatchTogether.sheetOpen.value)
        assertTrue(WatchTogether.hintKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
    }

    @Test
    fun everyOtherKeyAndNoCardPassThrough() {
        WatchTogether.showHint(offer)
        assertFalse(WatchTogether.hintKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0))
        assertFalse(WatchTogether.hintKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CHANNEL_UP, 0))
        WatchTogether.hint.value = null
        assertFalse(WatchTogether.hintKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertFalse(WatchTogether.hintKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
    }
}
