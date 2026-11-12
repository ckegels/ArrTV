package com.aeriotv.android.feature.movies.tv

import com.aeriotv.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeriotv.android.core.tv.TvMenuAction
import com.aeriotv.android.feature.movies.MediaHeroPage
import com.aeriotv.android.feature.movies.MediaItem
import com.aeriotv.android.feature.movies.MediaKind
import com.aeriotv.android.feature.movies.MediaSortOrder
import com.aeriotv.android.feature.movies.formatRating
import com.aeriotv.android.ui.tv.TvPill

/** Poster column width on the 960 dp Streamer canvas: (960 - 70 - 48 - 6 * 12) / 7. */
internal val TV_POSTER_WIDTH = 110.dp

/**
 * Movies / TV Shows on Android TV: the shared [TvMediaPage] fed by the same
 * data MediaTabContent builds for the phone (Continue Watching hero pages,
 * Watchlist, the sorted library, search hits and provider pills). tvOS
 * MoviesView legacyContent is the reference: hero carousel, Watchlist
 * shelf, library header with search / sort / filter circles, genre pills,
 * seven-column poster grid, alphabet rail.
 */
@Composable
internal fun TvMediaTab(
    kind: MediaKind,
    gridState: LazyGridState,
    heroPages: List<MediaHeroPage>,
    /** Keys of the underlying Continue Watching progress rows (unresolved).
     *  The mid-load hero hold may only keep pages still in this set. */
    progressKeys: Set<String> = emptySet(),
    watchlistPages: List<MediaHeroPage>,
    backdrops: Map<String, String?>,
    /** Resolved poster per item: the cached TMDB art first, the provider's
     *  otherwise. Re-created by the tab on each art-cache version bump. */
    posterUrlFor: (MediaItem) -> String?,
    library: List<MediaItem>,
    gridItems: List<MediaItem>,
    available: Set<Char>,
    /** First grid index for a rail letter (the catalog window's bucket index). */
    railIndexOf: (Char) -> Int,
    /** Grid index of an item key without walking [gridItems]; null for a plain list. */
    gridIndexOfKey: ((Any) -> Int)? = null,
    isSearching: Boolean,
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onClearSearch: () -> Unit,
    personMatchName: String?,
    showProviderPills: Boolean,
    providerIds: List<Int>,
    providerNames: Map<Int, String>,
    selectedProviderId: Int?,
    onSelectProvider: (Int?) -> Unit,
    genrePills: List<String>,
    selectedGenre: String?,
    onGenre: (String?) -> Unit,
    sortOrder: MediaSortOrder,
    onSort: (MediaSortOrder) -> Unit,
    onFilter: () -> Unit,
    filterActive: Boolean,
    /** The Filter sheet is open over the page: focus returns to the circle on close. */
    filterOpen: Boolean,
    watchlistKeys: Set<String>,
    onToggleWatchlist: (MediaItem) -> Unit,
    /** Titles the user hid; a hidden title only appears under "Hidden". */
    hiddenKeys: Set<String>,
    /** Long-press Hide / Unhide (Logan 2026-09-14). */
    onToggleHidden: (MediaItem) -> Unit,
    onRemoveWatchlist: (String) -> Unit,
    onRemoveProgress: (String) -> Unit,
    onPlay: (videoId: String, title: String) -> Unit,
    /** "Play from Beginning": start at 0 and KEEP the Continue Watching row. */
    onPlayFromStart: (videoId: String, title: String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    /** Hero label for a SERIES whose target episode is known ("Play S1 E1" /
     *  "Resume S2 E3"); null while the episode list is still loading. */
    seriesPlayLabel: (MediaItem) -> String? = { null },
    /** Play a series hero: resolves the target episode instead of opening
     *  Details (Apple tvSeriesTarget, VODDetailView.swift:731-751). */
    onPlaySeries: (MediaItem) -> Unit = {},
    isLoading: Boolean,
    libraryPending: Boolean,
    isSearchBusy: Boolean,
) {
    val pageId = kind.name
    val context = LocalContext.current
    // Come back at the exact offset AND on the exact focusable that opened
    // the detail (hero button, shelf card or grid cell): arm() freezes both
    // (tvOS never disposes the tab, MoviesView.swift:221, 259-279).
    /** Arm the return state for an action that opens a ROUTE but not an item
     *  detail (the hero Play buttons): the page's focused source is frozen,
     *  so Back lands on the button that was pressed. */
    val armHero: () -> Unit = { TvReturnMemory.arm(pageId, null, gridState) }
    val open: (MediaItem) -> Unit = { item ->
        TvReturnMemory.arm(pageId, item.key, gridState)
        onOpen(item)
    }

    fun heroFor(page: MediaHeroPage, watchlist: Boolean): TvHeroPage {
        val item = page.item
        val videoId = page.key.removePrefix("cw:")
        // A watchlisted SERIES has no movieUuid: play its target episode
        // rather than falling through to Details.
        val play: () -> Unit = if (watchlist) ({
            armHero()
            item?.movieUuid?.let { onPlay(it, page.title) }
                ?: item?.let { if (it.seriesId != null) onPlaySeries(it) else open(it) }
        }) else ({ armHero(); onPlay(videoId, page.title) })
        val details: () -> Unit = { item?.let(open) }
        val meta = buildList {
            page.year?.let { add(it.toString()) }
            if ((page.season ?: 0) > 0) add(context.getString(R.string.media_season_episode_short, page.season ?: 0, page.episode ?: 0))
            // tvOS: VODSeries.durationText is always empty, so a TV Shows
            // hero never carries a runtime (VODModels.swift:1288).
            if (kind != MediaKind.TVShows) {
                page.durationSecs?.takeIf { it > 0 }?.let { s -> add(if (s >= 3600) context.getString(R.string.media_duration_h_min, s / 3600, (s % 3600) / 60) else context.getString(R.string.media_duration_min, s / 60)) }
            }
            page.genre?.split(',', '/', '|')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        val seriesLabel = item?.takeIf { it.movieUuid == null && it.seriesId != null }?.let(seriesPlayLabel)
        val buttons = buildList {
            // A series hero always carries the season / episode of the
            // target ("Resume S1 E1"), on the Continue Watching hero as well
            // as the watchlist one; bare "Resume" hid which episode would
            // play (Logan 2026-09-11). Movies keep Play / Resume.
            val primaryLabel = when {
                seriesLabel != null -> seriesLabel
                page.hasProgress -> context.getString(R.string.media_resume)
                else -> context.getString(R.string.common_play)
            }
            add(TvHeroButton(primaryLabel, Icons.Filled.PlayArrow, primary = true, id = "Primary", onClick = play))
            // tvOS plays at 0 and LEAVES WatchProgress intact
            // (MoviesView.swift:1507-1511): the row must survive.
            if (page.hasProgress) {
                add(TvHeroButton(context.getString(R.string.media_play_from_beginning), Icons.Filled.Replay, id = "FromStart") { armHero(); onPlayFromStart(videoId, page.title) })
            }
            add(TvHeroButton(context.getString(R.string.media_details), Icons.Outlined.Info, id = "Details", onClick = details))
        }
        val longPress = buildList {
            if (item != null) add(TvMenuAction(context.getString(if (item.key in watchlistKeys) R.string.media_remove_from_watchlist else R.string.media_add_to_watchlist)) { onToggleWatchlist(item) })
            if (item != null) add(TvMenuAction(context.getString(if (item.key in hiddenKeys) R.string.media_unhide else R.string.media_hide)) { onToggleHidden(item) })
            if (!watchlist) add(TvMenuAction(context.getString(R.string.media_remove_from_continue_watching), destructive = true) { onRemoveProgress(videoId) })
            else if (item != null) add(TvMenuAction(context.getString(R.string.media_remove_from_watchlist), destructive = true) { onRemoveWatchlist(item.key) })
        }
        return TvHeroPage(
            key = page.key, title = page.title, artUrl = backdrops[page.key] ?: page.artUrl,
            buttons = buttons, meta = meta, rating = formatRating(page.rating).ifEmpty { null },
            plot = page.plot, longPressActions = longPress,
        )
    }

    // The hero exists ONLY for Continue Watching (Logan 2026-09-14): with
    // nothing resumable there is no fallback hero and no reserved space, the
    // page starts at the first shelf. The mid-sweep hold stays: while the
    // library is still loading, KEEP the pages already on screen instead of
    // swapping the carousel while the progress rows are still resolving.
    val heldHero = remember { mutableStateOf(emptyList<TvHeroPage>()) }
    val tvHero = remember(heroPages, backdrops, watchlistKeys, hiddenKeys, isLoading, progressKeys) {
        // Drop held pages whose progress row is gone: "Remove from Continue
        // Watching" on the LAST item empties heroPages, and without this the
        // hold kept the removed hero on screen whenever a library sweep
        // happened to be running (Logan 2026-09-14).
        val stillValid = heldHero.value.filter { it.key in progressKeys }
        heldHero.value = if (!isLoading || heroPages.isNotEmpty() || stillValid.isEmpty()) {
            heroPages.map { heroFor(it, watchlist = false) }
        } else stillValid
        heldHero.value
    }
    val watchlistShelf = TvShelf(
        title = stringResource(R.string.media_watchlist),
        items = watchlistPages,
        key = { it.key },
        cardWidth = TV_POSTER_WIDTH,
    ) { page, modifier ->
        val item = page.item
        TvPosterCard(
            title = page.title, year = page.year,
            posterUrl = item?.let(posterUrlFor) ?: page.artUrl,
            rating = formatRating(page.rating),
            onClick = { item?.let(open) }, modifier = modifier,
            longPressActions = listOfNotNull(
                item?.let { TvMenuAction(context.getString(R.string.media_details)) { open(it) } },
                item?.let { TvMenuAction(context.getString(if (it.key in hiddenKeys) R.string.media_unhide else R.string.media_hide)) { onToggleHidden(it) } },
                item?.let { TvMenuAction(context.getString(R.string.media_remove_from_watchlist), destructive = true) { onRemoveWatchlist(it.key) } },
            ),
        )
    }
    val sortActions = MediaSortOrder.entries.map { order ->
        val label = stringResource(order.labelRes)
        TvMenuAction(if (order == sortOrder) label + "  ✓" else label) { onSort(order) }
    }
    val extras = buildList<@Composable () -> Unit> {
        if (personMatchName != null) add {
            Text(
                stringResource(R.string.media_includes_titles_with, personMatchName), fontSize = 10.sp.subtext(),
                color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (showProviderPills) add {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp), modifier = Modifier.fillMaxWidth()) {
                item(key = "all") { TvPill(stringResource(R.string.media_all_providers), selectedProviderId == null, onClick = { onSelectProvider(null) }) }
                items(providerIds.size, key = { providerIds[it] }) { i ->
                    val pid = providerIds[i]
                    TvPill(providerNames[pid] ?: stringResource(R.string.media_provider_n, pid), selectedProviderId == pid, onClick = { onSelectProvider(if (selectedProviderId == pid) null else pid) })
                }
            }
        }
    }

    TvMediaPage(
        gridState = gridState,
        heroPages = tvHero,
        // The hero is always the Continue Watching carousel.
        heroSectionTitle = if (tvHero.isNotEmpty()) stringResource(R.string.media_continue_watching) else null,
        shelves = listOf(watchlistShelf),
        headerTitle = if (isSearching) stringResource(R.string.media_results) else stringResource(kind.libraryTitleRes),
        headerCount = gridItems.size,
        columns = 7,
        gridRowSpacing = 24.dp,
        sortActions = sortActions,
        onFilter = onFilter,
        filterActive = filterActive,
        filterOpen = filterOpen,
        searchEnabled = true,
        searchActive = searchActive,
        query = query,
        onQueryChange = onQueryChange,
        onSearchToggle = onSearchToggle,
        onClearSearch = onClearSearch,
        searchPlaceholder = if (kind == MediaKind.Movies) stringResource(R.string.media_search_movies) else stringResource(R.string.media_search_tv_shows),
        isSearching = isSearching,
        searchExtras = extras,
        pills = genrePills,
        selectedPill = selectedGenre,
        onPill = onGenre,
        gridItems = gridItems,
        gridKey = { it.key },
        gridLabel = { it.title },
        cell = { item, scope ->
            TvPosterCard(
                title = item.title, year = item.year, posterUrl = posterUrlFor(item), rating = formatRating(item.rating),
                onClick = { open(item) }, modifier = scope.modifier,
                longPressActions = listOf(
                    TvMenuAction(context.getString(R.string.media_details)) { open(item) },
                    TvMenuAction(context.getString(if (item.key in watchlistKeys) R.string.media_remove_from_watchlist else R.string.media_add_to_watchlist)) { onToggleWatchlist(item) },
                    TvMenuAction(context.getString(if (item.key in hiddenKeys) R.string.media_unhide else R.string.media_hide)) { onToggleHidden(item) },
                ),
            )
        },
        emptyContent = {
            if (isLoading || (!isSearching && libraryPending) || (isSearching && isSearchBusy)) CircularProgressIndicator()
            else Text(if (isSearching) stringResource(R.string.media_no_results) else stringResource(kind.emptyTitleRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        isLoading = isLoading,
        railLetters = available,
        railIndexOf = railIndexOf,
        indexOfKey = gridIndexOfKey,
        // Snapshot at composition start: the click that ARMS the key must not
        // restart the restore while the page is still up (it would clear the
        // key before the detail even opened).
        returnKey = remember { TvReturnMemory.pending[pageId] },
        returnOffset = remember { TvReturnMemory.pendingOffset[pageId] },
        returnSource = remember { TvReturnMemory.pendingSource[pageId] },
        onReturnHandled = { TvReturnMemory.clear(pageId) },
        pageId = pageId,
        // TMDB's terms ask for the logo and wording wherever their data and
        // images are shown; the posters in this grid are TMDB art.
        footer = { com.aeriotv.android.ui.TmdbAttribution(long = true, isTv = true) },
    )
}
