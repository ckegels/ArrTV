package com.aeriotv.android.feature.settings

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
        SettingsDetailTopBar(title = "Player", onBack = onBack)

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
                        header = "Overlay Style",
                        footer = "Info bar: changing channel shows a full-width bar along the " +
                            "bottom with the channel logo and program details. OK adds a row " +
                            "with TV guide, History and the next channels; hold OK for the " +
                            "options menu (record, audio, subtitles and more).",
                    ) {
                        SettingsToggleRow(
                            title = "Info bar",
                            subtitle = "Replaces the info card and control buttons",
                            checked = infoBarStyle,
                            onCheckedChange = viewModel::setPlayerInfoBarStyle,
                        )
                    }
                }

                // MARK: Info Card
                SettingsSection(
                    header = "Info Card",
                    footer = "Choose what appears on the program info card in the " +
                        "player while the controls are showing.",
                ) {
                    SettingsToggleRow(
                        title = "Channel Logo",
                        checked = cardChannelLogo,
                        onCheckedChange = viewModel::setPlayerCardShowChannelLogo,
                    )
                    SettingsToggleRow(
                        title = "Channel Name",
                        checked = cardChannelName,
                        onCheckedChange = viewModel::setPlayerCardShowChannelName,
                    )
                    SettingsToggleRow(
                        title = "Program Name",
                        checked = cardProgramName,
                        onCheckedChange = viewModel::setPlayerCardShowProgramName,
                    )
                    SettingsToggleRow(
                        title = "Program Time",
                        checked = cardProgramTime,
                        onCheckedChange = viewModel::setPlayerCardShowProgramTime,
                    )
                    SettingsToggleRow(
                        title = "Program Subtitle",
                        checked = cardProgramSubtitle,
                        onCheckedChange = viewModel::setPlayerCardShowProgramSubtitle,
                    )
                    SettingsToggleRow(
                        title = "Program Description",
                        checked = cardProgramDescription,
                        onCheckedChange = viewModel::setPlayerCardShowProgramDescription,
                    )
                }

                // MARK: Live Rewind
                SettingsSection(
                    header = "Live Rewind",
                    footer = "Buffers the channel you are watching so you can pause and " +
                        "rewind live TV. Uses device storage while you watch; buffered " +
                        "video is removed automatically.",
                ) {
                    SettingsToggleRow(
                        title = "Pause & rewind live TV",
                        subtitle = "Buffer fullscreen live playback on this device",
                        checked = liveRewindEnabled,
                        onCheckedChange = viewModel::setLiveRewindEnabled,
                    )
                }
                if (liveRewindEnabled) {
                    SettingsSection(
                        header = "Keep Available",
                        footer = "How far back you can rewind the channel you are " +
                            "watching. Buffered video is released as soon as you " +
                            "leave the channel. " +
                            depthEstimateText(liveRewindDepth),
                    ) {
                        SteppedSliderRow(
                            label = "Rewind up to",
                            values = REWIND_DEPTH_MINUTES,
                            selected = liveRewindDepth,
                            format = ::formatDepthMinutes,
                            onSelect = viewModel::setLiveRewindDepthMinutes,
                        )
                    }
                    SettingsSection(
                        header = "Keep Recent Channels Live",
                        footer = if (keepRecent) {
                            "Channels you flip away from keep buffering so you can flip " +
                                "back and rewind across the time you were away. Each kept " +
                                "channel uses an extra connection to your provider; accounts " +
                                "limited to one connection should leave this off. Oldest " +
                                "channels stop first, and all stop when the app leaves the screen."
                        } else {
                            "Keep buffering the channels you most recently flipped away " +
                                "from. Uses one extra provider connection per kept channel."
                        },
                    ) {
                        SettingsToggleRow(
                            title = "Keep recent channels live",
                            subtitle = "Buffer flipped-away channels in the background",
                            checked = keepRecent,
                            onCheckedChange = viewModel::setLiveRewindKeepRecent,
                        )
                        if (keepRecent) {
                            SteppedSliderRow(
                                label = "Channels to keep",
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
                    header = "Playback",
                    footer = (
                        if (isTv) {
                            "How far the skip buttons and a single left or right press move " +
                                "in live rewind, catch-up, recordings, movies, and TV shows. " +
                                "Holding left or right still scrubs faster the longer you hold."
                        } else {
                            "How far the skip buttons move in live rewind, catch-up, " +
                                "recordings, movies, and TV shows, including the cast remote " +
                                "and the playback notification."
                        }
                        ) + " Buffer Size controls how much stream data is pre-loaded: larger " +
                        "buffers reduce stuttering on poor connections but add startup delay. " +
                        "If a live stream stops sending video, Auto-Recover reloads it; turn " +
                        "that off if live channels restart or stutter during commercial " +
                        "breaks, and a brief freeze may show instead. Recovery applies to the " +
                        "next channel you tune.",
                ) {
                    SteppedSliderRow(
                        label = "Skip back",
                        values = SkipIntervals.CHOICES,
                        selected = skipBackSeconds,
                        format = ::formatSkipSeconds,
                        onSelect = viewModel::setSkipBackSeconds,
                    )
                    SteppedSliderRow(
                        label = "Skip forward",
                        values = SkipIntervals.CHOICES,
                        selected = skipForwardSeconds,
                        format = ::formatSkipSeconds,
                        onSelect = viewModel::setSkipForwardSeconds,
                    )
                    BUFFER_OPTIONS.forEach { opt ->
                        SettingsSelectionRow(
                            label = opt.label,
                            subtitle = opt.detail,
                            selected = opt.id == bufferSize,
                            onClick = { viewModel.setStreamBufferSize(opt.id) },
                        )
                    }
                    SettingsToggleRow(
                        title = "Auto-Recover Frozen Streams",
                        subtitle = "Reload a live stream that stops sending video. Off keeps the stream as-is through commercial-break stutters.",
                        checked = autoRecoverFrozenStreams,
                        onCheckedChange = viewModel::setAutoRecoverFrozenStreams,
                    )
                }

                SettingsSection(
                    header = "Audio",
                    footer = "Passthrough sends surround sound audio as a bitstream for your TV or receiver to decode. Some TVs decode it late, which shows up as voices out of sync with lips on live TV. Off, AerioTV decodes audio itself and stays in sync. Takes effect on the next playback.",
                ) {
                    SettingsToggleRow(
                        title = "Surround sound passthrough",
                        subtitle = "Send AC-3 and E-AC-3 audio to your receiver untouched. Leave off if lip sync drifts.",
                        checked = audioPassthrough,
                        onCheckedChange = viewModel::setAudioPassthroughEnabled,
                    )
                }

                // MARK: Gestures
                SettingsSection(
                    header = "Gestures",
                    // tvOS / Android TV flip channels with D-pad up/down, not a
                    // swipe, so the "accidental swipes" caution is meaningless on
                    // a remote (user request: drop the note on TV). Phones keep it.
                    footer = if (isTv) {
                        null
                    } else {
                        "Turn off if accidental swipes during playback flip channels by mistake. " +
                            "Brightness and volume slides are recognized only inside a narrow band at the very edge of the screen, " +
                            "so they stay clear of the channel flip and of swiping down to minimize."
                    },
                ) {
                    SettingsToggleRow(
                        title = "Up / Down channel change",
                        subtitle = if (isTv) {
                            "While the player chrome is visible, press up for the next channel and down for the previous. Live single-stream playback only."
                        } else {
                            "While the player chrome is visible, swipe up for the next channel and down for the previous. Live single-stream playback only."
                        },
                        checked = appleTVChannelFlip,
                        onCheckedChange = viewModel::setAppleTVChannelFlip,
                    )
                    if (!isTv) {
                        SettingsToggleRow(
                            title = "Brightness edge slide",
                            subtitle = "Slide a finger up or down the brightness edge to dim or brighten the screen. Applies to this app only and is restored when you leave the player.",
                            checked = playerBrightnessGesture,
                            onCheckedChange = viewModel::setPlayerBrightnessGesture,
                        )
                        SettingsToggleRow(
                            title = "Volume edge slide",
                            subtitle = "Slide a finger up or down the other edge to change the media volume.",
                            checked = playerVolumeGesture,
                            onCheckedChange = viewModel::setPlayerVolumeGesture,
                        )
                        if (playerBrightnessGesture || playerVolumeGesture) {
                            listOf(
                                PLAYER_EDGE_LEFT to "Brightness: Left, Volume: Right",
                                PLAYER_EDGE_RIGHT to "Brightness: Right, Volume: Left",
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
                    header = "Multiview",
                    footer = "How the grid shows which tile is unmuted. Center Icon fades with the chrome, Gray Outline stays visible, Accent Outline appears on switch and fades after 5 seconds. Padding inserts a small gap between tiles so each stream stands on its own.",
                ) {
                    AUDIO_FOCUS_OPTIONS.forEach { opt ->
                        SettingsSelectionRow(
                            label = opt.label,
                            subtitle = opt.detail,
                            selected = multiviewStyle == opt.id,
                            onClick = { viewModel.setMultiviewAudioFocusStyle(opt.id) },
                        )
                    }
                    SettingsToggleRow(
                        title = "Padding Between Tiles",
                        subtitle = "Add a small gap between tiles for visual separation.",
                        checked = multiviewPadding,
                        onCheckedChange = viewModel::setMultiviewTilePadding,
                    )
                    SettingsSelectionRow(
                        label = "Square",
                        selected = !multiviewRounded,
                        onClick = { viewModel.setMultiviewTileCornersRounded(false) },
                    )
                    SettingsSelectionRow(
                        label = "Rounded",
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
                        header = "Display",
                        footer = "Startup Refresh Rate switches the display once at app launch so it is already on your main content rate before the first channel (changes apply on next launch). Match Content Resolution outputs at the stream's resolution so your TV does the upscaling; the screen blinks briefly on each switch.",
                    ) {
                        SettingsToggleRow(
                            title = "Match content resolution",
                            subtitle = "Output 1080p streams at 1080p and let the TV upscale. Off keeps the display at its native mode.",
                            checked = matchContentResolution,
                            onCheckedChange = viewModel::setMatchContentResolution,
                        )
                        listOf(
                            "off" to "Off (system default)",
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

private data class AudioFocusOption(val id: String, val label: String, val detail: String)

private val AUDIO_FOCUS_OPTIONS: List<AudioFocusOption> = listOf(
    AudioFocusOption("centerIcon", "Center Icon", "Speaker icon centered on the active tile. Default."),
    AudioFocusOption("grayPersistent", "Gray Outline", "Subtle gray border always around the active tile."),
    AudioFocusOption("themeFading", "Accent Outline (Fading)", "Accent-tinted border that auto-hides after 5 seconds."),
)

data class BufferOption(val id: String, val label: String, val detail: String, val cachingMs: Int)

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
    BufferOption("default", "Default", "4 seconds - recommended", 4_000),
    BufferOption("large", "Large", "8 seconds - unstable connections", 8_000),
    BufferOption("xlarge", "Extra Large", "16 seconds - very poor networks", 16_000),
)

/** Unknown ids (including the retired "small") resolve to Default. */
internal fun bufferMillisFor(id: String): Int =
    BUFFER_OPTIONS.firstOrNull { it.id == id }?.cachingMs ?: 4_000

/** Keep Available ladder (2026-07-11 rework, user directive round 2:
 *  the user-meaningful knob is HOW FAR BACK you can rewind, not how
 *  long files persist - retention is now a fixed internal 1 hour). */
private val REWIND_DEPTH_MINUTES = listOf(15, 30, 60, 90, 120, 180)

private fun formatDepthMinutes(mins: Int): String = when {
    mins < 60 -> "$mins minutes"
    mins == 60 -> "1 hour"
    mins % 60 == 0 -> "${mins / 60} hours"
    else -> "${mins / 60}h ${mins % 60}m"
}

/** Skip Intervals value readout: "10 seconds". */
private fun formatSkipSeconds(seconds: Int): String = "$seconds seconds"

/**
 * Storage estimate under the Keep Available slider: scales with the
 * depth choice (which bounds live disk usage now that retention is a
 * fixed short window) at typical stream bitrates - HD ~4 Mbps, FHD ~8,
 * UHD ~20 - so the user can pick what fits their streams and disk.
 */
private fun depthEstimateText(mins: Int): String {
    fun gb(mbps: Int): String {
        val v = mbps * 7.5 * mins / 1024.0
        return if (v < 10) String.format("~%.1f GB", v) else "~${v.roundToInt()} GB"
    }
    return "Uses up to ${gb(4)} in HD, ${gb(8)} in FHD, or ${gb(20)} in UHD while you watch."
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
