package com.aeriotv.android.core.preferences

import com.aeriotv.android.core.remote.GuideRemoteAction
import com.aeriotv.android.core.remote.RemoteControlMap
import com.aeriotv.android.core.remote.RemoteSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompactModernLayoutTest {

    // The Shield's stored map on 2026-09-25 (Settings > Remote Control).
    private val shieldMap = """{"version":1,"preset":"custom","guideKeys":2,"player":{"okShort":"toggleControls","okLong":"none","upShort":"channelUp","downShort":"channelDown","upLong":"recentChannels","downLong":"openSearch","leftShort":"channelList","leftLong":"minimizeToGuide","rightShort":"lastChannel","rightLong":"showProgramInfo","playPause":"playPause","ffwd":"seekForward","rewind":"seekBackward","channelUp":"channelUp","channelDown":"channelDown"},"guide":{"ffwd":"pageDown","rewind":"pageUp","channelUp":"pageUp","channelDown":"pageDown","okShort":"play","okLong":"programMenu","leftShort":"navigate","rightShort":"navigate","rightLong":"closeMiniPlayer","playPause":"resumePlayer","leftLong":"none"}}"""

    @Test
    fun presetSetsOnlyTheGuidesLeftButtons() {
        val before = RemoteControlMap.fromJson(shieldMap)
        val after = RemoteControlMap.fromJson(CompactModernLayout.remoteMapWithPreset(shieldMap))
        assertEquals(GuideRemoteAction.FOCUS_GROUP_PILLS, after.guideAction(RemoteSlot.LEFT_SHORT))
        assertEquals(GuideRemoteAction.TIMELINE_BACK, after.guideAction(RemoteSlot.LEFT_LONG))
        for (slot in RemoteSlot.entries) {
            assertEquals("player $slot", before.playerAction(slot), after.playerAction(slot))
            if (slot != RemoteSlot.LEFT_SHORT && slot != RemoteSlot.LEFT_LONG) {
                assertEquals("guide $slot", before.guideAction(slot), after.guideAction(slot))
            }
        }
    }

    @Test
    fun presetOnDefaultMapKeepsEveryOtherDefault() {
        val after = RemoteControlMap.fromJson(CompactModernLayout.remoteMapWithPreset(""))
        for (slot in RemoteSlot.entries) {
            assertEquals("player $slot", RemoteControlMap.DEFAULT.playerAction(slot), after.playerAction(slot))
            if (slot != RemoteSlot.LEFT_SHORT && slot != RemoteSlot.LEFT_LONG) {
                assertEquals("guide $slot", RemoteControlMap.DEFAULT.guideAction(slot), after.guideAction(slot))
            }
        }
        assertEquals(GuideRemoteAction.FOCUS_GROUP_PILLS, after.guideAction(RemoteSlot.LEFT_SHORT))
    }

    @Test
    fun snapshotRoundTripsIncludingNeverSetValues() {
        val snap = CompactModernLayout.Snapshot(
            liveTvLayout = "basic", guideGroupSelector = null, guideSidebarLayout = "overlay",
            displayScaleLiveTv = null, remoteControlMap = shieldMap,
        )
        assertEquals(snap, CompactModernLayout.Snapshot.fromJson(snap.toJson()))
    }

    @Test
    fun missingOrBrokenSnapshotRestoresNothing() {
        assertNull(CompactModernLayout.Snapshot.fromJson(null))
        assertNull(CompactModernLayout.Snapshot.fromJson(""))
        assertNull(CompactModernLayout.Snapshot.fromJson("{not json"))
    }
}
