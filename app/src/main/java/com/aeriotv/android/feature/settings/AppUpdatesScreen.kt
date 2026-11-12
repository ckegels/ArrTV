package com.aeriotv.android.feature.settings

import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeriotv.android.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.BuildConfig
import com.aeriotv.android.core.update.UpdateState
import com.aeriotv.android.feature.update.UpdateViewModel
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsActionRow
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsInfoRow
import com.aeriotv.android.ui.settings.SettingsRowContainer
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset

/**
 * Settings > App Updates (github flavor only; the row that opens this screen
 * is hidden when the updater is disabled). Manual check + the full
 * download/install state, mirroring the launch prompt's actions.
 *
 * Every actionable element is a [SettingsActionRow] (the SettingsRowContainer
 * family), NOT a Material button: those rows are the screen's D-pad focus
 * unit, with the card highlight TV users can see. A plain OutlinedButton here
 * was unreachable/invisible to D-pad focus on the Streamer.
 */
@Composable
fun AppUpdatesScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle(initialValue = true)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = stringResource(R.string.settings2_updates_title), onBack = onBack)
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
                    header = stringResource(R.string.settings2_this_device),
                    footer = stringResource(R.string.settings2_updates_device_footer),
                ) {
                    SettingsInfoRow(
                        label = stringResource(R.string.settings2_updates_version),
                        value = BuildConfig.VERSION_NAME,
                    )
                    SettingsInfoRow(
                        label = stringResource(R.string.settings2_updates_channel),
                        value = stringResource(R.string.settings2_updates_channel_github),
                    )
                    // The result shows on the row itself, so every tap gives
                    // visible feedback even when the answer did not change.
                    val s = state
                    SettingsActionRow(
                        label = stringResource(R.string.settings2_updates_check),
                        leadingIcon = Icons.Filled.Refresh,
                        onClick = { viewModel.manualCheck() },
                        running = checking,
                        statusLine = when {
                            checking -> stringResource(R.string.settings2_updates_checking)
                            s is UpdateState.UpToDate ->
                                stringResource(R.string.settings2_updates_up_to_date, timeOf(s.checkedAtMs))
                            s is UpdateState.Available ->
                                stringResource(R.string.settings2_updates_version_available_below, s.info.versionName)
                            s is UpdateState.Error && s.info == null ->
                                stringResource(R.string.settings2_updates_check_failed)
                            else -> null
                        },
                        statusIsError = !checking && s is UpdateState.Error && s.info == null,
                    )
                }

                SettingsSection(
                    header = stringResource(R.string.settings2_updates_auto_header),
                    footer = stringResource(R.string.settings2_updates_auto_footer),
                ) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings2_updates_auto_toggle),
                        checked = autoCheck,
                        onCheckedChange = { viewModel.setAutoCheck(it) },
                    )
                }

                when (val s = state) {
                    is UpdateState.UpToDate -> Unit // shown on the Check for updates row
                    is UpdateState.Available -> SettingsSection(
                        header = stringResource(R.string.settings2_updates_available_header),
                        footer = s.info.notes.ifBlank { null },
                    ) {
                        SettingsActionRow(
                            label = stringResource(R.string.settings2_updates_download, s.info.versionName),
                            subtitle = stringResource(
                                R.string.settings2_updates_download_size,
                                s.info.apkSizeBytes / (1024 * 1024),
                            ),
                            leadingIcon = Icons.Filled.Download,
                            onClick = { viewModel.download() },
                        )
                    }
                    is UpdateState.Downloading -> Column {
                        StatusText(
                            stringResource(
                                R.string.settings2_updates_downloading,
                                s.info.versionName,
                                s.progressPercent,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { s.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UpdateState.Verifying -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp))
                        Spacer(Modifier.width(10.dp))
                        StatusText(stringResource(R.string.settings2_updates_verifying))
                    }
                    is UpdateState.ReadyToInstall -> SettingsSection(
                        header = stringResource(R.string.settings2_updates_ready_header),
                        footer = stringResource(R.string.settings2_updates_ready_footer),
                    ) {
                        SettingsActionRow(
                            label = stringResource(R.string.settings2_updates_install_version, s.info.versionName),
                            leadingIcon = Icons.Filled.SystemUpdate,
                            onClick = { viewModel.install() },
                        )
                    }
                    is UpdateState.AwaitingInstallPermission -> SettingsSection(
                        header = stringResource(R.string.settings2_updates_permission_header),
                        footer = stringResource(R.string.settings2_updates_permission_footer),
                    ) {
                        SettingsActionRow(
                            label = stringResource(R.string.settings2_updates_install),
                            leadingIcon = Icons.Filled.SystemUpdate,
                            onClick = { viewModel.install() },
                        )
                    }
                    is UpdateState.Installing -> StatusText(
                        stringResource(R.string.settings2_updates_installing),
                    )
                    is UpdateState.Error -> SettingsSection(
                        header = stringResource(R.string.settings2_updates_problem_header),
                        footer = s.message,
                    ) {
                        SettingsActionRow(
                            label = if (s.info != null) stringResource(R.string.settings2_updates_try_again)
                            else stringResource(R.string.settings2_updates_check_again),
                            leadingIcon = Icons.Filled.Refresh,
                            onClick = {
                                if (s.info != null) viewModel.download() else viewModel.manualCheck()
                            },
                        )
                        SettingsActionRow(
                            label = stringResource(R.string.settings2_updates_dismiss),
                            leadingIcon = Icons.Filled.Close,
                            onClick = { viewModel.dismissError() },
                        )
                    }
                    UpdateState.Idle -> Unit
                }
            }
        }
    }
}

/** Local time of day, e.g. "14:05", for the last check. */
@Composable
private fun timeOf(ms: Long): String =
    android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(ms))

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.subtext(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
