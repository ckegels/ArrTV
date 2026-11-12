package com.aeriotv.android.feature.settings

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aeriotv.android.R
import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import com.aeriotv.android.ui.scale.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import com.aeriotv.android.core.tv.TvActionMenuDialog
import com.aeriotv.android.core.tv.TvMenuAction
import com.aeriotv.android.core.tv.rememberTvMenuGuard
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsDialogTextButton
import com.aeriotv.android.ui.settings.SettingsHeaderTextButton
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset

/**
 * Multi-playlist switcher reachable from Settings root. Lists every saved
 * playlist with an Active checkmark on the current one; select to switch,
 * long-press for delete confirm (on TV the long-press opens a Move Up /
 * Move Down / Delete menu, since drag reorder is touch-only). The top-bar
 * add action routes to the same
 * Choose-Source-Type onboarding screen used for first-run setup, except after
 * the new playlist persists the user pops back here instead of being thrown
 * into the player.
 *
 * Mirrors iOS Playlists screen (project_aeriotv_ios_canon.md "Settings" >
 * "Playlists section" implicit in canon since the iOS test-server screenshots
 * show only a single playlist; multi-playlist UX is taken from iOS source).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenPlaylistDetail: (String) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlists: List<PlaylistEntity> by viewModel.allPlaylists
        .collectAsStateWithLifecycle(initialValue = emptyList<PlaylistEntity>())
    val state by viewModel.state.collectAsStateWithLifecycle()
    // LIVE from the DAO, not the UiState snapshot: the selection indicator
    // has to move to the newly active row the moment the switch commits,
    // rather than after its channel fetch returns (Logan 2026-09-16).
    val activeIdLive by viewModel.activeIdLive
        .collectAsStateWithLifecycle(initialValue = state.playlist?.id)
    val activeId = activeIdLive
    val isTv = rememberIsTvDevice()

    var pendingDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    // TV-only long-press menu target (Move Up / Move Down / Delete / Cancel).
    var menuFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    val tvGuard = rememberTvMenuGuard()

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.settings_playlists_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                // No back arrow on Android TV -- the remote BACK pops it.
                if (!isTv) {
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
                if (isTv) {
                    // Focusable pill inset to the overscan margin; a bare
                    // IconButton has no visible D-pad focus state.
                    SettingsHeaderTextButton(label = stringResource(R.string.common_add), onClick = onAddPlaylist)
                } else {
                    IconButton(onClick = onAddPlaylist) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.settings_playlists_add_playlist),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isTv) stringResource(R.string.settings_playlists_empty_tv)
                    else stringResource(R.string.settings_playlists_empty_phone),
                    style = MaterialTheme.typography.bodyMedium.subtext(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Local working copy so the drag preview updates fluidly without
        // hitting Room on every onMove frame. We commit the order to the
        // repository on drag end (applyPlaylistOrder).
        var workingOrder by remember(playlists) { mutableStateOf(playlists) }
        androidx.compose.runtime.LaunchedEffect(playlists) {
            workingOrder = playlists
        }
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            workingOrder = workingOrder.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.adaptiveFormWidth(),
            // 104dp bottom clears the MainScaffold NavigationBar so the
            // last playlist row stays draggable down to the very bottom.
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = LocalTabBarBottomInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(workingOrder, key = { it.id }) { pl ->
                ReorderableItem(reorderState, key = pl.id) { isDragging ->
                    SwipeablePlaylistRow(
                        playlist = pl,
                        isActive = pl.id == activeId,
                        isDragging = isDragging,
                        // Guarded so the OK-release after a TV long-press can't
                        // also register as a tap on the row (see TvMenuGuard).
                        onTap = tvGuard.wrap {
                            // Same rule as the root/pane list: on TV every row
                            // enters its detail (Set Active lives there), so a
                            // non-active playlist is reachable. Phone keeps
                            // tap-to-activate.
                            if (isTv || pl.id == activeId) {
                                onOpenPlaylistDetail(pl.id)
                            } else {
                                viewModel.switchToPlaylist(pl.id)
                            }
                        },
                        onLongPress = {
                            if (isTv) {
                                menuFor = pl
                                tvGuard.arm()
                            } else {
                                pendingDelete = pl
                            }
                        },
                        onSwipedToDelete = { pendingDelete = pl },
                        // Touch-only affordance; D-pad reorder lives in the
                        // long-press menu instead.
                        dragHandle = if (isTv) {
                            null
                        } else {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.settings_playlists_drag_to_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .draggableHandle(
                                            onDragStopped = {
                                                viewModel.applyPlaylistOrder(workingOrder.map { it.id })
                                            },
                                        )
                                        .size(24.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        menuFor?.let { pl ->
            val index = workingOrder.indexOfFirst { it.id == pl.id }
            fun moveTo(target: Int) {
                val swapped = workingOrder.toMutableList().apply { add(target, removeAt(index)) }
                workingOrder = swapped
                viewModel.applyPlaylistOrder(swapped.map { it.id })
            }
            // TvActionMenuDialog dismisses (menuFor = null) before running the
            // row's onClick, so the actions only carry their own effect.
            TvActionMenuDialog(
                title = pl.name,
                actions = buildList {
                    if (index > 0) {
                        add(TvMenuAction(stringResource(R.string.settings_playlists_move_up), Icons.Filled.KeyboardArrowUp) { moveTo(index - 1) })
                    }
                    if (index in 0 until workingOrder.lastIndex) {
                        add(TvMenuAction(stringResource(R.string.settings_playlists_move_down), Icons.Filled.KeyboardArrowDown) { moveTo(index + 1) })
                    }
                    add(TvMenuAction(stringResource(R.string.common_delete), Icons.Filled.Delete, destructive = true) { pendingDelete = pl })
                    add(TvMenuAction(stringResource(R.string.common_cancel), Icons.Filled.Close) {})
                },
                guard = tvGuard,
                onDismiss = { menuFor = null },
            )
        }
        }
    }

    pendingDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.settings_playlists_delete_title)) },
            text = {
                Text(
                    stringResource(if (pl.id == activeId) R.string.settings_playlists_delete_text_active else R.string.settings_playlists_delete_text, pl.name),
                )
            },
            confirmButton = {
                SettingsDialogTextButton(
                    label = stringResource(R.string.common_delete),
                    destructive = true,
                    onClick = {
                        val id = pl.id
                        pendingDelete = null
                        viewModel.deletePlaylist(id)
                    },
                )
            },
            dismissButton = {
                SettingsDialogTextButton(label = stringResource(R.string.common_cancel), onClick = { pendingDelete = null })
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    isActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    dragHandle: @Composable (() -> Unit)? = null,
    isDragging: Boolean = false,
) {
    val elevation = if (isDragging) 6.dp else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            )
            .dpadFocusRing(RoundedCornerShape(12.dp), washTint = MaterialTheme.colorScheme.primary)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dragHandle != null) {
            dragHandle()
            Spacer(Modifier.size(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.settings_playlists_row_subtitle, playlist.channelCount, playlist.channelCount, playlist.sourceType),
                style = MaterialTheme.typography.bodySmall.subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.settings_playlists_active),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = "▸",
            style = MaterialTheme.typography.bodyLarge.subtext(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Wraps a [PlaylistRow] in Material 3's SwipeToDismissBox so the user can
 * swipe either direction to delete (mirrors iOS SettingsView swipe action).
 * The dismiss action surfaces the same confirmation dialog as long-press, so
 * a wayward swipe never silently destroys a playlist row + its credentials.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeablePlaylistRow(
    playlist: com.aeriotv.android.core.data.db.entity.PlaylistEntity,
    isActive: Boolean,
    isDragging: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipedToDelete: () -> Unit,
    dragHandle: @Composable (() -> Unit)?,
) {
    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd ||
                value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
            ) {
                onSwipedToDelete()
                // Don't actually dismiss the row here - the confirmation dialog
                // owns the delete. Returning false snaps the row back to settled.
                false
            } else {
                true
            }
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Only paint the red action WHILE a swipe is in progress. The
            // playlist row's own fill is translucent, so an always-drawn
            // background bled through every settled row (screenshot pass).
            if (dismissState.dismissDirection !=
                androidx.compose.material3.SwipeToDismissBoxValue.Settled
            ) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection ==
                    androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
                ) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(
                    text = stringResource(R.string.common_delete),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
        PlaylistRow(
            playlist = playlist,
            isActive = isActive,
            isDragging = isDragging,
            dragHandle = dragHandle,
            onTap = onTap,
            onLongPress = onLongPress,
        )
    }
}

