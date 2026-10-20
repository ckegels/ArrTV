package com.aeriotv.android.core.remote

import androidx.annotation.StringRes
import com.aeriotv.android.R
import com.aeriotv.android.core.locale.AppText

/**
 * Human copy for the on-screen gesture hint chips, derived from the
 * EFFECTIVE remote map so a remapped button never advertises a stale
 * gesture (the chips used to hardcode the standard scheme: "Press Select
 * for program info", "Hold left ... earlier programs").
 *
 * Actions without a natural short phrase return null and their sentence
 * is simply omitted - a hint that says nothing beats one that lies.
 */
/**
 * One "key  action" reminder for the on-screen hint strip. [key] is the
 * button as printed on the remote ("Hold Left", "Play/Pause"), [action] is
 * what that press does right now: sentence case, no punctuation.
 */
data class RemoteHint(val key: String, val action: String)

object RemoteControlHints {

    private fun t(@StringRes id: Int): String = AppText.get(id)
    private fun t(@StringRes id: Int, arg: String): String = AppText.get(id, arg)

    private fun playerPhrase(action: PlayerRemoteAction): String? = when (action) {
        PlayerRemoteAction.SHOW_PROGRAM_INFO -> t(R.string.livetv_remote_phrase_program_info)
        PlayerRemoteAction.TOGGLE_CONTROLS -> t(R.string.livetv_remote_phrase_player_controls)
        PlayerRemoteAction.OPTIONS_MENU -> t(R.string.livetv_remote_phrase_options)
        PlayerRemoteAction.CHANNEL_LIST -> t(R.string.livetv_remote_phrase_channel_list)
        PlayerRemoteAction.RECENT_CHANNELS -> t(R.string.livetv_remote_phrase_recent_channels)
        PlayerRemoteAction.OPEN_SEARCH -> t(R.string.livetv_remote_phrase_search)
        PlayerRemoteAction.LAST_CHANNEL -> t(R.string.livetv_remote_phrase_previous_channel)
        PlayerRemoteAction.MINIMIZE_TO_GUIDE -> t(R.string.livetv_remote_phrase_tv_guide)
        else -> null
    }

    /** The player's Select line, compressed: "Select: X  ·  Hold: Y".
     *  Null when neither OK slot maps to a phrasable action. */
    fun selectHint(map: RemoteControlMap): String? {
        val short = playerPhrase(map.playerAction(RemoteSlot.OK_SHORT))
        val long = playerPhrase(map.playerAction(RemoteSlot.OK_LONG))
        val parts = buildList {
            short?.let { add(t(R.string.livetv_remote_select_is, it)) }
            long?.let { add(t(R.string.livetv_remote_hold_select_is, it)) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }

    /** Whether Up/Down still channel-surf (gates the flip hint chip). */
    fun verticalFlipMapped(map: RemoteControlMap): Boolean =
        map.playerAction(RemoteSlot.UP_SHORT) == PlayerRemoteAction.CHANNEL_UP &&
            map.playerAction(RemoteSlot.DOWN_SHORT) == PlayerRemoteAction.CHANNEL_DOWN

    /** The player's Left/Right line (Logan 2026-08-06: users forgot what the
     *  horizontal presses do). CHANNEL_LIST gets the fixed second-press note -
     *  Left inside the channel list opens the group sidebar - because that
     *  stage is built into the overlay, not the map. Null when neither slot
     *  maps to a phrasable action. */
    fun playerHorizontalHint(map: RemoteControlMap): String? {
        val left = map.playerAction(RemoteSlot.LEFT_SHORT)
        val right = map.playerAction(RemoteSlot.RIGHT_SHORT)
        val parts = buildList {
            if (left == PlayerRemoteAction.CHANNEL_LIST) {
                add(t(R.string.livetv_remote_left_channel_list))
                add(t(R.string.livetv_remote_left_again_groups))
            } else {
                playerPhrase(left)?.let { add(t(R.string.livetv_remote_left_is, it)) }
            }
            playerPhrase(right)?.let { add(t(R.string.livetv_remote_right_is, it)) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }

    private fun guidePhrase(action: GuideRemoteAction): String? = when (action) {
        GuideRemoteAction.TIMELINE_BACK -> t(R.string.livetv_remote_guide_earlier)
        GuideRemoteAction.TIMELINE_FORWARD -> t(R.string.livetv_remote_guide_later)
        GuideRemoteAction.FOCUS_GROUP_PILLS -> t(R.string.livetv_remote_guide_group_pills)
        GuideRemoteAction.PAGE_UP -> t(R.string.livetv_remote_guide_page_up)
        GuideRemoteAction.PAGE_DOWN -> t(R.string.livetv_remote_guide_page_down)
        GuideRemoteAction.JUMP_TO_NOW -> t(R.string.livetv_remote_guide_now)
        GuideRemoteAction.JUMP_TO_DAY -> t(R.string.livetv_remote_guide_day)
        GuideRemoteAction.JUMP_TO_TOP -> t(R.string.livetv_remote_guide_top)
        GuideRemoteAction.RESUME_PLAYER -> t(R.string.livetv_remote_guide_player)
        else -> null
    }

    /** The guide's hold-Left chip; null when the slot is unmapped. */
    fun guideHoldLeftHint(map: RemoteControlMap, sidebarGroups: Boolean = false): String? =
        guidePhrase(map.guideAction(RemoteSlot.LEFT_LONG, sidebarGroups))
            ?.let { t(R.string.livetv_remote_hold_left_to, it) }

    /**
     * Terse phrases for the corner hint chip. [guidePhrase] is written for
     * full sentences ("browse earlier programs"); dropped verbatim into the
     * chip it pushed the pill under the centered tab bar, so the chip gets
     * its own two-or-three-word forms. Mirrors Apple's `guidePhraseShort`.
     */
    private fun guidePhraseShort(action: GuideRemoteAction): String? = when (action) {
        GuideRemoteAction.TIMELINE_BACK -> t(R.string.livetv_remote_short_earlier)
        GuideRemoteAction.TIMELINE_FORWARD -> t(R.string.livetv_remote_short_later)
        GuideRemoteAction.FOCUS_GROUP_PILLS -> t(R.string.livetv_remote_short_group_pills)
        GuideRemoteAction.PAGE_UP -> t(R.string.livetv_remote_short_page_up)
        GuideRemoteAction.PAGE_DOWN -> t(R.string.livetv_remote_short_page_down)
        GuideRemoteAction.JUMP_TO_NOW -> t(R.string.livetv_remote_short_now)
        GuideRemoteAction.JUMP_TO_DAY -> t(R.string.livetv_remote_short_jump_to)
        GuideRemoteAction.JUMP_TO_TOP -> t(R.string.livetv_remote_short_top)
        GuideRemoteAction.RESUME_PLAYER -> t(R.string.livetv_remote_short_player)
        else -> null
    }

    /** Terse form for the compact combined guide nav chip: "Hold Left = X". */
    fun guideHoldLeftShort(map: RemoteControlMap, sidebarGroups: Boolean = false): String? =
        guidePhraseShort(map.guideAction(RemoteSlot.LEFT_LONG, sidebarGroups))
            ?.let { t(R.string.livetv_remote_hold_left_is, it) }

    // ---------------------------------------------------------------
    // The remote hint STRIP (Logan 2026-09-11). One line of "key action"
    // pairs, generated from the CURRENT effective map plus the current app
    // state, never hard-coded: a user who remaps a button in Settings >
    // Remote Control must see their own button named here. A slot whose
    // action is unmapped, or not applicable in this mode, drops out.
    // ---------------------------------------------------------------

    /** The button name as printed on the remote, for the strip's key column. */
    fun slotLabel(slot: RemoteSlot): String = when (slot) {
        RemoteSlot.OK_SHORT -> "OK"
        RemoteSlot.OK_LONG -> t(R.string.livetv_remote_key_hold_ok)
        RemoteSlot.UP_SHORT -> t(R.string.livetv_remote_key_up)
        RemoteSlot.UP_LONG -> t(R.string.livetv_remote_key_hold_up)
        RemoteSlot.DOWN_SHORT -> t(R.string.livetv_remote_key_down)
        RemoteSlot.DOWN_LONG -> t(R.string.livetv_remote_key_hold_down)
        RemoteSlot.LEFT_SHORT -> t(R.string.livetv_remote_key_left)
        RemoteSlot.LEFT_LONG -> t(R.string.livetv_remote_key_hold_left)
        RemoteSlot.RIGHT_SHORT -> t(R.string.livetv_remote_key_right)
        RemoteSlot.RIGHT_LONG -> t(R.string.livetv_remote_key_hold_right)
        RemoteSlot.PLAY_PAUSE -> t(R.string.livetv_remote_key_play_pause)
        RemoteSlot.FFWD -> t(R.string.livetv_remote_key_ffwd)
        RemoteSlot.REWIND -> t(R.string.livetv_remote_key_rewind)
        RemoteSlot.CHANNEL_UP -> t(R.string.livetv_remote_key_channel_up)
        RemoteSlot.CHANNEL_DOWN -> t(R.string.livetv_remote_key_channel_down)
    }

    /** Strip wording for a guide action; null = nothing worth advertising. */
    private fun guideStripAction(action: GuideRemoteAction, sidebarGroups: Boolean): String? =
        when (action) {
            GuideRemoteAction.FOCUS_GROUP_PILLS -> if (sidebarGroups) t(R.string.livetv_remote_strip_groups) else t(R.string.livetv_remote_strip_group_pills)
            GuideRemoteAction.TIMELINE_BACK -> t(R.string.livetv_remote_strip_earlier)
            GuideRemoteAction.TIMELINE_FORWARD -> t(R.string.livetv_remote_strip_later)
            GuideRemoteAction.PAGE_UP -> t(R.string.livetv_remote_strip_page_up)
            GuideRemoteAction.PAGE_DOWN -> t(R.string.livetv_remote_strip_page_down)
            GuideRemoteAction.JUMP_TO_NOW -> t(R.string.livetv_remote_strip_now)
            GuideRemoteAction.JUMP_TO_DAY -> t(R.string.livetv_remote_strip_jump_to_day)
            GuideRemoteAction.JUMP_TO_TOP -> t(R.string.livetv_remote_strip_top_channel)
            GuideRemoteAction.RESUME_PLAYER -> t(R.string.livetv_remote_strip_resume)
            GuideRemoteAction.CLOSE_MINI_PLAYER -> t(R.string.livetv_remote_strip_close_mini)
            GuideRemoteAction.PROGRAM_INFO -> t(R.string.livetv_remote_strip_program_info)
            GuideRemoteAction.PROGRAM_MENU -> t(R.string.livetv_remote_strip_program_menu)
            GuideRemoteAction.RECORD -> t(R.string.livetv_remote_strip_record)
            GuideRemoteAction.PLAY -> t(R.string.common_play)
            GuideRemoteAction.OPEN_SEARCH -> t(R.string.common_search)
            GuideRemoteAction.NAVIGATE, GuideRemoteAction.NONE -> null
        }

    /** Strip wording for a player action; null = nothing worth advertising. */
    private fun playerStripAction(action: PlayerRemoteAction): String? = when (action) {
        PlayerRemoteAction.TOGGLE_CONTROLS -> t(R.string.livetv_remote_strip_player_controls)
        PlayerRemoteAction.SHOW_PROGRAM_INFO -> t(R.string.livetv_remote_strip_program_info)
        PlayerRemoteAction.OPTIONS_MENU -> t(R.string.livetv_remote_strip_options)
        PlayerRemoteAction.CHANNEL_LIST -> t(R.string.livetv_remote_strip_channel_list)
        PlayerRemoteAction.RECENT_CHANNELS -> t(R.string.livetv_remote_strip_recent_channels)
        PlayerRemoteAction.OPEN_SEARCH -> t(R.string.common_search)
        PlayerRemoteAction.LAST_CHANNEL -> t(R.string.livetv_remote_strip_previous_channel)
        PlayerRemoteAction.MINIMIZE_TO_GUIDE -> t(R.string.livetv_remote_strip_tv_guide)
        PlayerRemoteAction.CHANNEL_UP -> t(R.string.livetv_remote_strip_channel_up)
        PlayerRemoteAction.CHANNEL_DOWN -> t(R.string.livetv_remote_strip_channel_down)
        PlayerRemoteAction.PLAY_PAUSE -> t(R.string.livetv_remote_strip_play_pause)
        PlayerRemoteAction.SEEK_FORWARD -> t(R.string.livetv_remote_strip_seek_forward)
        PlayerRemoteAction.SEEK_BACKWARD -> t(R.string.livetv_remote_strip_seek_back)
        PlayerRemoteAction.RESTART_PROGRAM -> t(R.string.livetv_remote_strip_restart)
        PlayerRemoteAction.JUMP_TO_LIVE -> t(R.string.livetv_remote_strip_jump_to_live)
        PlayerRemoteAction.SUBTITLES -> t(R.string.livetv_remote_strip_subtitles)
        PlayerRemoteAction.AUDIO_TRACKS -> t(R.string.livetv_remote_strip_audio)
        PlayerRemoteAction.ASPECT_RATIO -> t(R.string.livetv_remote_strip_aspect_ratio)
        PlayerRemoteAction.RECORD -> t(R.string.livetv_remote_strip_record)
        PlayerRemoteAction.SLEEP_TIMER -> t(R.string.livetv_remote_strip_sleep_timer)
        PlayerRemoteAction.STOP_PLAYBACK -> t(R.string.livetv_remote_strip_stop)
        PlayerRemoteAction.NONE -> null
    }

    /** Cap: the strip is ONE line, so an unbounded list would just truncate. */
    private const val MAX_PAIRS = 6

    /**
     * Live TV tab strip. [sidebarGroups] is the guide's group-selector mode
     * ("sidebar" drawer vs the pill row), [miniActive] whether the corner mini
     * is playing.
     *
     * The Select / Left / Right pairs read the same resolution GuideGrid.kt
     * dispatches (RemoteControlMap.guideAction with the group selector): an
     * unset hold-Left opens the drawer in sidebar mode and browses earlier
     * programs with the pills; a slot left on its default navigation or
     * play is not advertised. Back is never a map slot: with a mini up the guide's Back belongs
     * to the mini (single = resume, double = top channel), without one a single
     * Back walks the guide back to the top channel.
     */
    fun guideStripHints(
        map: RemoteControlMap,
        sidebarGroups: Boolean,
        miniActive: Boolean,
    ): List<RemoteHint> = buildList {
        // Select plays by default, so only a remapped Select is worth a pair;
        // the arrows and their holds are advertised whenever they do something
        // other than move focus (truthful to the map, Settings > Remote Control).
        map.guideAction(RemoteSlot.OK_SHORT, sidebarGroups).takeIf { it != GuideRemoteAction.PLAY }
            ?.let { guideStripAction(it, sidebarGroups) }
            ?.let { add(RemoteHint(slotLabel(RemoteSlot.OK_SHORT), it)) }
        for (slot in listOf(RemoteSlot.LEFT_LONG, RemoteSlot.LEFT_SHORT, RemoteSlot.RIGHT_SHORT, RemoteSlot.RIGHT_LONG)) {
            val action = map.guideAction(slot, sidebarGroups)
            // The mini-player pairs below cover these, and only while a mini is up.
            if (action == GuideRemoteAction.CLOSE_MINI_PLAYER || action == GuideRemoteAction.RESUME_PLAYER) continue
            guideStripAction(action, sidebarGroups)?.let {
                add(RemoteHint(slotLabel(slot), it))
            }
        }
        add(RemoteHint(if (miniActive) t(R.string.livetv_remote_key_double_back) else t(R.string.livetv_remote_key_back), t(R.string.livetv_remote_strip_top_channel)))
        if (miniActive) {
            map.guideSlotFor(GuideRemoteAction.RESUME_PLAYER)?.let {
                add(RemoteHint(slotLabel(it), t(R.string.livetv_remote_strip_resume)))
            }
            map.guideSlotFor(GuideRemoteAction.CLOSE_MINI_PLAYER)?.let {
                add(RemoteHint(slotLabel(it), t(R.string.livetv_remote_strip_close_mini)))
            }
        }
    }.take(MAX_PAIRS)

    /**
     * Live player strip. Mirrors PlayerScreen's own key routing: in catch-up
     * (and while the Live Rewind buffer is rolling with a seek-mapped slot)
     * Left/Right scrub the timeline instead of running their mapped actions,
     * Up/Down only channel-surf while [channelFlip] is on and not replaying,
     * and Back is fixed (minimize to the corner mini, hold to stop).
     */
    fun livePlayerStripHints(
        map: RemoteControlMap,
        catchupMode: Boolean,
        rewindBuffering: Boolean,
        channelFlip: Boolean,
        controlsVisible: Boolean,
    ): List<RemoteHint> = buildList {
        val scrubbing = catchupMode ||
            (
                rewindBuffering &&
                    (
                        map.playerAction(RemoteSlot.LEFT_SHORT) == PlayerRemoteAction.SEEK_BACKWARD ||
                            map.playerAction(RemoteSlot.RIGHT_SHORT) == PlayerRemoteAction.SEEK_FORWARD
                        )
                )
        // With the controls on screen OK operates the FOCUSED control, not the
        // mapped chrome toggle, so the OK pairs would be lying; the strip only
        // ever renders in that state, but the gate is explicit so a future
        // chrome-hidden caller gets the right copy (Logan 2026-09-11).
        if (!controlsVisible) {
            playerStripAction(map.playerAction(RemoteSlot.OK_SHORT))?.let {
                add(RemoteHint(slotLabel(RemoteSlot.OK_SHORT), it))
            }
            playerStripAction(map.playerAction(RemoteSlot.OK_LONG))?.let {
                add(RemoteHint(slotLabel(RemoteSlot.OK_LONG), it))
            }
        }
        if (scrubbing) {
            add(RemoteHint(t(R.string.livetv_remote_key_left_right), t(R.string.livetv_remote_strip_scrub)))
        } else {
            if (channelFlip && verticalFlipMapped(map)) {
                add(RemoteHint(t(R.string.livetv_remote_key_up_down), t(R.string.livetv_remote_strip_channels)))
            } else {
                if (channelFlip) {
                    playerStripAction(map.playerAction(RemoteSlot.UP_SHORT))?.let {
                        add(RemoteHint(slotLabel(RemoteSlot.UP_SHORT), it))
                    }
                    playerStripAction(map.playerAction(RemoteSlot.DOWN_SHORT))?.let {
                        add(RemoteHint(slotLabel(RemoteSlot.DOWN_SHORT), it))
                    }
                }
            }
            playerStripAction(map.playerAction(RemoteSlot.LEFT_SHORT))?.let {
                add(RemoteHint(slotLabel(RemoteSlot.LEFT_SHORT), it))
            }
            playerStripAction(map.playerAction(RemoteSlot.RIGHT_SHORT))?.let {
                add(RemoteHint(slotLabel(RemoteSlot.RIGHT_SHORT), it))
            }
            playerStripAction(map.playerAction(RemoteSlot.LEFT_LONG))?.let {
                add(RemoteHint(slotLabel(RemoteSlot.LEFT_LONG), it))
            }
            playerStripAction(map.playerAction(RemoteSlot.RIGHT_LONG))?.let {
                add(RemoteHint(slotLabel(RemoteSlot.RIGHT_LONG), it))
            }
        }
        // Back is fixed and identical whether or not the controls are up:
        // PlayerScreen's BackHandler minimizes straight to the corner mini (the
        // old reveal-chrome-first rung was removed deliberately), and a
        // catch-up replay exits to where the user came from.
        add(RemoteHint(t(R.string.livetv_remote_key_back), if (catchupMode) t(R.string.livetv_remote_strip_exit) else t(R.string.livetv_remote_strip_mini_player)))
        // Logan 2026-09-14: Back twice (within 300 ms) ends playback outright
        // - no mini, stream stopped, back on the page the user came from. A
        // catch-up replay already exits on the first Back, so it has no
        // second rung to advertise.
        if (!catchupMode) add(RemoteHint(t(R.string.livetv_remote_key_back_twice), t(R.string.common_close)))
        // The two Back rungs are the fixed tail of this strip, so it carries
        // one pair more than the shared cap rather than dropping "Back twice".
    }.take(MAX_PAIRS + 1)

    /**
     * VOD / recording player strip. VODPlayerScreen runs its OWN transport
     * model (a focus ZONE walked with Left/Right, Up into the scrubber) and
     * deliberately does not consult the remote map, so these pairs follow the
     * zone rather than the mapping. [scrubberZone] = the scrubber holds the
     * zone.
     */
    fun vodPlayerStripHints(
        scrubberZone: Boolean,
        isPaused: Boolean,
    ): List<RemoteHint> = buildList {
        if (scrubberZone) {
            add(RemoteHint(t(R.string.livetv_remote_key_left_right), t(R.string.livetv_remote_strip_scrub)))
            add(RemoteHint("OK", t(R.string.livetv_remote_strip_go_to_time)))
            add(RemoteHint(t(R.string.livetv_remote_key_down), t(R.string.livetv_remote_strip_controls)))
        } else {
            add(RemoteHint("OK", if (isPaused) t(R.string.common_play) else t(R.string.common_pause)))
            add(RemoteHint(t(R.string.livetv_remote_key_left_right), t(R.string.livetv_remote_strip_controls)))
            add(RemoteHint(t(R.string.livetv_remote_key_up), t(R.string.livetv_remote_strip_scrubber)))
        }
        add(RemoteHint(t(R.string.livetv_remote_key_play_pause), if (isPaused) t(R.string.common_play) else t(R.string.common_pause)))
        add(RemoteHint(t(R.string.livetv_remote_key_back), t(R.string.livetv_remote_strip_exit)))
    }.take(MAX_PAIRS)
}
