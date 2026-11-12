package com.aeriotv.android.feature.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.category.CategoryPaletteState
import com.aeriotv.android.core.category.ProgramCategory
import com.aeriotv.android.core.tv.TvActionMenuDialog
import com.aeriotv.android.core.tv.TvMenuAction
import com.aeriotv.android.core.tv.rememberTvMenuGuard
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.ui.adaptive.rememberViewport
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsRowContainer
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsSelectionRow
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.settings.dpadFocusWash
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import com.aeriotv.android.ui.theme.textAccent

/**
 * Settings > Live TV. Everything that shapes the guide and the channel list:
 * presentation toggles, the List view choice, the TV guide layout, groups,
 * program badges, the Live TV display scale, and the category colors.
 *
 * Settings phase 1 regroup: the rows here came from App Behaviors (default
 * view, groups, guide layout, badges), Appearance (presentation, artwork,
 * display scale, category colors and palette) and, on TV, the Group Selection
 * row that used to sit under Remote Control. Every persisted key, control type
 * and string is carried over unchanged.
 */
@Composable
fun LiveTvSettingsScreen(
    onBack: () -> Unit,
    onOpenAddMoreCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTvDevice()
    // TV has no Live TV List view (Logan 2026-09-16, see core.ui.TvListView),
    // so the list-only options are hidden and the list-facing copy is written
    // for the Guide's channel column instead. The keys keep their values.
    val listViewShown = !isTv || com.aeriotv.android.core.ui.TvListView.ENABLED

    val showChannelLogos by viewModel.showChannelLogos.collectAsStateWithLifecycle(initialValue = true)
    val showChannelNumbers by viewModel.showChannelNumbers.collectAsStateWithLifecycle(initialValue = true)
    val showChannelNames by viewModel.showChannelNames.collectAsStateWithLifecycle(initialValue = true)
    val showProgramSubtitles by viewModel.showProgramSubtitles.collectAsStateWithLifecycle(initialValue = true)
    val roundedArtwork by viewModel.roundedArtwork.collectAsStateWithLifecycle(initialValue = true)
    val roundedArtworkGuide by viewModel.roundedArtworkGuide.collectAsStateWithLifecycle(initialValue = false)
    val defaultLiveTVView by viewModel.defaultLiveTVView.collectAsStateWithLifecycle(initialValue = "")
    val liveTvLayout by viewModel.liveTvLayout.collectAsStateWithLifecycle(initialValue = "basic")
    val defaultGroupToken by viewModel.defaultGroupToken.collectAsStateWithLifecycle()
    val defaultGroupOptions by viewModel.defaultGroupOptions.collectAsStateWithLifecycle()
    val phoneGroupSelector by viewModel.phoneGroupSelector.collectAsStateWithLifecycle(initialValue = "sidebar")
    val guideGroupSelector by viewModel.guideGroupSelector.collectAsStateWithLifecycle(initialValue = "pills")
    val showEpgBadges by viewModel.showEpgBadges(isTv).collectAsStateWithLifecycle(initialValue = true)
    val hiddenEpgBadges by viewModel.hiddenEpgBadges.collectAsStateWithLifecycle(initialValue = emptySet())
    val scaleLiveTV by viewModel.displayScaleLiveTV.collectAsStateWithLifecycle(initialValue = 1.0f)
    val palette by viewModel.categoryPalette.collectAsStateWithLifecycle(initialValue = CategoryPaletteState.Default)

    var editingGroupSelector by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<ProgramCategory?>(null) }
    val menuGuard = rememberTvMenuGuard()

    Column(modifier = Modifier.fillMaxSize()) {
        val context = LocalContext.current
        SettingsDetailTopBar(title = stringResource(R.string.settings_livetv_title), onBack = onBack)

        val vp = rememberViewport()
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = if (vp.formMaxWidth != Dp.Unspecified) {
                    Modifier.widthIn(max = vp.formMaxWidth)
                } else {
                    Modifier
                },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = LocalTabBarBottomInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // MARK: Guide Presentation
                item("guide-presentation") {
                    SettingsSection(
                        header = stringResource(R.string.settings_livetv_presentation_header),
                        footer = (
                            if (listViewShown) {
                                stringResource(R.string.settings_livetv_presentation_footer_list)
                            } else {
                                stringResource(R.string.settings_livetv_presentation_footer_guide)
                            }
                            ) + " " + stringResource(R.string.settings_livetv_presentation_footer_rounded),
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_livetv_show_logos),
                            subtitle = if (listViewShown) {
                                stringResource(R.string.settings_livetv_show_logos_list)
                            } else {
                                stringResource(R.string.settings_livetv_show_logos_guide)
                            },
                            checked = showChannelLogos,
                            onCheckedChange = viewModel::setShowChannelLogos,
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_livetv_show_numbers),
                            subtitle = if (listViewShown) {
                                stringResource(R.string.settings_livetv_show_numbers_list)
                            } else {
                                stringResource(R.string.settings_livetv_show_numbers_guide)
                            },
                            checked = showChannelNumbers,
                            onCheckedChange = viewModel::setShowChannelNumbers,
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_livetv_show_names),
                            subtitle = stringResource(R.string.settings_livetv_show_names_subtitle),
                            checked = showChannelNames,
                            onCheckedChange = viewModel::setShowChannelNames,
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_livetv_show_subtitles),
                            subtitle = if (listViewShown) {
                                stringResource(R.string.settings_livetv_show_subtitles_list)
                            } else {
                                stringResource(R.string.settings_livetv_show_subtitles_guide)
                            },
                            checked = showProgramSubtitles,
                            onCheckedChange = viewModel::setShowProgramSubtitles,
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_livetv_rounded_guide),
                            checked = roundedArtworkGuide,
                            onCheckedChange = viewModel::setRoundedArtworkGuide,
                        )
                    }
                }

                // MARK: List view
                //
                // Hidden on TV under the TvListView gate, exactly as the two
                // source screens gated these rows before the regroup.
                if (listViewShown) item("list-view") {
                    SettingsSection(
                        header = stringResource(R.string.settings_livetv_list_view_header),
                        footer = stringResource(R.string.settings_livetv_list_view_footer),
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_livetv_rounded_list),
                            checked = roundedArtwork,
                            onCheckedChange = viewModel::setRoundedArtwork,
                        )
                        val current = defaultLiveTVView.lowercase()
                        DEFAULT_LIVE_TV_VIEW_OPTIONS.forEach { (value, label) ->
                            SettingsSelectionRow(
                                label = stringResource(label),
                                selected = current == value,
                                onClick = { viewModel.setDefaultLiveTVView(value) },
                            )
                        }
                    }
                }

                // MARK: Guide Layout (TV)
                if (isTv) item("guide-layout") {
                    SettingsSection(header = stringResource(R.string.settings_livetv_guide_layout_header)) {
                        SettingsSelectionRow(
                            label = stringResource(R.string.settings_livetv_layout_basic),
                            subtitle = stringResource(R.string.settings_livetv_layout_basic_subtitle),
                            selected = liveTvLayout != "preview",
                            onClick = { viewModel.setLiveTvLayout("basic") },
                        )
                        SettingsSelectionRow(
                            label = stringResource(R.string.settings_livetv_layout_preview),
                            subtitle = stringResource(R.string.settings_livetv_layout_preview_subtitle),
                            selected = liveTvLayout == "preview",
                            onClick = { viewModel.setLiveTvLayout("preview") },
                        )
                    }
                }

                // MARK: Groups
                item("groups") {
                    SettingsSection(
                        header = stringResource(R.string.settings_livetv_groups_header),
                        footer = stringResource(R.string.settings_livetv_groups_footer),
                    ) {
                        val fixedTokens = listOf(
                            com.aeriotv.android.feature.playlist.PlaylistViewModel.ALL_GROUPS,
                            com.aeriotv.android.feature.playlist.PlaylistViewModel.FAVORITES_GROUP,
                            com.aeriotv.android.feature.playlist.PlaylistViewModel.RECENT_GROUP,
                        )
                        (fixedTokens + defaultGroupOptions).forEach { token ->
                            val isAll =
                                token == com.aeriotv.android.feature.playlist.PlaylistViewModel.ALL_GROUPS
                            SettingsSelectionRow(
                                label = com.aeriotv.android.feature.livetv.groupDisplayName(token),
                                // Nothing stored reads as All Channels, which is
                                // where a first launch lands.
                                selected = defaultGroupToken == token ||
                                    (isAll && defaultGroupToken.isBlank()),
                                onClick = { viewModel.setDefaultGroupToken(token) },
                            )
                        }
                    }
                }

                item("group-selection") {
                    if (isTv) {
                        // Moved here from Remote Control (Settings phase 1). Same
                        // row, same dialog, same persisted key.
                        SettingsSection(
                            header = stringResource(R.string.settings_livetv_group_selection),
                            footer = stringResource(R.string.settings_livetv_group_selection_footer_tv),
                        ) {
                            GroupSelectionRow(
                                slotName = stringResource(R.string.settings_livetv_group_selection),
                                valueName = stringResource(if (guideGroupSelector == "sidebar") R.string.settings_livetv_sidebar_menu else R.string.settings_livetv_top_group_pills),
                                onClick = { editingGroupSelector = true },
                            )
                        }
                    } else {
                        SettingsSection(
                            header = stringResource(R.string.settings_livetv_group_selection),
                            footer = stringResource(R.string.settings_livetv_group_selection_footer_phone),
                        ) {
                            SettingsSelectionRow(
                                label = stringResource(R.string.settings_livetv_drawer),
                                selected = phoneGroupSelector != "pills",
                                onClick = { viewModel.setPhoneGroupSelector("sidebar") },
                            )
                            SettingsSelectionRow(
                                label = stringResource(R.string.settings_livetv_pills),
                                selected = phoneGroupSelector == "pills",
                                onClick = { viewModel.setPhoneGroupSelector("pills") },
                            )
                        }
                    }
                }

                // MARK: Badges
                item("badges") {
                    SettingsSection(
                        header = stringResource(R.string.settings_livetv_badges_header),
                        footer = stringResource(if (isTv) R.string.settings_livetv_badges_footer_tv else R.string.settings_livetv_badges_footer_mobile),
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_livetv_show_badges),
                            subtitle = stringResource(R.string.settings_livetv_show_badges_subtitle),
                            checked = showEpgBadges,
                            onCheckedChange = { viewModel.setShowEpgBadges(isTv, it) },
                        )
                        if (showEpgBadges) {
                            for ((badge, badgeLabel) in listOf(
                                "NEW" to R.string.settings_livetv_badge_new,
                                "REPEAT" to R.string.settings_livetv_badge_repeat,
                                "LIVE" to R.string.settings_livetv_badge_live,
                                "PREMIERE" to R.string.settings_livetv_badge_premiere,
                                "FINALE" to R.string.settings_livetv_badge_finale,
                            )) {
                                SettingsToggleRow(
                                    title = stringResource(badgeLabel),
                                    checked = badge !in hiddenEpgBadges,
                                    onCheckedChange = { on ->
                                        viewModel.setBadgeHidden(badge, hidden = !on)
                                    },
                                )
                            }
                        }
                    }
                }

                // MARK: Display Scale
                settingsCard(
                    header = context.getString(R.string.settings_display_scale_header),
                    footer = if (listViewShown) {
                        context.getString(R.string.settings_livetv_scale_footer_list)
                    } else {
                        context.getString(R.string.settings_livetv_scale_footer_guide)
                    },
                ) {
                    ScaleSliderRow(
                        label = stringResource(if (listViewShown) R.string.settings_livetv_scale_list_label else R.string.settings_livetv_title),
                        value = scaleLiveTV,
                        onValueChange = viewModel::setDisplayScaleLiveTV,
                    )
                }

                // MARK: Colors
                settingsCard(
                    header = context.getString(R.string.settings_livetv_colors_header),
                    footer = context.getString(R.string.settings_livetv_colors_footer),
                ) {
                    ToggleRow(
                        title = stringResource(R.string.settings_livetv_color_by_category),
                        subtitle = stringResource(R.string.settings_livetv_color_by_category_subtitle),
                        checked = palette.masterEnabled,
                        onCheckedChange = viewModel::setCategoryColorsEnabled,
                    )
                    DividerRow()
                    BoxWithConstraints {
                        val dim = if (palette.masterEnabled) 1f else 0.4f
                        if (twoUpInPane(maxWidth)) {
                            Column {
                                ProgramCategory.defaultBuckets.chunked(2)
                                    .forEachIndexed { rowIndex, pair ->
                                        if (rowIndex > 0) DividerRow()
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            pair.forEach { bucket ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .alpha(dim),
                                                ) {
                                                    CategoryPaletteRow(
                                                        bucket = bucket,
                                                        hex = palette.hexFor(bucket),
                                                        enabled = palette.masterEnabled,
                                                        onClick = { pickerTarget = bucket },
                                                    )
                                                }
                                            }
                                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                            }
                        } else {
                            Column {
                                ProgramCategory.defaultBuckets.forEachIndexed { idx, bucket ->
                                    if (idx > 0) DividerRow()
                                    Box(modifier = Modifier.alpha(dim)) {
                                        CategoryPaletteRow(
                                            bucket = bucket,
                                            hex = palette.hexFor(bucket),
                                            enabled = palette.masterEnabled,
                                            onClick = { pickerTarget = bucket },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    DividerRow()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusWash()
                            .clickable(enabled = palette.masterEnabled) { viewModel.resetCategoryPalette() }
                            .alpha(if (palette.masterEnabled) 1f else 0.4f)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_livetv_reset_colors),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFB8C00),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    DividerRow()
                    Box(modifier = Modifier.alpha(if (palette.masterEnabled) 1f else 0.4f)) {
                        AddMoreCategoriesRow(
                            extraOn = ProgramCategory.additionalBuckets.count { palette.isBucketEnabled(it) },
                            customCount = palette.custom.size,
                            enabled = palette.masterEnabled,
                            onClick = onOpenAddMoreCategories,
                        )
                    }
                }
            }
        }
    }

    pickerTarget?.let { bucket ->
        HexPickerDialog(
            bucket = bucket,
            currentHex = palette.hexFor(bucket),
            onDismiss = { pickerTarget = null },
            onSave = { hex ->
                viewModel.setCategoryBucketHex(bucket, hex)
                pickerTarget = null
            },
            onReset = {
                viewModel.setCategoryBucketHex(bucket, null)
                pickerTarget = null
            },
        )
    }

    if (editingGroupSelector) {
        TvActionMenuDialog(
            title = stringResource(R.string.settings_livetv_group_selection),
            actions = listOf(
                TvMenuAction(
                    label = stringResource(R.string.settings_livetv_top_group_pills).let { if (guideGroupSelector != "sidebar") stringResource(R.string.settings_livetv_menu_current, it) else it },
                ) {
                    viewModel.setGuideGroupSelector("pills")
                    editingGroupSelector = false
                },
                TvMenuAction(
                    label = stringResource(R.string.settings_livetv_sidebar_menu).let { if (guideGroupSelector == "sidebar") stringResource(R.string.settings_livetv_menu_current, it) else it },
                ) {
                    viewModel.setGuideGroupSelector("sidebar")
                    editingGroupSelector = false
                },
            ),
            onDismiss = { editingGroupSelector = false },
            guard = menuGuard,
        )
    }
}

/** Label + current value row, as Remote Control drew the Group Selection row
 *  before it moved here. Same shared container, no new chrome. */
@Composable
private fun GroupSelectionRow(
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

/** Default Live TV View choices. Empty string = "Automatic" (form-factor
 *  default: List on compact phones, Guide on tablets / TV). "list" / "guide"
 *  are explicit overrides. Written to the same [defaultLiveTVView] pref the
 *  Live TV screen reads; the in-screen List / Guide button is session-only and
 *  never writes here. */
private val DEFAULT_LIVE_TV_VIEW_OPTIONS = listOf(
    "" to R.string.settings_livetv_view_automatic,
    "list" to R.string.settings_livetv_view_list,
    "guide" to R.string.settings_livetv_view_guide,
)
