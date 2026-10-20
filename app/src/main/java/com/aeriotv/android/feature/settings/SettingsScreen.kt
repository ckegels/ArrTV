package com.aeriotv.android.feature.settings

import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.theme.textAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import com.aeriotv.android.core.data.db.entity.sourceTypeDisplayLabel
import com.aeriotv.android.core.tv.TvQrLink
import com.aeriotv.android.core.tv.TvQrLinkDialog
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsNavRow
import com.aeriotv.android.feature.whatsnew.WhatsNewSheetOnDemand
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import com.aeriotv.android.ui.settings.settingsShowsBackArrow
import com.aeriotv.android.ui.settings.settingsPaneWidth
import com.aeriotv.android.ui.settings.settingsEyebrowStyle
import com.aeriotv.android.ui.settings.settingsFootnoteStyle
import com.aeriotv.android.ui.settings.settingsRowTitleStyle
import com.aeriotv.android.ui.settings.settingsRowValueStyle
import com.aeriotv.android.ui.settings.settingsTitleStyle
import java.text.DateFormat
import java.util.Date

/**
 * Settings root. Mirrors iOS SettingsView.swift section ordering + grouped-card
 * presentation (lines 150-496):
 *
 *  1. Playlists  - inline list of every saved playlist with tap-to-activate
 *                  (tap the active row again for details, edit, delete) and an
 *                  Add Playlist row. Footer surfaces the matching hints.
 *  2. App Settings - Appearance / App Behaviors / Multiview / Network rows
 *                  inside a single grouped card.
 *  3. Sync       - current cut routes through to the full SyncSettingsScreen.
 *                  iOS surfaces the toggle inline here; that follow-up lands
 *                  alongside the Google Drive Sync rewrite that mirrors the
 *                  iCloud Sync toggle / Sync Now / Clear Data set.
 *  4. DVR        - single nav row.
 *  5. Developer  - single nav row.
 *  6. About      - Device / System / App Version / First Installed /
 *                  Last Updated + Copy / Developer Website / Report an Issue.
 *
 * Each section is a [SettingsSectionGroup] - uppercase header in primary
 * tint, rounded card containing the rows separated by hairline dividers,
 * optional footer text in muted-tint below. Mirrors iOS .insetGrouped list
 * style + sectionHeaderStyle().
 */
/**
 * Which blocks of the Settings root [SettingsScreen] renders.
 *
 * Phase B3: on a phone the root is one scrolling list of everything. In a
 * two-pane host the sidebar takes over the section list, and the Playlists and
 * About blocks - which have no screen of their own - become detail panes. Both
 * panes are this same screen filtered down, so the copy, ordering, and row
 * behavior are literally the phone's.
 */
enum class SettingsRootContent(@StringRes val titleRes: Int) {
    Full(R.string.common_settings),
    PlaylistsOnly(R.string.settings2_playlists),
    AboutOnly(R.string.settings2_about),
    ;

    val title: String
        @Composable get() = stringResource(titleRes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSectionClick: (SettingsSection) -> Unit,
    /** Back affordance for the pushed About page; unused by the root list. */
    onBack: () -> Unit = {},
    onOpenPlaylistDetail: (String) -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onAddPlaylist: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel(),
    // Phase B3: the two-pane hosts render the Playlists and About blocks as
    // detail panes. Rather than duplicate either block (and risk the copy
    // drifting from the phone root, which the plan freezes), the same screen
    // renders a subset of itself.
    content: SettingsRootContent = SettingsRootContent.Full,
) {
    val fullRoot = content == SettingsRootContent.Full
    val context = androidx.compose.ui.platform.LocalContext.current
    // Flavor-gated: the App Updates row only exists on the GitHub/sideload
    // channel (play flavor binds a disabled no-op manager).
    val updateVm: com.aeriotv.android.feature.update.UpdateViewModel = hiltViewModel()
    val updaterEnabled = updateVm.isEnabled
    // Root row values: Sync reads On/Off, About reads the installed version.
    val settingsVm: SettingsViewModel = hiltViewModel()
    val syncEnabled by settingsVm.syncMasterEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    // LIVE from the DAO, not the UiState snapshot (Logan 2026-09-16): the
    // radio button must fill in on the new row as soon as the switch commits.
    val activeIdLive by viewModel.activeIdLive
        .collectAsStateWithLifecycle(initialValue = state.playlist?.id)
    val activeId = activeIdLive


    val packageInfo = remember {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }.getOrNull()
    }
    val installedAt = packageInfo?.firstInstallTime ?: 0L
    val updatedAt = packageInfo?.lastUpdateTime ?: 0L
    val versionName = packageInfo?.versionName ?: "0.1.0"

    // TV: external links surface as a QR dialog (no browser on Android TV);
    // phones keep the ACTION_VIEW intent in openUrl.
    val isTv = rememberIsTvDevice()
    var qrLink by remember { mutableStateOf<TvQrLink?>(null) }
    var showWhatsNew by remember { mutableStateOf(false) }
    val qrCaption = stringResource(R.string.settings2_qr_scan_caption)
    val websiteTitle = stringResource(R.string.settings2_developer_website)
    val reportIssueTitle = stringResource(R.string.settings2_report_issue)

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = content.title,
                    style = settingsTitleStyle(),
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                // About is a pushed page like any other sub-screen, so it gets
                // the same back arrow (TV and pane hosts suppress it, as there
                // the remote's BACK or the rail beside it does the popping).
                if (content == SettingsRootContent.AboutOnly && settingsShowsBackArrow()) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
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

        // Center + cap the form on wider viewports. The Pixel Tablet,
        // unfolded foldables, AND phone-landscape (~997 dp wide on a
        // Pixel 10 Pro XL) all hit the Expanded breakpoint, which without
        // the cap stretches a single column of settings rows edge-to-edge
        // and turns the playlist card into a 900-dp-wide stripe. iOS gets
        // the equivalent narrowing for free via insetGrouped.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        LazyColumn(
            // Full-screen measures the WINDOW; a detail pane must measure the
            // PANE or it sizes itself against the whole tablet and overflows.
            modifier = (if (fullRoot) Modifier.adaptiveFormWidth() else Modifier.settingsPaneWidth())
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                // TV: keep the last row above the ~5% bottom overscan band.
                // Phones reserve the floating tab pill / cast controls the same
                // way every other scrolling surface does.
                bottom = if (rememberIsTvDevice()) 28.dp else LocalTabBarBottomInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // MARK: Playlists
            if (content != SettingsRootContent.AboutOnly) item("playlists") {
                PlaylistsSection(
                    playlists = playlists,
                    activeId = activeId,
                    // In a pane the top bar already reads "Playlists".
                    showHeader = fullRoot,
                    paneHost = content == SettingsRootContent.PlaylistsOnly || isTv,
                    onTap = { pl ->
                        // Rev 2 canon amendment 1: on every RAIL/SIDEBAR form
                        // factor - the tablet pane and ALL of TV - a playlist
                        // row enters its detail, where the Set Active row
                        // lives. TV is included even though its rail host is
                        // still pending, because the old rule made a detail
                        // page unreachable whenever nothing was active yet
                        // (e.g. straight after a Drive restore): only the
                        // ACTIVE playlist opened, and OK on the others just
                        // tried to activate. The phone root is untouched.
                        if (content == SettingsRootContent.PlaylistsOnly || isTv) {
                            onOpenPlaylistDetail(pl.id)
                        } else if (pl.id == activeId) {
                            onOpenPlaylistDetail(pl.id)
                        } else {
                            viewModel.switchToPlaylist(pl.id)
                        }
                    },
                    onAdd = onAddPlaylist,
                    onManage = onOpenPlaylists,
                )
            }

            // MARK: App Settings / Sync / DVR / Developer
            //
            // Sourced from the shared canon so the sidebar in the two-pane
            // hosts cannot drift from this list (plan B7: frozen canon).
            if (fullRoot) {
                items(
                    items = visibleSettingsSections(isTv = isTv, updaterEnabled = updaterEnabled),
                    key = { it.key },
                ) { group ->
                    SettingsSectionGroup(
                        header = group.header?.let { stringResource(it) }.orEmpty(),
                        rows = group.sections,
                        onClick = onSectionClick,
                        footer = group.footer,
                        valueFor = { section ->
                            if (section == SettingsSection.About) versionName else null
                        },
                        syncEnabled = syncEnabled,
                    )
                }
            }

            // MARK: About
            //
            // Settings phase 1: About is a PUSHED page (and a pane in the
            // two-pane hosts), reached from the closing group's About row. It
            // is no longer inlined at the bottom of the root list.
            if (content == SettingsRootContent.AboutOnly) item("about") {
                AboutSection(
                    showHeader = fullRoot,
                    onShowWhatsNew = { showWhatsNew = true },
                    versionName = versionName,
                    versionCode = packageInfo?.longVersionCode ?: 0L,
                    installedAt = installedAt,
                    updatedAt = updatedAt,
                    onCopy = {
                        val text = buildAboutClipboard(
                            versionName,
                            packageInfo?.longVersionCode ?: 0L,
                            installedAt,
                            updatedAt,
                        )
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("AerioTV diagnostics", text))
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.settings2_copied_diagnostics),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onOpenWebsite = {
                        val url = "https://github.com/jonzey231/AerioTV-Android"
                        if (isTv) {
                            qrLink = TvQrLink(
                                title = websiteTitle,
                                caption = qrCaption,
                                url = url,
                            )
                        } else {
                            openUrl(context, url)
                        }
                    },
                    onOpenLicenses = onOpenLicenses,
                    onReportIssue = {
                        val url = "https://github.com/jonzey231/AerioTV-Android/issues/new"
                        if (isTv) {
                            qrLink = TvQrLink(
                                title = reportIssueTitle,
                                caption = qrCaption,
                                url = url,
                            )
                        } else {
                            openUrl(context, url)
                        }
                    },
                )
            }
        }
        }
    }

    if (showWhatsNew) {
        WhatsNewSheetOnDemand(onDismiss = { showWhatsNew = false })
    }

    qrLink?.let { link ->
        TvQrLinkDialog(
            title = link.title,
            caption = link.caption,
            url = link.url,
            onDismiss = { qrLink = null },
        )
    }
}

// MARK: - Playlists section

@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistEntity>,
    activeId: String?,
    showHeader: Boolean = true,
    paneHost: Boolean = false,
    onTap: (PlaylistEntity) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
) {
    Column {
        if (showHeader) {
            SectionHeader(stringResource(R.string.settings2_playlists))
            Spacer(Modifier.height(6.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
        ) {
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.settings2_no_playlists),
                        style = settingsRowValueStyle().subtext(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                playlists.forEachIndexed { index, pl ->
                    if (index > 0) RowDivider()
                    PlaylistRow(
                        playlist = pl,
                        isActive = pl.id == activeId,
                        onTap = { onTap(pl) },
                    )
                }
                RowDivider()
            }
            // Add Playlist row - iOS calls this out with a cyan plus glyph
            // (SettingsView line 206-220).
            var addFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { addFocused = it.isFocused }
                    .groupRowFocus(addFocused)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.settings2_add_playlist),
                    style = settingsRowValueStyle(),
                    color = MaterialTheme.colorScheme.textAccent,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (playlists.size > 1) {
                RowDivider()
                var manageFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { manageFocused = it.isFocused }
                        .groupRowFocus(manageFocused)
                        .clickable(onClick = onManage)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings2_manage_playlists),
                        style = settingsRowValueStyle(),
                        color = MaterialTheme.colorScheme.textAccent,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (playlists.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // Input-appropriate verbs: a remote has no "tap".
            if (paneHost) {
                // Rail/sidebar form factors enter the detail on select, so the
                // activate verb moved there. The phone strings below are
                // untouched (frozen canon).
                SectionFooter(stringResource(R.string.settings2_playlists_footer_pane_open))
                if (playlists.size > 1) {
                    SectionFooter(stringResource(R.string.settings2_playlists_footer_pane_reorder))
                }
            } else {
                SectionFooter(stringResource(R.string.settings2_playlists_footer_touch_active))
                if (playlists.size > 1) {
                    SectionFooter(stringResource(R.string.settings2_playlists_footer_touch_reorder))
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    isActive: Boolean,
    onTap: () -> Unit,
) {
    // No long-press menu: editing and deleting live on the Playlist Detail
    // screen (open the active playlist), so the row is a plain click target.
    val isTv = rememberIsTvDevice()
    var focused by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .groupRowFocus(focused)
                .clickable(onClick = onTap)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Radio-button active marker - filled cyan dot inside a ring on
            // the active row, empty ring on the rest. Matches the iOS
            // SettingsView footer hint "Tap ○ to set the active playlist".
            Icon(
                imageVector = if (isActive) Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isActive) stringResource(R.string.settings2_playlist_active)
                else stringResource(R.string.settings2_playlist_set_active),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = settingsRowTitleStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
                val channelsText = pluralStringResource(
                    R.plurals.settings2_channel_count,
                    playlist.channelCount,
                    playlist.channelCount,
                )
                val subtitle = buildString {
                    append(channelsText)
                    // Shared pretty-printer (PlaylistEntity.sourceTypeDisplayLabel)
                    // keeps this subtitle in lockstep with the Playlist Detail
                    // Type row.
                    val pretty = playlist.sourceTypeDisplayLabel()
                    if (pretty.isNotBlank()) append("  ·  ").append(pretty)
                }
                Text(
                    text = subtitle,
                    style = settingsFootnoteStyle().subtext(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


/**
 * D-pad focus highlight for rows inside the grouped settings cards. The
 * default Material ripple is nearly invisible at 10 feet; this paints the
 * same accent wash the sub-screen rows use. No-op while unfocused (touch).
 */
@Composable
private fun Modifier.groupRowFocus(focused: Boolean): Modifier = this.background(
    if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
)

// MARK: - Generic grouped section

@Composable
private fun SettingsSectionGroup(
    header: String,
    rows: List<SettingsSection>,
    onClick: (SettingsSection) -> Unit,
    footer: String? = null,
    valueFor: (SettingsSection) -> String? = { null },
    syncEnabled: Boolean = false,
) {
    Column {
        // A blank header means the group carries no label (the closing
        // Developer / About group).
        if (header.isNotBlank()) {
            SectionHeader(header)
            Spacer(Modifier.height(6.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
        ) {
            rows.forEachIndexed { index, section ->
                if (index > 0) RowDivider()
                SectionNavRow(
                    section = section,
                    value = valueFor(section),
                    syncEnabled = syncEnabled,
                    onClick = { onClick(section) },
                )
            }
        }
        footer?.let {
            Spacer(Modifier.height(8.dp))
            SectionFooter(it)
        }
    }
}

@Composable
private fun SectionNavRow(
    section: SettingsSection,
    onClick: () -> Unit,
    value: String? = null,
    syncEnabled: Boolean = false,
) {
    // Phase B1: delegates to the shared row so the root gets the same
    // border+scale+wash focus treatment as every subpage (the old
    // groupRowFocus was noticeably weaker on TV).
    SettingsNavRow(
        title = section.title,
        subtitle = settingsSectionSubtitle(section, syncEnabled),
        icon = section.icon,
        value = value,
        onClick = onClick,
    )
}

// MARK: - About section

@Composable
private fun AboutSection(
    showHeader: Boolean = true,
    onShowWhatsNew: () -> Unit,
    versionName: String,
    versionCode: Long,
    installedAt: Long,
    updatedAt: Long,
    onCopy: () -> Unit,
    onOpenWebsite: () -> Unit,
    onReportIssue: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Column {
        if (showHeader) {
            SectionHeader(stringResource(R.string.settings2_about))
            Spacer(Modifier.height(6.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
        ) {
            val unknown = stringResource(R.string.common_unknown)
            AboutInfoRow(stringResource(R.string.settings2_about_device), deviceDisplayName())
            RowDivider()
            AboutInfoRow(stringResource(R.string.common_system), "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            RowDivider()
            AboutVersionRow(
                value = "$versionName ($versionCode)",
                onClick = onShowWhatsNew,
            )
            RowDivider()
            AboutInfoRow(
                stringResource(R.string.settings2_about_first_installed),
                formatInstallTime(installedAt, unknown),
            )
            RowDivider()
            AboutInfoRow(
                stringResource(R.string.settings2_about_last_updated),
                if (updatedAt > 0 && updatedAt != installedAt) formatInstallTime(updatedAt, unknown)
                else stringResource(R.string.settings2_about_never),
            )
            RowDivider()
            AboutActionRow(
                stringResource(R.string.settings2_about_copy),
                Icons.Filled.ContentCopy,
                onClick = onCopy,
            )
            RowDivider()
            AboutActionRow(
                stringResource(R.string.settings2_developer_website),
                Icons.Outlined.OpenInNew,
                onClick = onOpenWebsite,
                external = true,
            )
            RowDivider()
            AboutActionRow(
                stringResource(R.string.settings2_report_issue),
                Icons.Outlined.BugReport,
                onClick = onReportIssue,
                external = true,
            )
            RowDivider()
            AboutActionRow(
                stringResource(R.string.settings2_about_licenses),
                Icons.Outlined.Description,
                onClick = onOpenLicenses,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings2_about_in_memory),
            style = settingsFootnoteStyle().subtext(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = settingsRowValueStyle().subtext(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = settingsRowValueStyle(),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * App Version row. Reads as an info row (label + value) but is clickable and,
 * on TV, focusable with the same card highlight as the action rows below it,
 * so the D-pad can reach it and DPAD_CENTER opens the What's New notes for
 * the installed build. The trailing "What's New" hint is the only affordance;
 * nothing about the launch-time gate changes.
 */
@Composable
private fun AboutVersionRow(value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .groupRowFocus(focused)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings2_about_app_version),
            style = settingsRowValueStyle().subtext(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = settingsRowValueStyle(),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = stringResource(R.string.settings2_about_whats_new),
            style = settingsFootnoteStyle(),
            color = MaterialTheme.colorScheme.textAccent,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AboutActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    external: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .groupRowFocus(focused)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            style = settingsRowValueStyle(),
            color = MaterialTheme.colorScheme.textAccent,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (external) {
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// MARK: - Section shell helpers

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = settingsEyebrowStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text = text,
        // TV takes the tvOS footnote (20pt halved); PHONES keep labelSmall
        // verbatim - the shared helper falls back to bodySmall, which would
        // have quietly enlarged frozen phone canon.
        style = (if (rememberIsTvDevice()) settingsFootnoteStyle()
        else MaterialTheme.typography.labelSmall).subtext(),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f),
        modifier = Modifier.padding(start = 14.dp),
    )
}

/**
 * Marketing name for this device. Several makers already prefix the model with
 * the brand ("Google TV Streamer" on a Google box), so blindly joining the two
 * printed "Google Google TV Streamer" in the About panel.
 */
private fun deviceDisplayName(): String {
    val manufacturer = android.os.Build.MANUFACTURER.orEmpty().trim()
    val model = android.os.Build.MODEL.orEmpty().trim()
    return when {
        model.isEmpty() -> manufacturer
        manufacturer.isEmpty() -> model
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "$manufacturer $model"
    }
}

/** [unknown] is the on-screen word for a missing time; the diagnostics copy keeps English. */
private fun formatInstallTime(ms: Long, unknown: String = "Unknown"): String {
    if (ms <= 0L) return unknown
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
}

private fun buildAboutClipboard(
    versionName: String,
    versionCode: Long,
    installedAt: Long,
    updatedAt: Long,
): String = buildString {
    appendLine("AerioTV diagnostics")
    appendLine("Device: ${deviceDisplayName()}")
    appendLine("System: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    appendLine("App Version: $versionName ($versionCode)")
    appendLine("First Installed: ${formatInstallTime(installedAt)}")
    appendLine("Last Updated: ${if (updatedAt > 0 && updatedAt != installedAt) formatInstallTime(updatedAt) else "Never"}")
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.settings2_no_browser, url),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
}

/**
 * Settings tree sub-sections. Each carries its title / subtitle / icon for
 * the parent [SettingsScreen] rows and a stable identifier for navigation.
 */
enum class SettingsSection(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val icon: ImageVector,
) {
    LiveTV(
        titleRes = R.string.settings2_section_live_tv,
        subtitleRes = R.string.settings2_section_live_tv_subtitle,
        icon = Icons.Filled.LiveTv,
    ),
    Player(
        titleRes = R.string.settings2_section_player,
        subtitleRes = R.string.settings2_section_player_subtitle,
        icon = Icons.Outlined.PlayCircle,
    ),
    MoviesAndTvShows(
        titleRes = R.string.settings2_section_movies,
        subtitleRes = R.string.settings2_section_movies_subtitle,
        icon = Icons.Filled.Movie,
    ),
    DvrSettings(
        titleRes = R.string.settings2_section_dvr,
        subtitleRes = R.string.settings2_section_dvr_subtitle,
        icon = Icons.Filled.FiberManualRecord,
    ),
    Appearance(
        titleRes = R.string.settings2_section_appearance,
        subtitleRes = R.string.settings2_section_appearance_subtitle,
        icon = Icons.Filled.Palette,
    ),
    General(
        titleRes = R.string.settings2_section_general,
        subtitleRes = R.string.settings2_section_general_subtitle,
        icon = Icons.Filled.Tune,
    ),
    RemoteControl(
        titleRes = R.string.settings2_section_remote_control,
        subtitleRes = R.string.settings2_section_remote_control_subtitle,
        icon = Icons.Filled.SettingsRemote,
    ),
    Sync(
        // Subtitle comes from [settingsSectionSubtitle]: the row reads the live
        // On / Off state instead of a description. Apple does the same, and the
        // long string truncated on the Android TV rail. The description lives on
        // the Sync page's own Drive Sync footer.
        titleRes = R.string.settings2_section_sync,
        subtitleRes = null,
        icon = Icons.Filled.Cloud,
    ),
    AppUpdates(
        titleRes = R.string.settings2_section_updates,
        subtitleRes = R.string.settings2_section_updates_subtitle,
        icon = Icons.Filled.SystemUpdate,
    ),
    Developer(
        titleRes = R.string.settings2_section_developer,
        subtitleRes = R.string.settings2_section_developer_subtitle,
        icon = Icons.Outlined.BugReport,
    ),
    About(
        titleRes = R.string.settings2_about,
        subtitleRes = null,
        icon = Icons.Outlined.Info,
    ),
    ;

    val title: String
        @Composable get() = stringResource(titleRes)

    val subtitle: String?
        @Composable get() = subtitleRes?.let { stringResource(it) }
}

/**
 * Subtitle for a section row in the root list, the tablet sidebar and the TV
 * rail. Everything but Sync uses its static enum subtitle; Sync reports whether
 * Drive sync is currently on.
 */
@Composable
fun settingsSectionSubtitle(section: SettingsSection, syncEnabled: Boolean): String? =
    if (section == SettingsSection.Sync) {
        stringResource(if (syncEnabled) R.string.common_on else R.string.common_off)
    } else {
        section.subtitle
    }
