package com.aeriotv.android.core.preferences

import com.aeriotv.android.core.remote.GuideRemoteAction
import com.aeriotv.android.core.remote.RemoteControlMap
import com.aeriotv.android.core.remote.RemotePreset
import com.aeriotv.android.core.remote.RemoteSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * "Compact modern layout" (TV): one switch for a TiviMate-style guide. Turning
 * it on remembers the user's own values of the settings it changes, then
 * applies the preset below; turning it off puts those values back exactly
 * (including "never set"). The left navigation rail it also enables is driven
 * by the switch itself, not by a stored preset value.
 *
 * Deliberately not part of the preset: theme, text sizes and the player's
 * remote buttons. Those are personal and stay as they are either way.
 */
object CompactModernLayout {

    /** Channel Preview banner above the guide; cells keep title and tags. */
    const val LIVE_TV_LAYOUT = "preview"

    /** Groups in a left sidebar instead of the top pill row... */
    const val GUIDE_GROUP_SELECTOR = "sidebar"

    /** ...that pushes the guide aside instead of covering it. */
    const val GUIDE_SIDEBAR_LAYOUT = "shift"

    /** Live TV display scale: denser rows. */
    const val DISPLAY_SCALE_LIVE_TV = 0.85

    /**
     * The settings the preset overwrites, as they were before it was turned
     * on. A null field means the key was never set (the app default applied),
     * and turning the preset off removes the key again.
     */
    @Serializable
    data class Snapshot(
        val liveTvLayout: String? = null,
        val guideGroupSelector: String? = null,
        val guideSidebarLayout: String? = null,
        val displayScaleLiveTv: Double? = null,
        val remoteControlMap: String? = null,
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            /** Null on a missing or unreadable snapshot: nothing to restore. */
            fun fromJson(raw: String?): Snapshot? =
                raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
        }
    }

    /**
     * [currentJson] (the stored remote map, blank = defaults) with the guide's
     * Left buttons set the TiviMate way: a press on the programme airing now
     * opens the groups (elsewhere it keeps stepping back in time, see
     * GuideGrid's live gate) and a hold goes back in time. Every other
     * button, guide and player, keeps its current mapping.
     */
    fun remoteMapWithPreset(currentJson: String?): String {
        val current = RemoteControlMap.fromJson(currentJson)
        // Materialise the effective guide map first: a DEFAULT-preset map
        // stores no slots, and switching it to CUSTOM must not change any
        // button the preset does not touch.
        val guide = RemoteSlot.entries.associateWith { current.guideAction(it) }.toMutableMap()
        val player = RemoteSlot.entries.associateWith { current.playerAction(it) }
        guide[RemoteSlot.LEFT_SHORT] = GuideRemoteAction.FOCUS_GROUP_PILLS
        guide[RemoteSlot.LEFT_LONG] = GuideRemoteAction.TIMELINE_BACK
        return RemoteControlMap(preset = RemotePreset.CUSTOM, player = player, guide = guide).toJson()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
