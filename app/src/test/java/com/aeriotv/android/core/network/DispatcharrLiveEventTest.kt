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
}
