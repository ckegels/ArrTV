package com.aeriotv.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.feature.main.AppTab
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsSelectionRow
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import com.aeriotv.android.ui.settings.settingsRowCard
import com.aeriotv.android.ui.theme.textAccent

/**
 * Settings > General. How the app starts, how often it refreshes in the
 * background, and the network request budget.
 *
 * Settings phase 1 regroup: Startup came from App Behaviors (Default Tab,
 * Launch, Orientation), Refresh and Network from the retired Network page.
 * Keys, control types and copy are carried over unchanged.
 */
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTvDevice()
    val defaultTab by viewModel.defaultTab.collectAsStateWithLifecycle(initialValue = "")
    val skipLoadingScreen by viewModel.skipLoadingScreen.collectAsStateWithLifecycle(initialValue = false)
    val autoResumeLastChannel by viewModel.autoResumeLastChannel.collectAsStateWithLifecycle(initialValue = false)
    val backgroundRefreshEnabled by viewModel.backgroundRefreshEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val backgroundRefreshIntervalMins by viewModel.backgroundRefreshIntervalMins
        .collectAsStateWithLifecycle(initialValue = 360)
    val dispatcharrLiveUpdates by viewModel.dispatcharrLiveUpdates
        .collectAsStateWithLifecycle(initialValue = false)
    val arrTvOptimizations by viewModel.arrTvOptimizations
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val arrTvWaits by viewModel.arrTvWaits
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    // Null until the active playlist has been read. That read can queue behind
    // heavy guide work at launch for tens of seconds, and a row rendered as
    // disabled meanwhile is skipped by D-pad focus, so it reads as broken.
    val dispatcharrLiveEligibility: com.aeriotv.android.core.network.DispatcharrLiveEligibility? by viewModel
        .dispatcharrLiveEligibility.collectAsStateWithLifecycle(initialValue = null)
    val timeoutSecs by viewModel.networkTimeoutSecs.collectAsStateWithLifecycle(initialValue = 15.0)
    val maxRetries by viewModel.maxRetries.collectAsStateWithLifecycle(initialValue = 3)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = "General", onBack = onBack)

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
                // MARK: Startup
                //
                // Apple phase 1 parity: Default Tab, the two launch toggles and
                // Auto-rotate are ONE section with one combined footer.
                SettingsSection(
                    header = "Startup",
                    footer = "The tab shown when the app first launches. Skip loading screen may cause brief stutter while data hydrates. Resume last channel re-opens the player on launch if the saved channel still exists in your playlist." +
                        if (isTv) {
                            ""
                        } else {
                            " The player's fullscreen button can still rotate into landscape whatever Auto-rotate says."
                        },
                ) {
                    // Search is a TV-only nav tab and not a sensible launch tab;
                    // on phones it does not exist at all. On Demand and Favorites
                    // are no longer tabs on any form factor (media center 2026-09-10).
                    AppTab.entries.filter {
                        it != AppTab.Search && it != AppTab.OnDemand && it != AppTab.Favorites
                    }.forEach { tab ->
                        val selected = (defaultTab.isEmpty() && tab == AppTab.LiveTV) ||
                            defaultTab == tab.name
                        SettingsSelectionRow(
                            label = tab.label,
                            selected = selected,
                            onClick = { viewModel.setDefaultTab(tab.name) },
                        )
                    }
                    SettingsToggleRow(
                        title = "Skip loading screen",
                        subtitle = "Land on Live TV instantly; data hydrates in the background",
                        checked = skipLoadingScreen,
                        onCheckedChange = viewModel::setSkipLoadingScreen,
                    )
                    SettingsToggleRow(
                        title = "Resume last channel",
                        subtitle = "Auto-start the last-played channel on launch.",
                        checked = autoResumeLastChannel,
                        onCheckedChange = viewModel::setAutoResumeLastChannel,
                    )
                    // Auto-Rotate (Logan 2026-08-07, iOS twin): phones/tablets
                    // only - TVs have no rotation. Default ON; when off
                    // MainActivity locks the activity to its current orientation.
                    if (!isTv) {
                        val autoRotate by viewModel.autoRotate
                            .collectAsStateWithLifecycle(initialValue = true)
                        SettingsToggleRow(
                            title = "Auto-rotate",
                            subtitle = "Follow the device orientation. When off, " +
                                "AerioTV stays in its current orientation",
                            checked = autoRotate,
                            onCheckedChange = viewModel::setAutoRotate,
                        )
                    }
                }

                // MARK: Refresh
                SettingsSection(
                    header = "Refresh",
                    footer = "Refresh channels + the EPG in the background on Wi-Fi while the battery isn't low, so the guide is current the moment you open the app. " +
                        if (isTv) {
                            "Off here means data refreshes only when you launch AerioTV or refresh from the playlist menu."
                        } else {
                            "Off here means data refreshes only when you launch AerioTV or pull to refresh."
                        },
                ) {
                    SettingsToggleRow(
                        title = "Refresh in the background",
                        checked = backgroundRefreshEnabled,
                        onCheckedChange = viewModel::setBackgroundRefreshEnabled,
                    )
                    if (backgroundRefreshEnabled) {
                        // iOS bgRefreshIntervalMins (audit P1 #7): how often the
                        // periodic refresh fires. Hidden when the master toggle is
                        // off so the UI doesn't suggest setting frequency on a
                        // disabled worker.
                        BG_REFRESH_INTERVAL_OPTIONS.forEach { opt ->
                            SettingsSelectionRow(
                                label = opt.label,
                                selected = opt.mins == backgroundRefreshIntervalMins,
                                onClick = { viewModel.setBackgroundRefreshIntervalMins(opt.mins) },
                            )
                        }
                    }
                }

                // MARK: Live updates
                // Dispatcharr's change notifications. The row stays visible for
                // every source type and greys out with the reason, so users of
                // an API key login learn what would enable it.
                val liveAvailable = dispatcharrLiveEligibility ==
                    com.aeriotv.android.core.network.DispatcharrLiveEligibility.AVAILABLE
                SettingsSection(
                    header = "Live updates",
                    footer = "While AerioTV is open, Dispatcharr tells it when a playlist or EPG refresh finishes, " +
                        "and the channel list and guide update within seconds instead of on the next refresh.",
                ) {
                    SettingsToggleRow(
                        title = "Update automatically when Dispatcharr changes",
                        checked = liveAvailable && dispatcharrLiveUpdates,
                        onCheckedChange = { if (liveAvailable) viewModel.setDispatcharrLiveUpdates(it) },
                        // Focusable while still checking; greyed out only once known unavailable.
                        enabled = dispatcharrLiveEligibility == null || liveAvailable,
                        subtitle = when (dispatcharrLiveEligibility) {
                            null -> "Checking your playlist\u2026"
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.AVAILABLE -> null
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.NEEDS_PASSWORD_LOGIN ->
                                "Requires logging in to Dispatcharr with username and password (not an API key)."
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.NOT_DISPATCHARR ->
                                "Only available for Dispatcharr playlists."
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.NO_PLAYLIST ->
                                "Add a Dispatcharr playlist to use this."
                        },
                    )
                }

                // MARK: arrTV optimizations
                // One switch per optimization, all on by default: switched off,
                // that part does nothing and the app behaves as before it, so
                // any of them can be ruled out when something goes wrong.
                SettingsSection(
                    header = "arrTV optimizations",
                    footer = "Faster, smarter playback with a Dispatch More server. Turn one off if you suspect it; " +
                        "the others keep working.",
                ) {
                    com.aeriotv.android.core.playback.ArrTvOptimization.entries.forEach { o ->
                        SettingsToggleRow(
                            title = o.title,
                            checked = arrTvOptimizations[o] ?: true,
                            onCheckedChange = { viewModel.setArrTvOptimization(o, it) },
                            subtitle = o.subtitle,
                        )
                    }
                }

                // The waits "Leave a stream that is slow to start" uses, in seconds.
                SettingsSection(
                    header = "Wait for a picture",
                    footer = "How long a stream may take to show a picture before the channel's next stream is tried: " +
                        "the channel's usual start time scaled up, at least these, never past the longest. " +
                        "With no other stream to go to, the stream is never left.",
                ) {
                    com.aeriotv.android.core.playback.ArrTvWait.entries.forEach { w ->
                        val secs = arrTvWaits[w] ?: w.default
                        SecondsStepperRow(
                            title = w.title,
                            seconds = secs,
                            range = w.range,
                            onChange = { viewModel.setArrTvWait(w, it) },
                        )
                    }
                }

                // MARK: Network
                //
                // tvOS Network (s_10) presents Request Timeout as a selection list
                // (5/10/15/30/60 seconds), not a slider: cleaner with a remote.
                SettingsSection(
                    header = "Network",
                    footer = "Adjust timeouts if you have a slow or unstable connection.",
                ) {
                    TIMEOUT_OPTIONS.forEach { secs ->
                        SettingsSelectionRow(
                            label = if (secs == 1) "1 second" else "$secs seconds",
                            selected = timeoutSecs.toInt() == secs,
                            onClick = { viewModel.setNetworkTimeoutSecs(secs.toDouble()) },
                        )
                    }
                }

                // Max Retries stays a stepper (no tvOS equivalent), on a resting card.
                SettingsSection(
                    header = "Max Retries",
                    footer = "Per-request retry budget (0-10).",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsRowCard(focused = false)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Retries",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { if (maxRetries > 0) viewModel.setMaxRetries(maxRetries - 1) },
                            enabled = maxRetries > 0,
                            modifier = Modifier.dpadFocusRing(CircleShape),
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                        }
                        Text(
                            text = maxRetries.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.textAccent,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = { if (maxRetries < 10) viewModel.setMaxRetries(maxRetries + 1) },
                            enabled = maxRetries < 10,
                            modifier = Modifier.dpadFocusRing(CircleShape),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase")
                        }
                    }
                }
            }
        }
    }
}

/** tvOS Request Timeout options (s_10): 5/10/15/30/60 seconds. */
private val TIMEOUT_OPTIONS: List<Int> = listOf(5, 10, 15, 30, 60)

private data class BgRefreshIntervalOption(val mins: Int, val label: String)

/** iOS bgRefreshIntervalMins picker options. 360 (6h) is the default;
 *  match the iOS picker so synced preferences round-trip cleanly. */
private val BG_REFRESH_INTERVAL_OPTIONS: List<BgRefreshIntervalOption> = listOf(
    BgRefreshIntervalOption(60, "Every hour"),
    BgRefreshIntervalOption(180, "Every 3 hours"),
    BgRefreshIntervalOption(360, "Every 6 hours"),
    BgRefreshIntervalOption(720, "Every 12 hours"),
    BgRefreshIntervalOption(1440, "Every 24 hours"),
    BgRefreshIntervalOption(2880, "Every 48 hours"),
)

/** A wait in whole seconds, stepped with - and + (easy with a remote), like Max Retries. */
@Composable
private fun SecondsStepperRow(title: String, seconds: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsRowCard(focused = false)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { if (seconds > range.first) onChange(seconds - 1) },
            enabled = seconds > range.first,
            modifier = Modifier.dpadFocusRing(CircleShape),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Shorter")
        }
        Text(
            text = "$seconds s",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.textAccent,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = { if (seconds < range.last) onChange(seconds + 1) },
            enabled = seconds < range.last,
            modifier = Modifier.dpadFocusRing(CircleShape),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Longer")
        }
    }
}
