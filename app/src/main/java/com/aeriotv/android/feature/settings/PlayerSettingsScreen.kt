package com.aeriotv.android.feature.settings

import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R
import com.aeriotv.android.core.locale.AppText
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.preferences.PLAYER_EDGE_LEFT
import com.aeriotv.android.core.preferences.PLAYER_EDGE_RIGHT
import com.aeriotv.android.core.ui.SkipIntervals
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import androidx.compose.material.icons.outlined.Translate
import com.aeriotv.android.ui.settings.SettingsActionRow
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsSelectionRow
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import com.aeriotv.android.ui.theme.textAccent
import com.aeriotv.android.ui.tv.dpadFocusEscape
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settings > Player. Everything about fullscreen playback: the info card, live
 * rewind, playback buffering and recovery, gestures, multiview tiles, and the
 * TV display-mode controls.
 *
 * Settings phase 1 regroup: rows came from App Behaviors (info card, rewind,
 * skip intervals, gestures, stream recovery, audio, display), Network (buffer
 * size) and the retired Multiview page. Keys, control types and copy are
 * carried over unchanged.
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTvDevice()

    val infoBarStyle by viewModel.playerInfoBarStyle.collectAsStateWithLifecycle(initialValue = false)
    val sideChannelList by viewModel.playerSideChannelList.collectAsStateWithLifecycle(initialValue = false)
    val cardChannelLogo by viewModel.playerCardShowChannelLogo
        .collectAsStateWithLifecycle(initialValue = true)
    val cardChannelName by viewModel.playerCardShowChannelName
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramName by viewModel.playerCardShowProgramName
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramTime by viewModel.playerCardShowProgramTime
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramSubtitle by viewModel.playerCardShowProgramSubtitle
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramDescription by viewModel.playerCardShowProgramDescription
        .collectAsStateWithLifecycle(initialValue = true)

    val liveRewindEnabled by viewModel.liveRewindEnabled.collectAsStateWithLifecycle(initialValue = false)
    val liveRewindDepth by viewModel.liveRewindDepthMinutes.collectAsStateWithLifecycle(initialValue = 30)
    val keepRecent by viewModel.liveRewindKeepRecent.collectAsStateWithLifecycle(initialValue = false)
    val keepCount by viewModel.liveRewindKeepCount.collectAsStateWithLifecycle(initialValue = 2)

    val skipBackSeconds by viewModel.skipBackSeconds
        .collectAsStateWithLifecycle(initialValue = SkipIntervals.DEFAULT_BACK_SECONDS)
    val skipForwardSeconds by viewModel.skipForwardSeconds
        .collectAsStateWithLifecycle(initialValue = SkipIntervals.DEFAULT_FORWARD_SECONDS)
    val bufferSize by viewModel.streamBufferSize.collectAsStateWithLifecycle(initialValue = "default")
    val autoRecoverFrozenStreams by viewModel.autoRecoverFrozenStreams
        .collectAsStateWithLifecycle(initialValue = true)
    val teletextSubtitles by viewModel.teletextSubtitles.collectAsStateWithLifecycle(initialValue = true)
    val generatedCaptions by viewModel.generatedCaptions.collectAsStateWithLifecycle(initialValue = true)
    val captionLanguage by viewModel.captionLanguage.collectAsStateWithLifecycle(initialValue = "")
    val audioPassthrough by viewModel.audioPassthroughEnabled.collectAsStateWithLifecycle(initialValue = false)

    val appleTVChannelFlip by viewModel.appleTVChannelFlip.collectAsStateWithLifecycle(initialValue = true)
    val playerBrightnessGesture by viewModel.playerBrightnessGesture.collectAsStateWithLifecycle(initialValue = false)
    val playerVolumeGesture by viewModel.playerVolumeGesture.collectAsStateWithLifecycle(initialValue = false)
    val playerBrightnessEdge by viewModel.playerBrightnessEdge
        .collectAsStateWithLifecycle(initialValue = PLAYER_EDGE_LEFT)

    val multiviewStyle by viewModel.multiviewAudioFocusStyle.collectAsStateWithLifecycle(initialValue = "centerIcon")
    val multiviewPadding by viewModel.multiviewTilePadding.collectAsStateWithLifecycle(initialValue = false)
    val multiviewRounded by viewModel.multiviewTileCornersRounded.collectAsStateWithLifecycle(initialValue = false)

    val startupRefreshRate by viewModel.startupRefreshRate.collectAsStateWithLifecycle(initialValue = "off")
    val matchContentResolution by viewModel.matchContentResolution.collectAsStateWithLifecycle(initialValue = false)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = stringResource(R.string.settings_player_title), onBack = onBack)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .adaptiveFormWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = LocalTabBarBottomInset.current,
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // MARK: Overlay style (TV)
                if (isTv) {
                    SettingsSection(
                        header = stringResource(R.string.settings_player_overlay_header),
                        footer = stringResource(R.string.settings_player_overlay_footer),
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_player_info_bar),
                            subtitle = stringResource(R.string.settings_player_info_bar_subtitle),
                            checked = infoBarStyle,
                            onCheckedChange = viewModel::setPlayerInfoBarStyle,
                        )
                    }
                    SettingsSection(
                        header = stringResource(R.string.settings_player_side_list),
                        footer = stringResource(R.string.settings_player_side_list_footer),
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_player_side_list),
                            subtitle = stringResource(R.string.settings_player_side_list_subtitle),
                            checked = sideChannelList,
                            onCheckedChange = viewModel::setPlayerSideChannelList,
                        )
                    }
                }

                // MARK: Info Card
                SettingsSection(
                    header = stringResource(R.string.settings_player_info_card_header),
                    footer = stringResource(R.string.settings_player_info_card_footer),
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_card_channel_logo),
                        checked = cardChannelLogo,
                        onCheckedChange = viewModel::setPlayerCardShowChannelLogo,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_card_channel_name),
                        checked = cardChannelName,
                        onCheckedChange = viewModel::setPlayerCardShowChannelName,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_card_program_name),
                        checked = cardProgramName,
                        onCheckedChange = viewModel::setPlayerCardShowProgramName,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_card_program_time),
                        checked = cardProgramTime,
                        onCheckedChange = viewModel::setPlayerCardShowProgramTime,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_card_program_subtitle),
                        checked = cardProgramSubtitle,
                        onCheckedChange = viewModel::setPlayerCardShowProgramSubtitle,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_card_program_description),
                        checked = cardProgramDescription,
                        onCheckedChange = viewModel::setPlayerCardShowProgramDescription,
                    )
                }

                // MARK: Live Rewind
                SettingsSection(
                    header = stringResource(R.string.settings_player_rewind_header),
                    footer = stringResource(R.string.settings_player_rewind_footer),
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_rewind_toggle),
                        subtitle = stringResource(R.string.settings_player_rewind_toggle_subtitle),
                        checked = liveRewindEnabled,
                        onCheckedChange = viewModel::setLiveRewindEnabled,
                    )
                }
                if (liveRewindEnabled) {
                    SettingsSection(
                        header = stringResource(R.string.settings_player_keep_available_header),
                        footer = depthEstimateText(liveRewindDepth),
                    ) {
                        SteppedSliderRow(
                            label = stringResource(R.string.settings_player_rewind_up_to),
                            values = REWIND_DEPTH_MINUTES,
                            selected = liveRewindDepth,
                            format = ::formatDepthMinutes,
                            onSelect = viewModel::setLiveRewindDepthMinutes,
                        )
                    }
                    SettingsSection(
                        header = stringResource(R.string.settings_player_keep_recent_header),
                        footer = if (keepRecent) {
                            stringResource(R.string.settings_player_keep_recent_footer_on)
                        } else {
                            stringResource(R.string.settings_player_keep_recent_footer_off)
                        },
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_player_keep_recent_toggle),
                            subtitle = stringResource(R.string.settings_player_keep_recent_subtitle),
                            checked = keepRecent,
                            onCheckedChange = viewModel::setLiveRewindKeepRecent,
                        )
                        if (keepRecent) {
                            SteppedSliderRow(
                                label = stringResource(R.string.settings_player_channels_to_keep),
                                values = listOf(1, 2, 3, 4, 5),
                                selected = keepCount,
                                format = { it.toString() },
                                onSelect = viewModel::setLiveRewindKeepCount,
                            )
                        }
                    }
                }

                // MARK: Playback
                //
                // Apple phase 1 parity: skip intervals, buffer size and stream
                // recovery share one section.
                SettingsSection(
                    header = stringResource(R.string.settings_player_playback_header),
                    footer = (
                        if (isTv) {
                            stringResource(R.string.settings_player_playback_footer_tv)
                        } else {
                            stringResource(R.string.settings_player_playback_footer_phone)
                        }
                        ) + " " + stringResource(R.string.settings_player_playback_footer_buffer),
                ) {
                    SteppedSliderRow(
                        label = stringResource(R.string.settings_player_skip_back),
                        values = SkipIntervals.CHOICES,
                        selected = skipBackSeconds,
                        format = ::formatSkipSeconds,
                        onSelect = viewModel::setSkipBackSeconds,
                    )
                    SteppedSliderRow(
                        label = stringResource(R.string.settings_player_skip_forward),
                        values = SkipIntervals.CHOICES,
                        selected = skipForwardSeconds,
                        format = ::formatSkipSeconds,
                        onSelect = viewModel::setSkipForwardSeconds,
                    )
                    BUFFER_OPTIONS.forEach { opt ->
                        SettingsSelectionRow(
                            label = stringResource(opt.labelRes),
                            subtitle = stringResource(opt.detailRes),
                            selected = opt.id == bufferSize,
                            onClick = { viewModel.setStreamBufferSize(opt.id) },
                        )
                    }
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_auto_recover),
                        subtitle = stringResource(R.string.settings_player_auto_recover_subtitle),
                        checked = autoRecoverFrozenStreams,
                        onCheckedChange = viewModel::setAutoRecoverFrozenStreams,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_teletext),
                        subtitle = stringResource(R.string.settings_player_teletext_subtitle),
                        checked = teletextSubtitles,
                        onCheckedChange = viewModel::setTeletextSubtitles,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_generated_captions),
                        subtitle = stringResource(R.string.settings_player_generated_captions_subtitle),
                        checked = generatedCaptions,
                        onCheckedChange = viewModel::setGeneratedCaptions,
                    )
                    run {
                        // Pressing steps through the choices. Always shown: hidden until
                        // Generated Captions was on, nobody found it (2026-10-02)
                        val choices = com.aeriotv.android.core.playback.CaptionLanguage.CHOICES
                        val at = choices.indexOfFirst { it.first == captionLanguage }.coerceAtLeast(0)
                        SettingsActionRow(
                            label = stringResource(R.string.settings_player_caption_language, com.aeriotv.android.core.playback.CaptionLanguage.label(captionLanguage)),
                            leadingIcon = androidx.compose.material.icons.Icons.Outlined.Translate,
                            onClick = { viewModel.setCaptionLanguage(choices[(at + 1) % choices.size].first) },
                            subtitle = stringResource(if (generatedCaptions) R.string.settings_player_caption_language_subtitle else R.string.settings_player_caption_language_subtitle_off),
                        )
                    }
                }

                SettingsSection(
                    header = stringResource(R.string.settings_player_audio_header),
                    footer = stringResource(R.string.settings_player_audio_footer),
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_passthrough),
                        subtitle = stringResource(R.string.settings_player_passthrough_subtitle),
                        checked = audioPassthrough,
                        onCheckedChange = viewModel::setAudioPassthroughEnabled,
                    )
                }

                // MARK: Gestures
                SettingsSection(
                    header = stringResource(R.string.settings_player_gestures_header),
                    // tvOS / Android TV flip channels with D-pad up/down, not a
                    // swipe, so the "accidental swipes" caution is meaningless on
                    // a remote (user request: drop the note on TV). Phones keep it.
                    footer = if (isTv) {
                        null
                    } else {
                        stringResource(R.string.settings_player_gestures_footer)
                    },
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_channel_flip),
                        subtitle = if (isTv) {
                            stringResource(R.string.settings_player_channel_flip_tv)
                        } else {
                            stringResource(R.string.settings_player_channel_flip_phone)
                        },
                        checked = appleTVChannelFlip,
                        onCheckedChange = viewModel::setAppleTVChannelFlip,
                    )
                    if (!isTv) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_player_brightness_gesture),
                            subtitle = stringResource(R.string.settings_player_brightness_gesture_subtitle),
                            checked = playerBrightnessGesture,
                            onCheckedChange = viewModel::setPlayerBrightnessGesture,
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_player_volume_gesture),
                            subtitle = stringResource(R.string.settings_player_volume_gesture_subtitle),
                            checked = playerVolumeGesture,
                            onCheckedChange = viewModel::setPlayerVolumeGesture,
                        )
                        if (playerBrightnessGesture || playerVolumeGesture) {
                            listOf(
                                PLAYER_EDGE_LEFT to stringResource(R.string.settings_player_edge_brightness_left),
                                PLAYER_EDGE_RIGHT to stringResource(R.string.settings_player_edge_brightness_right),
                            ).forEach { (wire, label) ->
                                SettingsSelectionRow(
                                    label = label,
                                    selected = playerBrightnessEdge == wire,
                                    onClick = { viewModel.setPlayerBrightnessEdge(wire) },
                                )
                            }
                        }
                    }
                }

                // MARK: Multiview
                //
                // Apple phase 1 parity: indicator, spacing and tile corners
                // share one section. tvOS presents corners as a Square /
                // Rounded selection (s_09); same underlying Boolean.
                SettingsSection(
                    header = stringResource(R.string.settings_player_multiview_header),
                    footer = stringResource(R.string.settings_player_multiview_footer),
                ) {
                    AUDIO_FOCUS_OPTIONS.forEach { opt ->
                        SettingsSelectionRow(
                            label = stringResource(opt.labelRes),
                            subtitle = stringResource(opt.detailRes),
                            selected = multiviewStyle == opt.id,
                            onClick = { viewModel.setMultiviewAudioFocusStyle(opt.id) },
                        )
                    }
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_player_tile_padding),
                        subtitle = stringResource(R.string.settings_player_tile_padding_subtitle),
                        checked = multiviewPadding,
                        onCheckedChange = viewModel::setMultiviewTilePadding,
                    )
                    SettingsSelectionRow(
                        label = stringResource(R.string.settings_player_tile_square),
                        selected = !multiviewRounded,
                        onClick = { viewModel.setMultiviewTileCornersRounded(false) },
                    )
                    SettingsSelectionRow(
                        label = stringResource(R.string.settings_player_tile_rounded),
                        selected = multiviewRounded,
                        onClick = { viewModel.setMultiviewTileCornersRounded(true) },
                    )
                }

                // MARK: Display (TV only)
                //
                // GH #38 + #40: each switch is a real HDMI mode change (brief
                // black screen), deliberate and user-opted.
                if (isTv) {
                    SettingsSection(
                        header = stringResource(R.string.settings_player_display_header),
                        footer = stringResource(R.string.settings_player_display_footer),
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_player_match_resolution),
                            subtitle = stringResource(R.string.settings_player_match_resolution_subtitle),
                            checked = matchContentResolution,
                            onCheckedChange = viewModel::setMatchContentResolution,
                        )
                        listOf(
                            "off" to stringResource(R.string.settings_player_refresh_rate_off),
                            "50" to "50 Hz",
                            "59.94" to "59.94 Hz",
                            "60" to "60 Hz",
                        ).forEach { (wire, label) ->
                            SettingsSelectionRow(
                                label = label,
                                selected = startupRefreshRate == wire,
                                onClick = { viewModel.setStartupRefreshRate(wire) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AudioFocusOption(val id: String, @StringRes val labelRes: Int, @StringRes val detailRes: Int)

private val AUDIO_FOCUS_OPTIONS: List<AudioFocusOption> = listOf(
    AudioFocusOption("centerIcon", R.string.settings_player_focus_center_icon, R.string.settings_player_focus_center_icon_detail),
    AudioFocusOption("grayPersistent", R.string.settings_player_focus_gray, R.string.settings_player_focus_gray_detail),
    AudioFocusOption("themeFading", R.string.settings_player_focus_accent, R.string.settings_player_focus_accent_detail),
)

data class BufferOption(val id: String, @StringRes val labelRes: Int, @StringRes val detailRes: Int, val cachingMs: Int)

/**
 * Discord (di5cord20, Formuler Z11, 2026-08-09): "Default -> Small made
 * zapping worse." It cannot have. The live player applies
 * `minBufferMs = maxOf(4_000, bufferFloorMs)` (AerioExoPlayerHolder), and the
 * old ladder was 300 / 1000 / 3000 / 8000 ms, so **Small, Default and Large
 * all collapsed onto the same 4s/8s LoadControl**: three of the four options
 * did nothing at all, while their subtitles advertised latencies (300 ms,
 * 1 second, 3 seconds) the player never used. Extra Large was worse than
 * inert - 8000 produced min == max == 8s, a LoadControl with no headroom
 * between its two bounds.
 *
 * The 4s floor is not an accident: it is the measured fix for the recurring
 * mid-stream micro-stutter on the Streamer (see the LoadControl comment in
 * AerioExoPlayerHolder, which walks through why 500ms and 2.5s both failed).
 * Nothing may sit below it, which leaves no room for a real "Small" - so it
 * is gone, and anyone on it is mapped to Default, which is exactly what they
 * were already getting.
 *
 * Default keeps its current behaviour to the millisecond, so no existing
 * install changes. Large and Extra Large start doing what their labels always
 * claimed.
 */
internal val BUFFER_OPTIONS: List<BufferOption> = listOf(
    BufferOption("default", R.string.common_default, R.string.settings_player_buffer_default_detail, 4_000),
    BufferOption("large", R.string.settings_player_buffer_large, R.string.settings_player_buffer_large_detail, 8_000),
    BufferOption("xlarge", R.string.settings_player_buffer_xlarge, R.string.settings_player_buffer_xlarge_detail, 16_000),
)

/** Unknown ids (including the retired "small") resolve to Default. */
internal fun bufferMillisFor(id: String): Int =
    BUFFER_OPTIONS.firstOrNull { it.id == id }?.cachingMs ?: 4_000

/** Keep Available ladder (2026-07-11 rework, user directive round 2:
 *  the user-meaningful knob is HOW FAR BACK you can rewind, not how
 *  long files persist - retention is now a fixed internal 1 hour). */
private val REWIND_DEPTH_MINUTES = listOf(15, 30, 60, 90, 120, 180)

private fun formatDepthMinutes(mins: Int): String = when {
    mins < 60 -> AppText.plural(R.plurals.settings_player_minutes, mins, mins)
    mins % 60 == 0 -> AppText.plural(R.plurals.settings_player_hours, mins / 60, mins / 60)
    else -> AppText.get(R.string.settings_player_hours_minutes, mins / 60, mins % 60)
}

/** Skip Intervals value readout: "10 seconds". */
private fun formatSkipSeconds(seconds: Int): String = AppText.plural(R.plurals.settings_player_seconds, seconds, seconds)

/**
 * Storage estimate under the Keep Available slider: scales with the
 * depth choice (which bounds live disk usage now that retention is a
 * fixed short window) at typical stream bitrates - HD ~4 Mbps, FHD ~8,
 * UHD ~20 - so the user can pick what fits their streams and disk.
 */
@Composable
private fun depthEstimateText(mins: Int): String {
    fun gb(mbps: Int): String {
        val v = mbps * 7.5 * mins / 1024.0
        return AppText.get(R.string.settings_player_gb_approx, if (v < 10) String.format("%.1f", v) else v.roundToInt().toString())
    }
    return stringResource(R.string.settings_player_keep_available_footer, gb(4), gb(8), gb(20))
}

/**
 * Discrete-stop slider row: label left, current value right, a stepped
 * Material slider beneath. TV-safe via [dpadFocusEscape] (the #90
 * lesson: UP/DOWN must move focus off the slider, LEFT/RIGHT adjust).
 */
@Composable
internal fun SteppedSliderRow(
    label: String,
    values: List<Int>,
    selected: Int,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    // Snap legacy/custom persisted values (e.g. 48h from the removed
    // custom dialog) to the nearest ladder stop for display; the pref
    // itself is only rewritten when the user moves the slider.
    val idx = values.indices.minByOrNull { abs(values[it] - selected) } ?: 0
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = format(values[idx]),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.textAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = idx.toFloat(),
            onValueChange = { raw ->
                val newIdx = raw.roundToInt().coerceIn(0, values.lastIndex)
                if (values[newIdx] != selected) onSelect(values[newIdx])
            },
            valueRange = 0f..values.lastIndex.toFloat(),
            steps = (values.size - 2).coerceAtLeast(0),
            modifier = Modifier.dpadFocusEscape(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
