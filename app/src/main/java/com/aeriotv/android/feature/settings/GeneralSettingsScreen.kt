package com.aeriotv.android.feature.settings

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aeriotv.android.R
import androidx.annotation.StringRes
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        SettingsDetailTopBar(title = stringResource(R.string.settings_general_title), onBack = onBack)

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
                    header = stringResource(R.string.settings_general_startup_header),
                    footer = stringResource(R.string.settings_general_startup_footer) +
                        if (isTv) "" else " " + stringResource(R.string.settings_general_startup_footer_rotate),
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
                            label = stringResource(tab.labelRes),
                            selected = selected,
                            onClick = { viewModel.setDefaultTab(tab.name) },
                        )
                    }
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_general_skip_loading),
                        subtitle = stringResource(R.string.settings_general_skip_loading_subtitle),
                        checked = skipLoadingScreen,
                        onCheckedChange = viewModel::setSkipLoadingScreen,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_general_resume_last_channel),
                        subtitle = stringResource(R.string.settings_general_resume_last_channel_subtitle),
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
                            title = stringResource(R.string.settings_general_auto_rotate),
                            subtitle = stringResource(R.string.settings_general_auto_rotate_subtitle),
                            checked = autoRotate,
                            onCheckedChange = viewModel::setAutoRotate,
                        )
                    }
                }

                // MARK: Refresh
                SettingsSection(
                    header = stringResource(R.string.settings_general_refresh_header),
                    footer = stringResource(R.string.settings_general_refresh_footer) + " " +
                        stringResource(if (isTv) R.string.settings_general_refresh_footer_tv else R.string.settings_general_refresh_footer_phone),
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_general_refresh_background),
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
                                label = stringResource(opt.labelRes),
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
                    header = stringResource(R.string.settings_general_live_updates_header),
                    footer = stringResource(R.string.settings_general_live_updates_footer),
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_general_live_updates_toggle),
                        checked = liveAvailable && dispatcharrLiveUpdates,
                        onCheckedChange = { if (liveAvailable) viewModel.setDispatcharrLiveUpdates(it) },
                        // Focusable while still checking; greyed out only once known unavailable.
                        enabled = dispatcharrLiveEligibility == null || liveAvailable,
                        subtitle = when (dispatcharrLiveEligibility) {
                            null -> stringResource(R.string.settings_general_live_updates_checking)
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.AVAILABLE -> null
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.NEEDS_PASSWORD_LOGIN ->
                                stringResource(R.string.settings_general_live_updates_needs_password)
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.NOT_DISPATCHARR ->
                                stringResource(R.string.settings_general_live_updates_not_dispatcharr)
                            com.aeriotv.android.core.network.DispatcharrLiveEligibility.NO_PLAYLIST ->
                                stringResource(R.string.settings_general_live_updates_no_playlist)
                        },
                    )
                }

                // MARK: arrTV optimizations
                // One switch per optimization, all on by default: switched off,
                // that part does nothing and the app behaves as before it, so
                // any of them can be ruled out when something goes wrong.
                SettingsSection(
                    header = stringResource(R.string.settings_general_arrtv_header),
                    footer = stringResource(R.string.settings_general_arrtv_footer),
                ) {
                    com.aeriotv.android.core.playback.ArrTvOptimization.entries.forEach { o ->
                        SettingsToggleRow(
                            title = stringResource(o.titleRes),
                            checked = arrTvOptimizations[o] ?: true,
                            onCheckedChange = { viewModel.setArrTvOptimization(o, it) },
                            subtitle = stringResource(o.subtitleRes),
                        )
                    }
                }

                // The waits "Leave a stream that is slow to start" uses, in seconds.
                SettingsSection(
                    header = stringResource(R.string.settings_general_wait_header),
                    footer = stringResource(R.string.settings_general_wait_footer),
                ) {
                    com.aeriotv.android.core.playback.ArrTvWait.entries.forEach { w ->
                        val secs = arrTvWaits[w] ?: w.default
                        SecondsStepperRow(
                            title = stringResource(w.titleRes),
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
                    header = stringResource(R.string.settings_general_network_header),
                    footer = stringResource(R.string.settings_general_network_footer),
                ) {
                    TIMEOUT_OPTIONS.forEach { secs ->
                        SettingsSelectionRow(
                            label = pluralStringResource(R.plurals.settings_general_timeout_seconds, secs, secs),
                            selected = timeoutSecs.toInt() == secs,
                            onClick = { viewModel.setNetworkTimeoutSecs(secs.toDouble()) },
                        )
                    }
                }

                // Low-memory profile (core/system/DeviceMemory): Auto follows
                // the device's RAM; On / Off force it.
                val memContext = androidx.compose.ui.platform.LocalContext.current
                var memoryMode by remember { mutableStateOf(com.aeriotv.android.core.system.DeviceMemory.mode(memContext)) }
                val memoryDetected = remember { com.aeriotv.android.core.system.DeviceMemory.detected(memContext) }
                SettingsSection(
                    header = stringResource(R.string.settings_general_memory_header),
                    footer = stringResource(R.string.settings_general_memory_footer),
                ) {
                    listOf(
                        com.aeriotv.android.core.system.DeviceMemory.AUTO to
                            stringResource(if (memoryDetected) R.string.settings_general_memory_auto_on else R.string.settings_general_memory_auto_off),
                        com.aeriotv.android.core.system.DeviceMemory.ON to stringResource(R.string.settings_general_memory_on),
                        com.aeriotv.android.core.system.DeviceMemory.OFF to stringResource(R.string.settings_general_memory_off),
                    ).forEach { (mode, label) ->
                        SettingsSelectionRow(
                            label = label,
                            selected = memoryMode == mode,
                            onClick = {
                                com.aeriotv.android.core.system.DeviceMemory.setMode(memContext, mode)
                                memoryMode = mode
                            },
                        )
                    }
                }

                // Max Retries stays a stepper (no tvOS equivalent), on a resting card.
                SettingsSection(
                    header = stringResource(R.string.settings_general_max_retries_header),
                    footer = stringResource(R.string.settings_general_max_retries_footer),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsRowCard(focused = false)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_general_retries),
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
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_general_decrease))
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
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_general_increase))
                        }
                    }
                }
            }
        }
    }
}

/** tvOS Request Timeout options (s_10): 5/10/15/30/60 seconds. */
private val TIMEOUT_OPTIONS: List<Int> = listOf(5, 10, 15, 30, 60)

private data class BgRefreshIntervalOption(val mins: Int, @StringRes val labelRes: Int)

/** iOS bgRefreshIntervalMins picker options. 360 (6h) is the default;
 *  match the iOS picker so synced preferences round-trip cleanly. */
private val BG_REFRESH_INTERVAL_OPTIONS: List<BgRefreshIntervalOption> = listOf(
    BgRefreshIntervalOption(60, R.string.settings_general_refresh_every_hour),
    BgRefreshIntervalOption(180, R.string.settings_general_refresh_every_3_hours),
    BgRefreshIntervalOption(360, R.string.settings_general_refresh_every_6_hours),
    BgRefreshIntervalOption(720, R.string.settings_general_refresh_every_12_hours),
    BgRefreshIntervalOption(1440, R.string.settings_general_refresh_every_24_hours),
    BgRefreshIntervalOption(2880, R.string.settings_general_refresh_every_48_hours),
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
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_general_shorter))
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
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_general_longer))
        }
    }
}
