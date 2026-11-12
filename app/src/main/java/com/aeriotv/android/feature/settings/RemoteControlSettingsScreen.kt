package com.aeriotv.android.feature.settings

import com.aeriotv.android.ui.theme.textAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.aeriotv.android.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.remote.GuideRemoteAction
import com.aeriotv.android.core.remote.PlayerRemoteAction
import com.aeriotv.android.core.remote.RemoteControlMap
import com.aeriotv.android.core.remote.RemotePreset
import com.aeriotv.android.core.remote.RemoteSlot
import com.aeriotv.android.core.tv.TvActionMenuDialog
import com.aeriotv.android.core.tv.TvMenuAction
import com.aeriotv.android.core.tv.rememberTvMenuGuard
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsRowContainer
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset

/**
 * Settings > App Settings > Remote Control (TV only). Customizable
 * per-context button maps. Reset restores the standard scheme behind
 * a TvMenuGuard-protected confirm.
 */

/** Slots offered per context on Android TV (plan section 5). Guide
 *  up/down long are deliberately omitted until a guide long-press
 *  detector exists (omit rather than fake). */
private val PLAYER_SLOTS = listOf(
    RemoteSlot.OK_SHORT, RemoteSlot.OK_LONG,
    RemoteSlot.UP_SHORT, RemoteSlot.UP_LONG,
    RemoteSlot.DOWN_SHORT, RemoteSlot.DOWN_LONG,
    RemoteSlot.LEFT_SHORT, RemoteSlot.LEFT_LONG,
    RemoteSlot.RIGHT_SHORT, RemoteSlot.RIGHT_LONG,
)
private val PLAYER_EXTENDED_SLOTS = listOf(
    RemoteSlot.PLAY_PAUSE, RemoteSlot.FFWD, RemoteSlot.REWIND,
    RemoteSlot.CHANNEL_UP, RemoteSlot.CHANNEL_DOWN,
)
private val GUIDE_SLOTS = listOf(
    RemoteSlot.OK_SHORT, RemoteSlot.OK_LONG,
    RemoteSlot.LEFT_SHORT, RemoteSlot.LEFT_LONG,
    RemoteSlot.RIGHT_SHORT, RemoteSlot.RIGHT_LONG,
)
private val GUIDE_EXTENDED_SLOTS = listOf(
    RemoteSlot.PLAY_PAUSE, RemoteSlot.FFWD, RemoteSlot.REWIND,
    RemoteSlot.CHANNEL_UP, RemoteSlot.CHANNEL_DOWN,
)

/** Player actions offered in the picker: only actions the executor can
 *  actually run today (implementer note: omit rather than fake). */
private val PLAYER_ACTION_CHOICES = listOf(
    PlayerRemoteAction.CHANNEL_UP,
    PlayerRemoteAction.CHANNEL_DOWN,
    PlayerRemoteAction.LAST_CHANNEL,
    PlayerRemoteAction.RECENT_CHANNELS,
    PlayerRemoteAction.OPEN_SEARCH,
    PlayerRemoteAction.TOGGLE_CONTROLS,
    PlayerRemoteAction.SHOW_PROGRAM_INFO,
    PlayerRemoteAction.OPTIONS_MENU,
    PlayerRemoteAction.SEEK_FORWARD,
    PlayerRemoteAction.SEEK_BACKWARD,
    PlayerRemoteAction.MINIMIZE_TO_GUIDE,
    PlayerRemoteAction.CHANNEL_LIST,
    PlayerRemoteAction.NONE,
)

private val GUIDE_ARROW_SLOTS = setOf(
    RemoteSlot.OK_SHORT, RemoteSlot.OK_LONG,
    RemoteSlot.LEFT_SHORT, RemoteSlot.LEFT_LONG,
    RemoteSlot.RIGHT_SHORT, RemoteSlot.RIGHT_LONG,
)

private fun guideActionChoices(slot: RemoteSlot): List<GuideRemoteAction> = buildList {
    if (slot in GUIDE_ARROW_SLOTS) {
        add(GuideRemoteAction.FOCUS_GROUP_PILLS)
        add(GuideRemoteAction.TIMELINE_BACK)
        add(GuideRemoteAction.TIMELINE_FORWARD)
        // Select has no focus movement of its own to fall back on.
        if (slot != RemoteSlot.OK_SHORT && slot != RemoteSlot.OK_LONG) add(GuideRemoteAction.NAVIGATE)
        add(GuideRemoteAction.PROGRAM_INFO)
        add(GuideRemoteAction.PROGRAM_MENU)
        add(GuideRemoteAction.RECORD)
        add(GuideRemoteAction.PLAY)
        add(GuideRemoteAction.JUMP_TO_NOW)
        add(GuideRemoteAction.JUMP_TO_DAY)
        add(GuideRemoteAction.PAGE_UP)
        add(GuideRemoteAction.PAGE_DOWN)
        if (slot == RemoteSlot.RIGHT_LONG) add(GuideRemoteAction.CLOSE_MINI_PLAYER)
        add(GuideRemoteAction.NONE)
        return@buildList
    }
    add(GuideRemoteAction.TIMELINE_BACK)
    add(GuideRemoteAction.TIMELINE_FORWARD)
    add(GuideRemoteAction.PAGE_UP)
    add(GuideRemoteAction.PAGE_DOWN)
    add(GuideRemoteAction.JUMP_TO_NOW)
    add(GuideRemoteAction.JUMP_TO_DAY)
    add(GuideRemoteAction.JUMP_TO_TOP)
    add(GuideRemoteAction.FOCUS_GROUP_PILLS)
    add(GuideRemoteAction.RESUME_PLAYER)
    // The mini-close teardown runs at the activity layer, which today
    // only dispatches it from the hold-Right path.
    if (slot == RemoteSlot.RIGHT_LONG) add(GuideRemoteAction.CLOSE_MINI_PLAYER)
    if (slot == RemoteSlot.OK_LONG) add(GuideRemoteAction.PROGRAM_INFO)
    add(GuideRemoteAction.NONE)
}

@get:StringRes
private val RemoteSlot.labelRes: Int
    get() = when (this) {
        RemoteSlot.OK_SHORT -> R.string.settings2_remote_slot_select
        RemoteSlot.OK_LONG -> R.string.settings2_remote_slot_select_hold
        RemoteSlot.UP_SHORT -> R.string.settings2_remote_slot_up
        RemoteSlot.UP_LONG -> R.string.settings2_remote_slot_up_hold
        RemoteSlot.DOWN_SHORT -> R.string.settings2_remote_slot_down
        RemoteSlot.DOWN_LONG -> R.string.settings2_remote_slot_down_hold
        RemoteSlot.LEFT_SHORT -> R.string.settings2_remote_slot_left
        RemoteSlot.LEFT_LONG -> R.string.settings2_remote_slot_left_hold
        RemoteSlot.RIGHT_SHORT -> R.string.settings2_remote_slot_right
        RemoteSlot.RIGHT_LONG -> R.string.settings2_remote_slot_right_hold
        RemoteSlot.PLAY_PAUSE -> R.string.settings2_remote_slot_play_pause
        RemoteSlot.FFWD -> R.string.settings2_remote_slot_ffwd
        RemoteSlot.REWIND -> R.string.settings2_remote_slot_rewind
        RemoteSlot.CHANNEL_UP -> R.string.settings2_remote_slot_channel_up
        RemoteSlot.CHANNEL_DOWN -> R.string.settings2_remote_slot_channel_down
    }

@get:StringRes
private val PlayerRemoteAction.labelRes: Int
    get() = when (this) {
        PlayerRemoteAction.CHANNEL_UP -> R.string.settings2_remote_action_channel_up
        PlayerRemoteAction.CHANNEL_DOWN -> R.string.settings2_remote_action_channel_down
        PlayerRemoteAction.LAST_CHANNEL -> R.string.settings2_remote_action_last_channel
        PlayerRemoteAction.RECENT_CHANNELS -> R.string.settings2_remote_action_recent_channels
        // Fixed hold-Back behavior; named here for completeness but never
        // offered in the choice lists (Back semantics stay hardcoded).
        PlayerRemoteAction.STOP_PLAYBACK -> R.string.settings2_remote_action_stop_playback
        PlayerRemoteAction.TOGGLE_CONTROLS -> R.string.settings2_remote_action_toggle_controls
        PlayerRemoteAction.SHOW_PROGRAM_INFO -> R.string.settings2_remote_action_show_program_info
        PlayerRemoteAction.OPTIONS_MENU -> R.string.settings2_remote_action_options_menu
        PlayerRemoteAction.PLAY_PAUSE -> R.string.settings2_remote_action_play_pause
        PlayerRemoteAction.SEEK_FORWARD -> R.string.settings2_remote_action_seek_forward
        PlayerRemoteAction.SEEK_BACKWARD -> R.string.settings2_remote_action_seek_back
        PlayerRemoteAction.RESTART_PROGRAM -> R.string.settings2_remote_action_restart_program
        PlayerRemoteAction.JUMP_TO_LIVE -> R.string.settings2_remote_action_jump_to_live
        PlayerRemoteAction.MINIMIZE_TO_GUIDE -> R.string.settings2_remote_action_return_to_guide
        PlayerRemoteAction.CHANNEL_LIST -> R.string.settings2_remote_action_channel_list
        PlayerRemoteAction.SUBTITLES -> R.string.settings2_remote_action_subtitles
        PlayerRemoteAction.AUDIO_TRACKS -> R.string.settings2_remote_action_audio_tracks
        PlayerRemoteAction.ASPECT_RATIO -> R.string.settings2_remote_action_aspect_ratio
        PlayerRemoteAction.RECORD -> R.string.settings2_remote_action_record
        PlayerRemoteAction.SLEEP_TIMER -> R.string.settings2_remote_action_sleep_timer
        PlayerRemoteAction.OPEN_SEARCH -> R.string.common_search
        PlayerRemoteAction.NONE -> R.string.settings2_remote_action_do_nothing
    }

/** Guide action name; the group menu is named for the selector it opens. */
@StringRes
private fun GuideRemoteAction.labelRes(sidebarGroups: Boolean): Int = when (this) {
    GuideRemoteAction.FOCUS_GROUP_PILLS ->
        if (sidebarGroups) R.string.settings2_remote_action_open_sidebar else R.string.settings2_remote_action_group_pills
    else -> labelRes
}

@get:StringRes
private val GuideRemoteAction.labelRes: Int
    get() = when (this) {
        GuideRemoteAction.PAGE_UP -> R.string.settings2_remote_action_page_up
        GuideRemoteAction.PAGE_DOWN -> R.string.settings2_remote_action_page_down
        GuideRemoteAction.TIMELINE_BACK -> R.string.settings2_remote_action_timeline_back
        GuideRemoteAction.TIMELINE_FORWARD -> R.string.settings2_remote_action_timeline_forward
        GuideRemoteAction.JUMP_TO_NOW -> R.string.settings2_remote_action_jump_to_now
        GuideRemoteAction.JUMP_TO_DAY -> R.string.settings2_remote_action_jump_to_day
        GuideRemoteAction.JUMP_TO_TOP -> R.string.settings2_remote_action_jump_to_top
        GuideRemoteAction.FOCUS_GROUP_PILLS -> R.string.settings2_remote_action_group_pills
        GuideRemoteAction.RESUME_PLAYER -> R.string.settings2_remote_action_resume_player
        GuideRemoteAction.CLOSE_MINI_PLAYER -> R.string.settings2_remote_action_close_mini_player
        GuideRemoteAction.PROGRAM_INFO -> R.string.settings2_remote_action_program_info
        GuideRemoteAction.PROGRAM_MENU -> R.string.settings2_remote_action_program_menu
        GuideRemoteAction.RECORD -> R.string.settings2_remote_action_record
        GuideRemoteAction.PLAY -> R.string.common_play
        GuideRemoteAction.NAVIGATE -> R.string.settings2_remote_action_move_focus
        GuideRemoteAction.OPEN_SEARCH -> R.string.common_search
        GuideRemoteAction.NONE -> R.string.settings2_remote_action_do_nothing
    }

@Composable
fun RemoteControlSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val map by viewModel.remoteControlMap.collectAsStateWithLifecycle(
        initialValue = RemoteControlMap.DEFAULT,
    )
    var editingPlayerSlot by remember { mutableStateOf<RemoteSlot?>(null) }
    var editingGuideSlot by remember { mutableStateOf<RemoteSlot?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val groupSelector by viewModel.guideGroupSelector.collectAsStateWithLifecycle(
        initialValue = "pills",
    )
    val tuneInMini by viewModel.guideTuneInMini.collectAsStateWithLifecycle(
        initialValue = false,
    )
    var editingTuneTarget by remember { mutableStateOf(false) }
    val showRemoteHints by viewModel.showRemoteHints.collectAsStateWithLifecycle(
        initialValue = true,
    )
    val menuGuard = rememberTvMenuGuard()

    fun saveEdited(newMap: RemoteControlMap) {
        viewModel.setRemoteControlMap(newMap.copy(preset = RemotePreset.CUSTOM))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = stringResource(R.string.settings2_section_remote_control), onBack = onBack)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .adaptiveFormWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = LocalTabBarBottomInset.current),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsSection(
                    header = stringResource(R.string.settings2_remote_hints_header),
                    footer = stringResource(R.string.settings2_remote_hints_footer),
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings2_remote_hints_toggle),
                        subtitle = stringResource(R.string.settings2_remote_hints_toggle_subtitle),
                        checked = showRemoteHints,
                        onCheckedChange = { viewModel.setShowRemoteHints(it) },
                    )
                }

                SettingsSection(
                    header = stringResource(R.string.settings2_remote_watching_header),
                    footer = stringResource(R.string.settings2_remote_watching_footer),
                ) {
                    PLAYER_SLOTS.forEach { slot ->
                        SlotRow(
                            slotName = stringResource(slot.labelRes),
                            valueName = stringResource(map.playerAction(slot).labelRes),
                            onClick = { editingPlayerSlot = slot },
                        )
                    }
                }

                SettingsSection(
                    header = stringResource(R.string.settings2_remote_guide_header),
                    footer = stringResource(R.string.settings2_remote_guide_footer),
                ) {
                    GUIDE_SLOTS.forEach { slot ->
                        SlotRow(
                            slotName = stringResource(slot.labelRes),
                            valueName = stringResource(map.guideAction(slot, groupSelector == "sidebar").labelRes(groupSelector == "sidebar")),
                            onClick = { editingGuideSlot = slot },
                        )
                    }
                }

                SettingsSection(
                    header = stringResource(R.string.settings2_remote_additional_header),
                    footer = stringResource(R.string.settings2_remote_additional_footer),
                ) {
                    PLAYER_EXTENDED_SLOTS.forEach { slot ->
                        SlotRow(
                            slotName = stringResource(R.string.settings2_remote_slot_watching, stringResource(slot.labelRes)),
                            valueName = stringResource(map.playerAction(slot).labelRes),
                            onClick = { editingPlayerSlot = slot },
                        )
                    }
                    GUIDE_EXTENDED_SLOTS.forEach { slot ->
                        SlotRow(
                            slotName = stringResource(R.string.settings2_remote_slot_guide, stringResource(slot.labelRes)),
                            valueName = stringResource(map.guideAction(slot).labelRes(groupSelector == "sidebar")),
                            onClick = { editingGuideSlot = slot },
                        )
                    }
                }

                SettingsSection(
                    header = stringResource(R.string.settings2_remote_tuning_header),
                    footer = stringResource(R.string.settings2_remote_tuning_footer),
                ) {
                    SlotRow(
                        slotName = stringResource(R.string.settings2_remote_play_channels_in),
                        valueName = stringResource(if (tuneInMini) R.string.settings2_remote_mini_player else R.string.settings2_remote_full_screen),
                        onClick = { editingTuneTarget = true },
                    )
                }

                SettingsSection(
                    header = stringResource(R.string.settings2_remote_reset_header),
                    footer = stringResource(R.string.settings2_remote_reset_footer),
                ) {
                    SlotRow(
                        slotName = stringResource(R.string.settings2_remote_reset_to_defaults),
                        valueName = "",
                        onClick = { showResetConfirm = true },
                    )
                }
            }
        }
    }

    if (editingTuneTarget) {
        TvActionMenuDialog(
            title = stringResource(R.string.settings2_remote_play_channels_in),
            actions = listOf(
                TvMenuAction(
                    label = markCurrent(stringResource(R.string.settings2_remote_full_screen), !tuneInMini),
                ) {
                    viewModel.setGuideTuneInMini(false)
                    editingTuneTarget = false
                },
                TvMenuAction(
                    label = markCurrent(stringResource(R.string.settings2_remote_mini_player), tuneInMini),
                ) {
                    viewModel.setGuideTuneInMini(true)
                    editingTuneTarget = false
                },
            ),
            onDismiss = { editingTuneTarget = false },
            guard = menuGuard,
        )
    }

    editingPlayerSlot?.let { slot ->
        TvActionMenuDialog(
            title = stringResource(slot.labelRes),
            actions = PLAYER_ACTION_CHOICES.map { action ->
                val current = map.playerAction(slot) == action
                TvMenuAction(
                    label = markCurrent(stringResource(action.labelRes), current),
                ) {
                    saveEdited(map.copy(player = map.player + (slot to action)))
                    editingPlayerSlot = null
                }
            },
            onDismiss = { editingPlayerSlot = null },
            guard = menuGuard,
        )
    }

    editingGuideSlot?.let { slot ->
        TvActionMenuDialog(
            title = stringResource(slot.labelRes),
            actions = guideActionChoices(slot).map { action ->
                val current = map.guideAction(slot, groupSelector == "sidebar") == action
                val name = stringResource(action.labelRes(groupSelector == "sidebar"))
                TvMenuAction(
                    label = markCurrent(name, current),
                ) {
                    saveEdited(map.copy(guide = map.guide + (slot to action)))
                    editingGuideSlot = null
                }
            },
            onDismiss = { editingGuideSlot = null },
            guard = menuGuard,
        )
    }

    if (showResetConfirm) {
        TvActionMenuDialog(
            title = stringResource(R.string.settings2_remote_reset_confirm_title),
            actions = listOf(
                TvMenuAction(label = stringResource(R.string.settings2_remote_reset_to_defaults), destructive = true) {
                    viewModel.setRemoteControlMap(RemoteControlMap.DEFAULT)
                    showResetConfirm = false
                },
                TvMenuAction(label = stringResource(R.string.common_cancel)) { showResetConfirm = false },
            ),
            onDismiss = { showResetConfirm = false },
            guard = menuGuard,
        )
    }
}

/** A menu choice, marked when it is the one in use. */
@Composable
private fun markCurrent(label: String, isCurrent: Boolean): String =
    if (isCurrent) stringResource(R.string.settings2_remote_current_choice, label) else label

@Composable
private fun SlotRow(
    slotName: String,
    valueName: String,
    onClick: () -> Unit,
) {
    SettingsRowContainer(onClick = onClick) {
        Text(
            text = slotName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (valueName.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = valueName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.textAccent,
            )
        }
    }
}
