package com.aeriotv.android.core.playback

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackActivityTrackerTest {
    @After
    fun reset() {
        PlaybackActivityTracker.mainPlayingChanged(false)
        PlaybackActivityTracker.fullscreenChanged(false)
    }

    @Test
    fun playingFullScreenIsWatching() {
        PlaybackActivityTracker.mainPlayingChanged(true)
        PlaybackActivityTracker.fullscreenChanged(true)
        assertTrue(PlaybackActivityTracker.watching.value)
    }

    @Test
    fun theCornerPlayerInTheGuideIsNotWatching() {
        PlaybackActivityTracker.mainPlayingChanged(true)
        PlaybackActivityTracker.fullscreenChanged(false)
        assertFalse(PlaybackActivityTracker.watching.value)
    }

    @Test
    fun pausedOrStoppedFullScreenIsNotWatching() {
        PlaybackActivityTracker.fullscreenChanged(true)
        PlaybackActivityTracker.mainPlayingChanged(false)
        assertFalse(PlaybackActivityTracker.watching.value)
    }

    @Test
    fun multiviewIsAlwaysWatching() {
        PlaybackActivityTracker.playerCreated(); PlaybackActivityTracker.playerCreated()
        try {
            assertTrue(PlaybackActivityTracker.watching.value)
            assertTrue(PlaybackActivityTracker.isMultiStreamActive)
        } finally {
            PlaybackActivityTracker.playerReleased(); PlaybackActivityTracker.playerReleased()
        }
        assertFalse(PlaybackActivityTracker.watching.value)
    }
}
