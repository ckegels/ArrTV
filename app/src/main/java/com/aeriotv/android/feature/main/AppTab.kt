package com.aeriotv.android.feature.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R

/**
 * Mirrors iOS Aerio/Features/Home/HomeView.swift AppTab enum (lines 2783-2787):
 * liveTV, favorites, dvr, onDemand, settings.
 *
 * Per iOS canon, the tab BAR only renders tabs that have applicable content:
 *  - Live TV: always visible
 *  - Settings: always visible
 *  - Favorites: only when the user has favorited at least one channel
 *    (Phase 5+; not surfaced today since the Favorites store is empty)
 *  - DVR: only when the active source supports server-side recordings
 *    (Dispatcharr + Xtream do, raw M3U does not)
 *  - On Demand: only when the active source serves VOD (Movies / Series)
 *
 * See [com.aeriotv.android.feature.main.MainScaffold.visibleTabs] for the
 * predicate that drives this. The enum carries every possible tab; the
 * scaffold filters down to what's actionable for the current source.
 */
enum class AppTab(
    val id: String,
    /** English name, kept for code that matches on it; show [labelRes] / [displayLabel]. */
    val label: String,
    @StringRes val labelRes: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
) {
    LiveTV(
        id = "livetv",
        label = "Live TV",
        labelRes = R.string.main_tab_live_tv,
        iconSelected = Icons.Filled.LiveTv,
        iconUnselected = Icons.Outlined.LiveTv,
    ),
    Favorites(
        id = "favorites",
        label = "Favorites",
        labelRes = R.string.main_tab_favorites,
        iconSelected = Icons.Filled.Favorite,
        iconUnselected = Icons.Outlined.Favorite,
    ),
    DVR(
        id = "dvr",
        label = "DVR",
        labelRes = R.string.main_tab_dvr,
        // iOS parity (record.circle): the Tv glyph doubled as the
        // Control-a-TV button once the bar minimized (Logan 2026-09-09).
        iconSelected = Icons.Filled.RadioButtonChecked,
        iconUnselected = Icons.Outlined.RadioButtonChecked,
    ),
    OnDemand(
        id = "ondemand",
        label = "On Demand",
        labelRes = R.string.main_tab_on_demand,
        iconSelected = Icons.Filled.OndemandVideo,
        iconUnselected = Icons.Outlined.OndemandVideo,
    ),

    /**
     * Media-center redesign (Apple parity, phone/tablet first, 2026-09-08):
     * On Demand splits into Movies and TV Shows. TV keeps On Demand until its
     * own redesign phase; visibleTabs picks by form factor.
     */
    Movies(
        id = "movies",
        label = "Movies",
        labelRes = R.string.main_tab_movies,
        iconSelected = Icons.Filled.Movie,
        iconUnselected = Icons.Outlined.Movie,
    ),
    TVShows(
        id = "tvshows",
        label = "TV Shows",
        labelRes = R.string.main_tab_tv_shows,
        iconSelected = Icons.Filled.Tv,
        iconUnselected = Icons.Outlined.Tv,
    ),
    Settings(
        id = "settings",
        label = "Settings",
        labelRes = R.string.common_settings,
        iconSelected = Icons.Filled.Settings,
        iconUnselected = Icons.Outlined.Settings,
    ),

    /**
     * Global Search. TV-ONLY (Logan 2026-08-06): the guide's in-header search
     * circles are gone on TV and this is the one search entry there. It is
     * NEVER a pill - visibleTabs never emits it - but it IS a selectable tab
     * state: TvTopTabBar renders it as the floating circle LEFT of Live TV
     * (one Left press for frequent searchers), and MainTabContent hosts its
     * screen like any other tab. Phones keep search in the Live TV app bar,
     * and the Default Tab picker skips it.
     */
    Search(
        id = "search",
        label = "Search",
        labelRes = R.string.common_search,
        iconSelected = Icons.Filled.Search,
        iconUnselected = Icons.Outlined.Search,
    ),
}

/** The tab's name in the app's language. */
@Composable
fun AppTab.displayLabel(): String = stringResource(labelRes)
