package com.aeriotv.android.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The Dispatcharr `/ws/` messages the app acts on. Everything else the socket
 * carries (progress ticks, plugin chatter such as a poster enricher sending
 * hundreds of messages a minute, connection telemetry) parses to null.
 *
 * Shapes captured from Dispatcharr 0.31.0 (apps/m3u/tasks.py send_m3u_update,
 * apps/epg/tasks.py, dispatcharr/consumers.py):
 *
 *     {"type": "update", "data": {"type": "m3u_refresh", "account": 14,
 *      "action": "parsing", "status": "success", "channels_created": 0,
 *      "channels_updated": 0, "channels_deleted": 0, ...}}
 *     {"type": "update", "data": {"type": "epg_refresh", "source": 42,
 *      "action": "parsing_programs", "status": "success", "updated_at": "..."}}
 *
 * Channel edits made by hand in Dispatcharr send NO message at all (verified
 * by creating, renaming and deleting a channel with a socket open). A plugin
 * can say it changed channels itself -- Show Groups (0.3.3) does after every
 * pass that moved a copy in or out of its group:
 *
 *     {"type": "update", "data": {"type": "channels_changed",
 *      "source": "show_groups", "channels": ["<uuid>", ...], "changes": 2}}
 *
 * Dispatch More (v219) sends the same message with `"guide": true` when a
 * channel is put on another guide (the Guides tab, or "Wrong guide?" in any
 * app) and again once that guide's programmes have been read.
 */
sealed interface DispatcharrLiveEvent {
    data object Connected : DispatcharrLiveEvent

    /** A playlist (M3U / XC account) refresh finished successfully. */
    data class PlaylistRefreshed(
        val accountId: Int?,
        /** False only when the server reported zero created/updated/deleted channels. */
        val channelsChanged: Boolean,
    ) : DispatcharrLiveEvent

    /** An EPG source finished importing its programmes. */
    data class EpgRefreshed(val sourceId: Int?) : DispatcharrLiveEvent

    /** Something on the server changed channels and said so (a plugin such as
     *  Show Groups switching its copies in and out of a profile). [guide]: it
     *  was their guide, so their programmes are to be fetched too. */
    data class ChannelsChanged(
        val source: String?,
        val channelUuids: List<String>,
        val guide: Boolean = false,
    ) : DispatcharrLiveEvent

    /** A recording was scheduled, started, stopped, changed or removed. */
    data object RecordingsChanged : DispatcharrLiveEvent

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private val RECORDING_TYPES = setOf(
            "recording_started", "recording_stopped", "recording_ended", "recording_updated",
            "recording_cancelled", "recording_extended", "recordings_refreshed",
        )

        /**
         * Parse one socket frame. Never throws; unknown, malformed and
         * uninteresting frames return null. A cheap substring gate skips the
         * JSON parse for the high-volume frames that can never match.
         */
        fun parse(text: String): DispatcharrLiveEvent? {
            if (!text.contains("m3u_refresh") && !text.contains("epg_refresh") &&
                !text.contains("channels_changed") &&
                !text.contains("recording") && !text.contains("connection_established")
            ) return null
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
            if (root.string("type") == "connection_established") return Connected
            val data = root["data"] as? JsonObject ?: return null
            return when (val type = data.string("type")) {
                "m3u_refresh" -> {
                    if (data.string("status") != "success" || data.string("action") != "parsing") return null
                    val counts = listOf("channels_created", "channels_updated", "channels_deleted")
                        .map { (data[it] as? JsonPrimitive)?.intOrNull }
                    // A server that does not report the counts: assume the lineup changed.
                    val changed = counts.any { it == null } || counts.any { it!! > 0 }
                    PlaylistRefreshed(data.int("account"), changed)
                }
                "epg_refresh" -> {
                    if (data.string("status") != "success" || data.string("action") != "parsing_programs") return null
                    EpgRefreshed(data.int("source"))
                }
                "channels_changed" -> ChannelsChanged(
                    data.string("source"),
                    (data["channels"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                    guide = (data["guide"] as? JsonPrimitive)?.contentOrNull == "true",
                )
                in RECORDING_TYPES -> RecordingsChanged
                else -> null
            }
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    }
}
