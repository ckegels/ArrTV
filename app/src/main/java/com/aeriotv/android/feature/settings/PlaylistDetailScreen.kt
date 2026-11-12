package com.aeriotv.android.feature.settings

import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R
import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.theme.textAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Wifi
import com.aeriotv.android.ui.scale.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.data.db.entity.sourceTypeDisplayLabel
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsDialogTextButton
import com.aeriotv.android.ui.settings.SettingsHeaderTextButton
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import java.text.DateFormat
import java.util.Date
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset

/**
 * Playlist Detail. Mirrors iOS Settings > tap-a-playlist row:
 * CONNECTION DETAILS / ACTIONS (Test Connection) / EPG CACHE (Refresh).
 *
 * v1 surfaces the active playlist only; multi-playlist support lands later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    // Rev 2 canon amendment 1: the rail and sidebar select ANY playlist and
    // show its detail, so this page can no longer assume it is looking at the
    // active one. Null keeps the old behavior for callers that mean "active".
    playlistId: String? = null,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    val playlist = allPlaylists.firstOrNull { it.id == playlistId } ?: state.playlist
    // Whether the playlist ON SCREEN is the active one. The refresh/test
    // actions below are deliberately gated on this: every one of them resolves
    // `repository.activePlaylist()` and loads its result into the single
    // channel/EPG store, so running them while viewing a DIFFERENT playlist
    // would act on the wrong source and leave the store disagreeing with the
    // page. Set Active is the call to action instead; refresh once it is live.
    // LIVE from the DAO, not the UiState snapshot (Logan 2026-09-16): "Set
    // Active" has to become "Active Playlist" the moment the switch commits.
    val activeIdLive by viewModel.activeIdLive
        .collectAsStateWithLifecycle(initialValue = state.playlist?.id)
    val isActivePlaylist = playlist != null && playlist.id == activeIdLive
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRefreshAll by remember { mutableStateOf(false) }

    // LAN/WAN route is a point-in-time probe (no NetworkCallback flow in the
    // app); re-check on entry and ON_RESUME, mirroring NetworkSettingsScreen's
    // HomeWifiSection. Action statuses reset when the screen goes away.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(playlist?.id) { viewModel.refreshActiveRoute() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshActiveRoute()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearDetailActionStatuses()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = playlist?.name ?: stringResource(R.string.settings_playlist_detail_title_fallback),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                // No back arrow on Android TV -- the remote BACK pops it (user
                // request). Phones/tablets keep it.
                if (!rememberIsTvDevice()) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            actions = {
                // TV gets an Edit Playlist row in the ACTIONS section instead;
                // a corner action sits off the natural D-pad path.
                if (!rememberIsTvDevice()) {
                    SettingsHeaderTextButton(
                        label = stringResource(R.string.common_edit),
                        // Active-only, same rule as the refresh actions below:
                        // saveEdits writes through repository.activePlaylist(),
                        // so editing a NON-active playlist would silently
                        // overwrite the active one's URL and credentials.
                        enabled = playlist != null && isActivePlaylist,
                        onClick = onEdit,
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        if (playlist == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_playlist_detail_no_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        val isTv = rememberIsTvDevice()
        LazyColumn(
            modifier = Modifier.adaptiveFormWidth(),
            // 104dp bottom clears the MainScaffold NavigationBar on phones;
            // the TV nav lives at the top, so a slim overscan inset suffices.
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = if (isTv) 32.dp else LocalTabBarBottomInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section(
                    header = stringResource(R.string.settings_playlist_detail_connection_header),
                    // Only worth explaining when there are two URLs to
                    // choose between.
                    footer = if (!playlist.lanUrlString.isNullOrBlank()) {
                        stringResource(R.string.settings_playlist_detail_connection_footer)
                    } else {
                        null
                    },
                ) {
                    // On TV the card is itself a (read-only) focus stop: with
                    // only the Action rows focusable, D-pad UP from "Test
                    // Connection" had nowhere to go, so the list stayed
                    // scrolled with this card clipped under the title bar.
                    var infoFocused by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .then(
                                if (isTv) {
                                    Modifier
                                        .onFocusChanged { infoFocused = it.isFocused }
                                        .background(
                                            if (infoFocused) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .focusable()
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        DetailRow(stringResource(R.string.settings_playlist_detail_type), playlist.sourceTypeDisplayLabel())
                        // Checkmark marks whichever URL is currently in effect
                        // per PlaylistRepository.effectiveBaseUrl's decision.
                        val activeRoute = state.activeRoute
                        DetailRow(
                            label = stringResource(R.string.settings_playlist_detail_remote_url),
                            value = playlist.urlString,
                            icon = Icons.Filled.CheckCircle.takeIf { activeRoute?.isLan == false },
                            iconTint = MaterialTheme.colorScheme.primary,
                        )
                        playlist.lanUrlString?.takeIf { it.isNotBlank() }?.let { lan ->
                            DetailRow(
                                label = stringResource(R.string.settings_playlist_detail_local_url),
                                value = lan,
                                icon = Icons.Filled.CheckCircle.takeIf { activeRoute?.isLan == true },
                                iconTint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        playlist.username?.takeIf { it.isNotBlank() }?.let { user ->
                            DetailRow(stringResource(R.string.settings_playlist_detail_username), user)
                        }
                        // Reflect a real signal -- whether the source has ever
                        // loaded channels -- instead of asserting "Verified"
                        // unconditionally (which read as connected even for a
                        // source that never reached the server). The "Last
                        // Connected" row below dates the last successful load.
                        val hasConnected = playlist.channelCount > 0
                        DetailRow(
                            label = stringResource(R.string.settings_playlist_detail_status),
                            value = stringResource(if (hasConnected) R.string.settings_playlist_detail_connected else R.string.settings_playlist_detail_not_connected),
                            valueColor = if (hasConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            icon = Icons.Filled.CheckCircle.takeIf { hasConnected },
                        )
                        playlist.lastRefreshedAt?.let { ts ->
                            DetailRow(
                                stringResource(R.string.settings_playlist_detail_last_connected),
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(ts)),
                            )
                        }
                        DetailRow(stringResource(R.string.settings_playlist_detail_channels), playlist.channelCount.toString())
                        if (!playlist.epgUrl.isNullOrBlank()) {
                            DetailRow("EPG", playlist.epgUrl!!)
                        }
                    }
                }
            }

            item {
                Section(header = stringResource(R.string.settings_playlist_detail_actions_header), footer = null) {
                    // Rev 2 canon amendment 1: activation lives here on every
                    // form factor. The rail and sidebar make selection show the
                    // detail, so OK-to-activate cannot survive on those roots;
                    // this row replaces it one move away. Mirrors Apple's
                    // ServerDetailView.swift:243-253.
                    ActionRow(
                        icon = if (isActivePlaylist) Icons.Filled.CheckCircle
                        else Icons.Outlined.PowerSettingsNew,
                        label = stringResource(if (isActivePlaylist) R.string.settings_playlist_detail_active else R.string.settings_playlist_detail_set_active),
                        onClick = { playlist?.id?.let { viewModel.switchToPlaylist(it) } },
                        enabled = !isActivePlaylist,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    if (isTv && isActivePlaylist) {
                        ActionRow(
                            icon = Icons.Outlined.Edit,
                            label = stringResource(R.string.settings_playlist_detail_edit_playlist),
                            onClick = onEdit,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    }
                    if (isActivePlaylist) {
                    ActionRow(
                        icon = Icons.Outlined.Public,
                        label = stringResource(R.string.settings_playlist_detail_test_connection),
                        onClick = { viewModel.testConnection() },
                        running = state.testStatus is PlaylistViewModel.ActionStatus.Running,
                        status = state.testStatus,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ActionRow(
                        icon = Icons.Filled.Refresh,
                        label = stringResource(R.string.settings_playlist_detail_refresh_playlist),
                        onClick = { viewModel.refreshPlaylist() },
                        running = state.playlistRefreshStatus is PlaylistViewModel.ActionStatus.Running,
                        status = state.playlistRefreshStatus,
                    )
                    }
                    if (isActivePlaylist && !playlist.lanUrlString.isNullOrBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ActionRow(
                            icon = Icons.Outlined.Wifi,
                            label = stringResource(R.string.settings_playlist_detail_refresh_lan),
                            onClick = { viewModel.refreshLanDetection() },
                            running = state.lanRefreshStatus is PlaylistViewModel.ActionStatus.Running,
                            status = state.lanRefreshStatus,
                        )
                    }
                }
            }

            if (isActivePlaylist) item {
                Section(
                    header = stringResource(R.string.settings_playlist_detail_epg_cache_header),
                    footer = stringResource(R.string.settings_playlist_detail_epg_cache_footer),
                ) {
                    ActionRow(
                        icon = Icons.Filled.Refresh,
                        label = stringResource(R.string.settings_playlist_detail_refresh_epg),
                        onClick = { viewModel.refreshEpg() },
                        running = state.epgRefreshStatus is PlaylistViewModel.ActionStatus.Running,
                        status = state.epgRefreshStatus,
                    )
                    playlist.lastEpgRefreshedAt?.let { ts ->
                        Text(
                            text = stringResource(R.string.settings_playlist_detail_last_refreshed, DateFormat.getDateTimeInstance().format(Date(ts))),
                            style = MaterialTheme.typography.bodySmall.subtext(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (isActivePlaylist) item {
                Section(
                    header = stringResource(R.string.settings_playlist_detail_full_refresh_header),
                    footer = stringResource(R.string.settings_playlist_detail_full_refresh_footer),
                ) {
                    ActionRow(
                        icon = Icons.Filled.Refresh,
                        label = stringResource(R.string.settings_playlist_detail_refresh_everything),
                        destructive = true,
                        onClick = { confirmRefreshAll = true },
                        running = state.refreshAllStatus is PlaylistViewModel.ActionStatus.Running,
                        status = state.refreshAllStatus,
                    )
                }
            }

            item {
                Section(
                    header = stringResource(R.string.settings_playlist_detail_danger_header),
                    footer = stringResource(R.string.settings_playlist_detail_danger_footer),
                ) {
                    ActionRow(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.settings_playlist_detail_delete_playlist),
                        destructive = true,
                        onClick = { confirmDelete = true },
                    )
                }
            }
        }
        }
    }

    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_playlist_detail_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_playlist_detail_delete_text, playlist.name),
                )
            },
            confirmButton = {
                SettingsDialogTextButton(
                    label = stringResource(R.string.common_delete),
                    destructive = true,
                    onClick = {
                        confirmDelete = false
                        viewModel.deletePlaylist(playlist.id)
                        onBack()
                    },
                )
            },
            dismissButton = {
                SettingsDialogTextButton(label = stringResource(R.string.common_cancel), onClick = { confirmDelete = false })
            },
        )
    }

    if (confirmRefreshAll && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmRefreshAll = false },
            title = { Text(stringResource(R.string.settings_playlist_detail_refresh_all_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_playlist_detail_refresh_all_text, playlist.name),
                )
            },
            confirmButton = {
                SettingsDialogTextButton(
                    label = stringResource(R.string.settings_playlist_detail_refresh),
                    destructive = true,
                    onClick = {
                        confirmRefreshAll = false
                        viewModel.refreshEverything()
                    },
                )
            },
            dismissButton = {
                SettingsDialogTextButton(label = stringResource(R.string.common_cancel), onClick = { confirmRefreshAll = false })
            },
        )
    }
}

@Composable
private fun Section(
    header: String,
    footer: String?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = header.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
        ) { content() }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall.subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    // Lets the URL rows show a primary checkmark without tinting the URL text.
    iconTint: androidx.compose.ui.graphics.Color = valueColor,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.subtext(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(0.6f),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    running: Boolean = false,
    enabled: Boolean = true,
    status: PlaylistViewModel.ActionStatus = PlaylistViewModel.ActionStatus.Idle,
) {
    // The whole row is the click/focus target. The old shape (label inside a
    // TextButton) gave D-pad focus a tiny pill around the text only, which
    // looked out of place next to the full-width rows around it.
    var focused by remember { mutableStateOf(false) }
    val baseAccent = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    // Same rule as the shared SettingsActionRow: a disabled row stays FOCUSABLE
    // so it does not drop out of D-pad traversal and strand focus; it dims and
    // swallows the click instead. "Active Playlist" is exactly this state.
    val accent = if (enabled) baseAccent else baseAccent.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(
                if (focused) {
                    accent.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
            )
            // Guarded instead of clickable(enabled = !running) so the row
            // keeps its D-pad focus stop while a run is in flight; a disabled
            // clickable drops out of focus traversal entirely.
            .clickable(onClick = { if (!running && enabled) onClick() })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = accent,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            when (status) {
                is PlaylistViewModel.ActionStatus.Success -> Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textAccent,
                )
                is PlaylistViewModel.ActionStatus.Failure -> Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
        }
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else if (status is PlaylistViewModel.ActionStatus.Success) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
