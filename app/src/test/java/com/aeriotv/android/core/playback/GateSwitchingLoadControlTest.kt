package com.aeriotv.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A learned start gate changes on the running player, without a rebuild (from AerioTV 577ab585). */
class GateSwitchingLoadControlTest {
    private fun control() = GateSwitchingLoadControl(2_000) { gateMs, allocator ->
        DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(gateMs + 4_000, (gateMs + 4_000) * 2, gateMs, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    private fun buffered(seconds: Long) = LoadControl.Parameters(
        PlayerId.UNSET, Timeline.EMPTY, MediaSource.MediaPeriodId(Any()),
        /* playbackPositionUs = */ 0, /* bufferedDurationUs = */ seconds * 1_000_000,
        /* playbackSpeed = */ 1f, /* playWhenReady = */ true, /* rebuffering = */ false,
        /* targetLiveOffsetUs = */ C.TIME_UNSET,
    )

    @Test
    fun theNextStreamStartsWithTheGateAskedFor() {
        val load = control()
        assertTrue(load.shouldStartPlayback(buffered(3)))
        load.requestGate(8_000)
        assertFalse(load.shouldStartPlayback(buffered(3)))
        assertTrue(load.shouldStartPlayback(buffered(9)))
        load.requestGate(2_000)
        assertTrue(load.shouldStartPlayback(buffered(3)))
    }

    @Test
    fun everyGateSharesOneAllocator() {
        val load = control()
        val first = load.allocator
        load.requestGate(8_000)
        load.shouldStartPlayback(buffered(1))
        assertSame(first, load.allocator)
    }
}
