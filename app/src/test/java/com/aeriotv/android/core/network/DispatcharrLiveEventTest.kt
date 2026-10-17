package com.aeriotv.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures are frames captured from a Dispatcharr 0.31.0 server's /ws/ socket. */
class DispatcharrLiveEventTest {

    private val epgDone = """{"type": "update", "data": {"progress": 100, "type": "epg_refresh", "source": 42, "action": "parsing_programs", "status": "success", "message": "Parsed 2,275 programs for 21 channels (skipped 166,688 programs for 1491 unmapped channels)", "updated_at": "2026-09-25T15:13:11.792656+00:00"}}"""

    private val m3uDoneNoChanges = """{"type": "update", "data": {"progress": 100, "type": "m3u_refresh", "account": 14, "action": "parsing", "status": "success", "elapsed_time": 2.799034833908081, "time_remaining": 0, "streams_processed": 1813, "streams_created": 0, "streams_updated": 0, "streams_stale": 0, "streams_deleted": 0, "channels_created": 0, "channels_updated": 0, "channels_deleted": 0, "channels_failed": 0, "failed_stream_details": [], "message": "Processing completed in 2.8 seconds. Streams: 0 created, 0 updated, 0 marked stale, 0 removed. Total processed: 1813."}}"""

    @Test
    fun connectionEstablished() {
        val frame = """{"type": "connection_established", "data": {"success": true, "message": "WebSocket connection established successfully"}}"""
        assertEquals(DispatcharrLiveEvent.Connected, DispatcharrLiveEvent.parse(frame))
    }

    @Test
    fun anotherDeviceTookOver() {
        // Dispatch More v253 (app_one_device.tell), sent the moment it closes this TV's stream
        val frame = """{"type": "update", "data": {"type": "device_replaced", "user_id": 1, "device": "3f2a9c1e-0b7d-4e21-9a55-0f1c2d3e4f50", "by": "Pixel 9"}}"""
        assertEquals(
            DispatcharrLiveEvent.DeviceReplaced("3f2a9c1e-0b7d-4e21-9a55-0f1c2d3e4f50", "Pixel 9"),
            DispatcharrLiveEvent.parse(frame),
        )
        assertNull(DispatcharrLiveEvent.parse("""{"type": "update", "data": {"type": "device_replaced"}}"""))
    }

    @Test
    fun theDeviceWatchedTogetherWithChangedChannel() {
        // Dispatch More v260 (app_links.mirror)
        val frame = """{"type": "update", "data": {"type": "linked_channel", "user_id": 1, "channel": "6014de0c", "by": "shield-1", "devices": ["pixel-9"]}}"""
        assertEquals(
            DispatcharrLiveEvent.LinkedChannel("6014de0c", "shield-1", listOf("pixel-9")),
            DispatcharrLiveEvent.parse(frame),
        )
        // Who took over, so the replaced device can offer to join it
        val replaced = """{"type": "update", "data": {"type": "device_replaced", "device": "pixel-9", "by": "SHIELD", "by_device": "shield-1"}}"""
        assertEquals(
            DispatcharrLiveEvent.DeviceReplaced("pixel-9", "SHIELD", "shield-1"),
            DispatcharrLiveEvent.parse(replaced),
        )
        // Another device joined this one: linked too
        assertEquals(
            DispatcharrLiveEvent.LinkJoined("pixel-9", listOf("shield-1")),
            DispatcharrLiveEvent.parse("""{"type": "update", "data": {"type": "link_joined", "user_id": 1, "by": "pixel-9", "devices": ["shield-1"]}}"""),
        )
        // The other device left: this one watches on alone
        assertEquals(
            DispatcharrLiveEvent.LinkEnded(listOf("shield-1")),
            DispatcharrLiveEvent.parse("""{"type": "update", "data": {"type": "link_ended", "user_id": 1, "devices": ["shield-1"]}}"""),
        )
    }

    @Test
    fun epgImportFinished() {
        assertEquals(DispatcharrLiveEvent.EpgRefreshed(42), DispatcharrLiveEvent.parse(epgDone))
    }

    @Test
    fun epgProgressTicksAreIgnored() {
        val ticks = listOf(
            """{"type": "update", "data": {"progress": 100, "type": "epg_refresh", "source": 42, "action": "downloading", "status": "success"}}""",
            """{"type": "update", "data": {"progress": 100, "type": "epg_refresh", "source": 42, "action": "parsing_channels", "status": "success", "channels_count": 1512}}""",
            """{"type": "update", "data": {"progress": 32, "type": "epg_refresh", "source": 42, "action": "parsing_channels", "processed": 150, "total": 1512}}""",
            """{"type": "update", "data": {"progress": 10, "type": "epg_refresh", "source": 42, "action": "parsing_programs", "message": "Parsing programs..."}}""",
        )
        ticks.forEach { assertNull(it, DispatcharrLiveEvent.parse(it)) }
    }

    @Test
    fun playlistRefreshWithoutChannelChanges() {
        assertEquals(
            DispatcharrLiveEvent.PlaylistRefreshed(accountId = 14, channelsChanged = false),
            DispatcharrLiveEvent.parse(m3uDoneNoChanges),
        )
    }

    @Test
    fun playlistRefreshThatCreatedChannels() {
        val frame = m3uDoneNoChanges.replace("\"channels_created\": 0", "\"channels_created\": 3")
        assertEquals(
            DispatcharrLiveEvent.PlaylistRefreshed(accountId = 14, channelsChanged = true),
            DispatcharrLiveEvent.parse(frame),
        )
    }

    @Test
    fun playlistRefreshWithoutCountsAssumesChanges() {
        val frame = """{"type": "update", "data": {"type": "m3u_refresh", "account": 3, "action": "parsing", "status": "success"}}"""
        assertEquals(
            DispatcharrLiveEvent.PlaylistRefreshed(accountId = 3, channelsChanged = true),
            DispatcharrLiveEvent.parse(frame),
        )
    }

    @Test
    fun playlistProgressAndErrorsAreIgnored() {
        val frames = listOf(
            """{"type": "update", "data": {"progress": 0, "type": "m3u_refresh", "account": 14, "action": "processing_groups", "status": "fetching", "message": "Refresh in progress..."}}""",
            """{"type": "update", "data": {"progress": 50, "type": "m3u_refresh", "account": 14, "action": "parsing", "status": "parsing", "message": "Refresh in progress...", "elapsed_time": 1.877000093460083, "time_remaining": 1.877000093460083, "streams_processed": 313}}""",
            """{"type": "update", "data": {"progress": 100, "type": "m3u_refresh", "account": 14, "action": "parsing", "status": "error", "error": "boom"}}""",
        )
        frames.forEach { assertNull(it, DispatcharrLiveEvent.parse(it)) }
    }

    @Test
    fun recordingEvents() {
        listOf("recording_started", "recording_ended", "recordings_refreshed", "recording_cancelled").forEach { type ->
            val frame = """{"type": "update", "data": {"success": true, "type": "$type", "channel": "x"}}"""
            assertEquals(type, DispatcharrLiveEvent.RecordingsChanged, DispatcharrLiveEvent.parse(frame))
        }
    }

    @Test
    fun pluginChatterAndTelemetryAreIgnored() {
        val frames = listOf(
            """{"type": "update", "data": {"type": "plugin", "plugin": "poster_enricher", "message": "Poster enrichment started — 249844 programmes", "total": 249844, "done": 0}}""",
            // A channel named after a recording must not trip the substring gate into a match.
            """{"type": "update", "data": {"success": true, "type": "channel_stats", "stats": "{\"channels\": [{\"name\": \"recording_started news\"}]}"}}""",
        )
        frames.forEach { assertNull(it, DispatcharrLiveEvent.parse(it)) }
    }

    @Test
    fun aPluginSayingItChangedChannelsIsHeard() {
        val frame = """{"type": "update", "data": {"type": "channels_changed", "source": "show_groups", "profile": "Show Groups", "channels": ["a1b2", "c3d4"], "changes": 2}}"""
        assertEquals(
            DispatcharrLiveEvent.ChannelsChanged("show_groups", listOf("a1b2", "c3d4")),
            DispatcharrLiveEvent.parse(frame),
        )
        // Without a list it is still a change
        assertEquals(
            DispatcharrLiveEvent.ChannelsChanged(null, emptyList()),
            DispatcharrLiveEvent.parse("""{"type": "update", "data": {"type": "channels_changed"}}"""),
        )
    }

    @Test
    fun aGuideChangedOnTheServerSaysSo() {
        val frame = """{"type": "update", "data": {"type": "channels_changed", "source": "guides", "channels": ["a1b2"], "changes": 1, "guide": true}}"""
        assertEquals(
            DispatcharrLiveEvent.ChannelsChanged("guides", listOf("a1b2"), guide = true),
            DispatcharrLiveEvent.parse(frame),
        )
    }

    @Test
    fun malformedFramesNeverThrow() {
        listOf("", "not json", "{", "[]", "null", """{"type": "update"}""", """{"type": "update", "data": "m3u_refresh"}""",
            """{"type": "update", "data": {"type": "m3u_refresh", "status": "success", "action": "parsing", "channels_created": "many"}}""",
        ).forEach { DispatcharrLiveEvent.parse(it) }
    }

    @Test
    fun lookBackMovedThisChannel() {
        val frame = """{"type": "update", "data": {"type": "lookback_moved", "channel": "6f4c3991-f5e6-474e-8e00-a970a654c1d1", "stream": "24KITCHEN (TiviBridge)", "text": "Moved to 24KITCHEN (TiviBridge) for another viewer"}}"""
        assertEquals(
            DispatcharrLiveEvent.LookBackMoved("6f4c3991-f5e6-474e-8e00-a970a654c1d1", "Moved to 24KITCHEN (TiviBridge) for another viewer"),
            DispatcharrLiveEvent.parse(frame),
        )
    }
}
