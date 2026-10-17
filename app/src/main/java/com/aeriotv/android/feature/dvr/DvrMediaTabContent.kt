package com.aeriotv.android.feature.dvr

import com.aeriotv.android.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.aeriotv.android.ui.scale.subtext
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import com.aeriotv.android.ui.scale.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.aeriotv.android.ui.scale.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aeriotv.android.core.data.ProgramInfoTarget
import com.aeriotv.android.core.data.RecordingFacts
import com.aeriotv.android.feature.livetv.ProgramInfoSheet
import com.aeriotv.android.feature.livetv.rememberLiveTvFormFactor
import com.aeriotv.android.feature.movies.AlphabetRail
import com.aeriotv.android.feature.movies.PhoneCardDeck
import com.aeriotv.android.feature.movies.PageSectionTitle
import com.aeriotv.android.feature.movies.PageRow
import com.aeriotv.android.feature.movies.PageDeck
import com.aeriotv.android.feature.movies.MediaPageScaffold
import com.aeriotv.android.feature.movies.nearestAvailable
import com.aeriotv.android.feature.movies.railLetters
import com.aeriotv.android.feature.movies.stripQualityPrefix
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import com.aeriotv.android.feature.settings.SettingsViewModel
import com.aeriotv.android.feature.watchprogress.WatchProgressViewModel
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date

private typealias Rec = DvrViewModel.Recording

/** DVR library sort (Apple parity: DVRView.SortOrder). Title A to Z default. */
enum class DvrSortOrder(val wire: String, @StringRes val labelRes: Int) {
    Newest("newest", R.string.media_sort_newest_first), Oldest("oldest", R.string.media_sort_oldest_first),
    Title("title", R.string.media_sort_title_a_z), Channel("channel", R.string.media_sort_channel);
    companion object { fun fromWire(s: String?) = entries.firstOrNull { it.wire == s } ?: Title }
}

/** Recording kind for the genre pills (Apple parity: DVRContentKind). */
enum class DvrKind(@StringRes val labelRes: Int) {
    Movies(R.string.media_kind_movies), TVShows(R.string.media_kind_tv_shows), Sports(R.string.media_kind_sports),
    News(R.string.media_kind_news), Kids(R.string.media_kind_kids), Other(R.string.media_kind_other),
}

// Port of the Apple DVRClassifier (DVRArtResolver.swift): category words
// first, newscast title shapes, real (non date-coded) episode identity, then
// title and subtitle hints, and finally airing length when the EPG says
// nothing. Memoised per row because the tab classifies every row per render.
private val sportsWords = listOf(
    "sport", "football", "soccer", "basketball", "baseball", "hockey", "nfl", "nba",
    "mlb", "nhl", "ncaa", "golf", "tennis", "racing", "nascar", "formula 1", "f1",
    "ufc", "mma", "boxing", "wrestling", "wwe", "olympic", "cricket", "rugby",
    "motogp", "lacrosse", "volleyball", "playoffs", "championship", "premier league",
    "la liga", "bundesliga", "serie a", "champions league",
)
private val movieWords = listOf("movie", "film", "cinema")
private val newsWords = listOf("news", "newscast", "current affairs", "weather", "politics", "public affairs")
private val newsTitleWords = listOf(
    "news", "newshour", "nightly", "60 minutes", "dateline", "meet the press",
    "face the nation", "this week", "good morning america", "today show",
)
private val kidsWords = listOf("kids", "children", "preschool", "family", "cartoon")
private val seriesWords = listOf(
    "series", "episode", "sitcom", "drama", "comedy", "reality", "talk", "animation",
    "documentary", "game show", "soap", "crime", "sci-fi", "science fiction", "fantasy",
    "mystery", "variety",
)
private val newscastTimeTitle = Regex("""\bat \d{1,2}(:\d{2})?\s*(am|pm)?$""")
private val classifyMemo = java.util.concurrent.ConcurrentHashMap<String, DvrKind>()

private fun classify(rec: Rec): DvrKind {
    val key = rec.id + "|" + rec.category + "|" + rec.title + "|" + rec.subTitle + "|" + rec.description.hashCode() +
        "|" + rec.season + "|" + rec.episode + "|" + rec.startMillis + "|" + rec.endMillis
    classifyMemo[key]?.let { return it }
    val category = rec.category.lowercase()
    val title = rec.title.lowercase()
    val sub = rec.subTitle.orEmpty().lowercase()
    val desc = rec.description.lowercase()
    // "S2026 E905" is a date code, not an episode identity.
    val realEpisode = (rec.season ?: 0) in 1..1899 && (rec.episode ?: 0) > 0
    val kind = when {
        sportsWords.any { category.contains(it) } -> DvrKind.Sports
        newsWords.any { category.contains(it) } -> DvrKind.News
        kidsWords.any { category.contains(it) } -> DvrKind.Kids
        newsTitleWords.any { title.contains(it) } -> DvrKind.News
        newscastTimeTitle.containsMatchIn(title) -> DvrKind.News
        desc.contains("news coverage") || desc.contains("local news") || desc.contains("regional news") ||
            desc.contains("headlines") || desc.startsWith("news") -> DvrKind.News
        realEpisode -> DvrKind.TVShows
        movieWords.any { category.contains(it) } -> DvrKind.Movies
        seriesWords.any { category.contains(it) } -> DvrKind.TVShows
        sportsWords.any { title.contains(it) } -> DvrKind.Sports
        sub.isNotEmpty() -> DvrKind.TVShows
        else -> {
            val minutes = (rec.endMillis - rec.startMillis) / 60_000
            when {
                minutes >= 80 -> DvrKind.Movies
                minutes >= 15 -> DvrKind.TVShows
                else -> DvrKind.Other
            }
        }
    }
    if (classifyMemo.size > 2000) classifyMemo.clear()
    classifyMemo[key] = kind
    return kind
}

private fun bucket(title: String): Char {
    val t = Normalizer.normalize(stripQualityPrefix(title), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    val c = t.firstOrNull { !it.isWhitespace() }?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

private fun Rec.progressKey(): String = when (source) {
    DvrViewModel.Source.Server -> "dvr-" + id.removePrefix("server-")
    else -> playbackUrl ?: id
}

private fun Rec.serverId(): Int = if (source == DvrViewModel.Source.Server) id.removePrefix("server-").toIntOrNull() ?: -1 else -1

/**
 * DVR tab, phone and tablet (media-center redesign, Apple parity with the
 * iPhone DVRView). Top to bottom: Recording Now / Continue Watching deck,
 * Scheduled shelf, Recent Recordings deck, All Recordings header with the
 * sort circle, kind pills, three-column poster grid, alphabet rail. Tap a
 * poster to play, long press for the menu (Program Info first). TV keeps
 * DvrTabContent.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DvrMediaTabContent(
    onPlayRecording: (String, String, Int) -> Unit,
    onWatchLive: (String, String, Boolean, Long, Int?) -> Unit,
    onWatchFromBeginning: (String, String, Boolean, Long, Int?, Boolean) -> Unit,
    viewModel: DvrViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
    playlistVm: PlaylistViewModel = hiltViewModel(),
    watchVm: WatchProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlistState by playlistVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compact = rememberLiveTvFormFactor().widthClass == WindowWidthSizeClass.Compact
    val sortWire by settingsVm.dvrSortOrder.collectAsStateWithLifecycle(initialValue = "title")
    val sortOrder = DvrSortOrder.fromWire(sortWire)
    val recent by watchVm.observeRecent(80).collectAsStateWithLifecycle(initialValue = emptyList())
    // A clock read straight out of the composable made "now" a NEW value on
    // every recomposition, so every remember(recordings, now) below missed and
    // the whole library was re-classified, re-filtered and re-sorted each time
    // the tab redrew - the reload Logan saw on every DVR open (2026-09-11).
    // A 30 s tick is finer than any status boundary the page shows.
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000L) }
    }

    // One map instead of a linear scan of every channel per card, per render.
    val channelsById = remember(playlistState.channels) {
        playlistState.channels.asSequence().filter { it.dispatcharrChannelId != null }
            .associateBy { it.dispatcharrChannelId!! }
    }
    val channelName: (Rec) -> String = { rec -> channelsById[rec.dispatcharrChannelId]?.name ?: "" }
    val channelLogo: (Rec) -> String? = { rec ->
        channelsById[rec.dispatcharrChannelId]?.tvgLogo?.takeIf { it.isNotBlank() }
    }
    val recentByVideoId = remember(recent) { recent.associateBy { it.videoId } }
    val progressOf: (Rec) -> Float = { rec ->
        val row = recentByVideoId[rec.progressKey()]
        val total = (rec.endMillis - rec.startMillis).toDouble()
        if (row == null || row.positionMs <= 0L) 0f
        else (row.positionMs / (if (row.durationMs > 0) row.durationMs.toDouble() else total.coerceAtLeast(1.0))).toFloat().coerceIn(0f, 1f)
    }

    val recordings = state.recordings
    val recordingNow = remember(recordings, now) { recordings.filter { it.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording }.sortedByDescending { it.startMillis } }
    val scheduled = remember(recordings, now) { recordings.filter { it.effectiveStatus(now) == DvrViewModel.Recording.Status.Scheduled }.sortedBy { it.startMillis } }
    val completed = remember(recordings, now) { recordings.filter { val s = it.effectiveStatus(now); s == DvrViewModel.Recording.Status.Completed || s == DvrViewModel.Recording.Status.Stopped } }
    val continueWatching = remember(recordingNow, completed, recent) {
        recordingNow + completed.filter { val p = progressOf(it); p > 0f && p < 0.97f }.sortedByDescending { it.startMillis }
    }
    val recentRecordings = remember(completed) { completed.sortedByDescending { it.startMillis }.take(20) }
    val library = remember(recordingNow, completed) { recordingNow + completed }
    var selectedKind by remember { mutableStateOf<DvrKind?>(null) }
    val kindOf = remember(library) { library.associate { it.id to classify(it) } }
    val kindsPresent = remember(kindOf) { kindOf.values.toSet() }
    // Search and Filter (Logan 2026-09-10, like Movies and TV Shows):
    // search matches title, subtitle and description; Filter hides channels.
    val hiddenChannels by settingsVm.hiddenDvrChannels.collectAsStateWithLifecycle(initialValue = emptySet())
    val channelNames = remember(library, channelsById) { library.map(channelName).filter { it.isNotBlank() }.distinct().sorted() }
    // Not rememberSaveable: see MediaTabContent. A restored open search
    // re-ran the field's focus effect and summoned the keyboard on the way
    // back from the fullscreen player.
    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    com.aeriotv.android.ui.search.CloseSearchOnLeave(searchActive) {
        searchActive = false
        query = ""
    }
    val isSearching = query.isNotBlank()
    var showManageChannels by remember { mutableStateOf(false) }
    val filteredLibrary = remember(library, kindOf, selectedKind, sortOrder, hiddenChannels, query, channelsById) {
        val q = query.trim()
        library.filter { rec ->
            (selectedKind == null || kindOf[rec.id] == selectedKind) &&
                channelName(rec) !in hiddenChannels &&
                (q.isEmpty() || rec.title.contains(q, ignoreCase = true) ||
                    (rec.subTitle?.contains(q, ignoreCase = true) == true) ||
                    rec.description.contains(q, ignoreCase = true))
        }.let { list ->
            when (sortOrder) {
                DvrSortOrder.Newest -> list.sortedByDescending { it.startMillis }
                DvrSortOrder.Oldest -> list.sortedBy { it.startMillis }
                DvrSortOrder.Title -> list.sortedWith(compareBy({ stripQualityPrefix(it.title).lowercase() }, { it.startMillis }))
                DvrSortOrder.Channel -> list.sortedWith(compareBy({ channelName(it).lowercase() }, { it.startMillis }))
            }
        }
    }
    val available = remember(filteredLibrary) { filteredLibrary.map { bucket(it.title) }.toSet() }

    var infoTarget by remember { mutableStateOf<ProgramInfoTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<Rec?>(null) }
    var pendingEdit by remember { mutableStateOf<Rec?>(null) }
    // Capability.CanManageDvr via LocalDvrAccess: a Dispatcharr account whose
    // custom_properties.dvr_access is "view" lists and plays server
    // recordings but must not see stop / edit / cancel / comskip /
    // Delete from Server. Local rows are this device's own files and are
    // never gated (Logan 2026-09-15).
    val canManageDvr = com.aeriotv.android.ui.LocalDvrAccess.current == "manage"
    var showSort by remember { mutableStateOf(false) }
    val gridState = if (com.aeriotv.android.ui.settings.rememberIsTvDevice()) com.aeriotv.android.ui.tv.rememberTvMediaGridState() else rememberLazyGridState()
    val bottomInset = LocalTabBarBottomInset.current

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun play(rec: Rec) {
        val url = rec.playbackUrl
        if (rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording) {
            rec.inProgressUrl?.let { onWatchFromBeginning(it, rec.title, rec.isDvr, rec.endMillis, rec.dispatcharrChannelId, true) }
                ?: toast(context.getString(R.string.media_dvr_not_playable_yet))
        } else if (!url.isNullOrBlank()) onPlayRecording(url, rec.title, rec.serverId())
        else toast(context.getString(R.string.media_dvr_no_playable_file))
    }
    fun playFromStart(rec: Rec) {
        if (rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording) {
            rec.inProgressUrl?.let { onWatchFromBeginning(it, rec.title, rec.isDvr, rec.endMillis, rec.dispatcharrChannelId, false) }
        } else {
            watchVm.delete(rec.progressKey()); play(rec)
        }
    }
    fun jumpToLive(rec: Rec) { rec.inProgressUrl?.let { onWatchLive(it, rec.title, rec.isDvr, rec.endMillis, rec.dispatcharrChannelId) } }
    fun showInfo(rec: Rec) {
        val statusLabel = when (rec.effectiveStatus(now)) {
            DvrViewModel.Recording.Status.Scheduled -> context.getString(R.string.media_status_scheduled); DvrViewModel.Recording.Status.Recording -> context.getString(R.string.media_status_recording); DvrViewModel.Recording.Status.Completed -> context.getString(R.string.media_status_completed)
            DvrViewModel.Recording.Status.Stopped -> context.getString(R.string.media_status_stopped); DvrViewModel.Recording.Status.Failed -> context.getString(R.string.media_status_failed); DvrViewModel.Recording.Status.Unknown -> context.getString(R.string.common_unknown)
        }
        val ext = (rec.fileName ?: rec.playbackUrl ?: "").substringAfterLast('.', "").takeIf { it.length in 2..5 && !it.contains('/') }
        infoTarget = ProgramInfoTarget(
            channelName = channelName(rec), title = rec.title.ifBlank { context.getString(R.string.media_recording) },
            // tvOS: "Airs" is the guide programme's airing, the recording
            // window sits in the facts row below.
            startMillis = rec.programStartMillis ?: rec.startMillis, endMillis = rec.programEndMillis ?: rec.endMillis,
            description = rec.description, category = rec.category,
            channelDispatcharrId = rec.dispatcharrChannelId, dispatcharrProgramId = rec.programId,
            subTitle = rec.subTitle, season = rec.season, episode = rec.episode, isNew = rec.isNew,
            recording = RecordingFacts(
                recordedOnMillis = rec.startMillis, windowStartMillis = rec.startMillis, windowEndMillis = rec.endMillis,
                fileSizeBytes = rec.fileSizeBytes,
                format = if (ext.equals("m3u8", true)) "HLS" else ext,
                location = if (rec.source == DvrViewModel.Source.Local) context.getString(R.string.media_this_device) else (playlistState.playlist?.name?.takeIf { it.isNotBlank() } ?: "Dispatcharr"),
                status = statusLabel,
                videoCodec = rec.videoCodec, resolution = rec.resolution, frameRate = rec.frameRate,
                videoBitrateKbps = rec.videoBitrateKbps, audioCodec = rec.audioCodec, audioChannels = rec.audioChannels,
            ),
        )
    }

    // One action list per row status; the phone renders it as DropdownMenu
    // items, the TV as the centered TvActionMenuDialog.
    fun menuActions(rec: Rec): List<com.aeriotv.android.core.tv.TvMenuAction> {
        val s = rec.effectiveStatus(now)
        val isServer = rec.source == DvrViewModel.Source.Server
        return buildList {
            add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_program_info)) { showInfo(rec) })
            if (s == DvrViewModel.Recording.Status.Completed || s == DvrViewModel.Recording.Status.Stopped) {
                add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.common_play)) { play(rec) })
                if (isServer) {
                    add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_watch_from_beginning)) { playFromStart(rec) })
                    // Save to Device is a read (GET the file), never gated.
                    add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_save_to_device)) { scope.launch { viewModel.saveToDevice(rec).onFailure { toast(context.getString(R.string.media_save_failed, it.message)) }.onSuccess { toast(context.getString(R.string.media_saving_to_device)) } } })
                    if (canManageDvr) {
                        add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_remove_commercials)) { scope.launch { viewModel.applyComskip(rec).onFailure { toast(context.getString(R.string.media_comskip_failed, it.message)) }.onSuccess { toast(context.getString(R.string.media_comskip_started)) } } })
                    }
                }
            }
            if (s == DvrViewModel.Recording.Status.Recording) {
                if (rec.inProgressUrl != null) {
                    add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_start_at_live)) { jumpToLive(rec) })
                    add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_watch_from_beginning)) { playFromStart(rec) })
                }
                if (!isServer || canManageDvr) {
                    add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_stop_recording)) { scope.launch { viewModel.stopRecording(rec).onFailure { toast(context.getString(R.string.media_stop_failed, it.message)) } } })
                }
            }
            if (s == DvrViewModel.Recording.Status.Scheduled) {
                if (isServer && canManageDvr) add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_edit_recording)) { pendingEdit = rec })
                if (!isServer || canManageDvr) {
                    add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(R.string.media_cancel_recording), destructive = true) { pendingDelete = rec })
                }
            } else if (!isServer || canManageDvr) {
                // A local row's bytes are on this device: "Delete from Device"
                // is always offered. The server copy stays behind dvr_access.
                add(com.aeriotv.android.core.tv.TvMenuAction(context.getString(if (isServer) R.string.media_delete_from_server else R.string.media_delete_from_device), destructive = true) { pendingDelete = rec })
            }
        }
    }

    @Composable
    fun menuItems(rec: Rec, close: () -> Unit) {
        menuActions(rec).forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label, color = if (action.destructive) MaterialTheme.colorScheme.error else Color.Unspecified) },
                onClick = { close(); action.onClick() },
            )
        }
    }

    if (recordings.isEmpty() && !state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.media_no_recordings), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.media_no_recordings_hint), fontSize = 13.sp.subtext(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        // Same page as Movies and TV Shows (Logan 2026-09-09): the only
        // difference is the second deck's title, Recently Recorded.
        val heroCard: @Composable (Rec) -> Unit = { rec ->
            DvrHeroCard(rec, channelName(rec), channelLogo(rec), progressOf(rec), now,
                onPrimary = { if (rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording) playFromStart(rec) else play(rec) },
                onSecondary = { if (rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording) jumpToLive(rec) else playFromStart(rec) },
                onStop = { scope.launch { viewModel.stopRecording(rec) } },
                onInfo = { showInfo(rec) },
                canManage = canManageDvr || rec.source != DvrViewModel.Source.Server,
                menu = { close -> menuItems(rec, close) })
        }
        val recentCard: @Composable (Rec) -> Unit = { rec ->
            DvrHeroCard(rec, channelName(rec), channelLogo(rec), progressOf(rec), now,
                onPrimary = { play(rec) }, onSecondary = { playFromStart(rec) },
                onStop = {}, onInfo = { showInfo(rec) },
                menu = { close -> menuItems(rec, close) })
        }
        val kindPills = DvrKind.entries.filter { it in kindsPresent }
        if (rememberLiveTvFormFactor().isTv) {
            TvDvrPage(
                gridState = gridState, now = now,
                continueWatching = continueWatching, recordingNow = recordingNow, scheduled = scheduled,
                recentRecordings = recentRecordings, filteredLibrary = filteredLibrary, available = available,
                channelName = channelName, channelLogo = channelLogo, progressOf = progressOf,
                kindPills = kindPills, selectedKind = selectedKind, onKind = { selectedKind = it },
                sortOrder = sortOrder, onSort = { settingsVm.setDvrSortOrder(it.wire) },
                searchActive = searchActive, query = query, isSearching = isSearching,
                onQueryChange = { query = it },
                onSearchToggle = { searchActive = !searchActive; if (!searchActive) query = "" },
                // tvOS clearSearch() clears the text AND closes the field
                // (DVRView.swift:161-164).
                onClearSearch = { query = ""; searchActive = false },
                onFilter = { showManageChannels = true }, filterActive = hiddenChannels.isNotEmpty(),
                filterOpen = showManageChannels,
                menuActions = ::menuActions,
                onPlay = ::play, onPlayFromStart = ::playFromStart, onJumpToLive = ::jumpToLive,
                onStop = { rec -> scope.launch { viewModel.stopRecording(rec) } }, onInfo = ::showInfo,
                isLoading = state.isLoading,
                canManageDvr = canManageDvr,
            )
        } else
        MediaPageScaffold(
            gridState = gridState,
            compact = compact,
            decks = listOf(
                PageDeck(
                    if (continueWatching.isNotEmpty() && continueWatching.all { it.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording }) stringResource(R.string.media_recording_now_title) else stringResource(R.string.media_continue_watching),
                    continueWatching, { it.id }, heroCard,
                ),
                PageDeck(stringResource(R.string.media_recently_recorded), if (recentRecordings.size > 1) recentRecordings else emptyList(), { it.id }, recentCard),
            ),
            rows = if (scheduled.isEmpty()) emptyList() else listOf(PageRow("scheduled") {
                Column {
                    PageSectionTitle(stringResource(R.string.media_scheduled))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(scheduled.size, key = { scheduled[it].id }) { i ->
                            val rec = scheduled[i]
                            Box(modifier = Modifier.width(150.dp)) {
                                DvrPosterCard(rec, channelLogo(rec), 0f, now, onClick = { showInfo(rec) }, menu = { close -> menuItems(rec, close) })
                            }
                        }
                    }
                }
            }),
            headerTitle = if (isSearching) stringResource(R.string.media_results) else stringResource(R.string.media_all_recordings),
            headerCount = filteredLibrary.size,
            sortMenu = {
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    DvrSortOrder.entries.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(stringResource(o.labelRes)) },
                            trailingIcon = { if (o == sortOrder) Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = { showSort = false; settingsVm.setDvrSortOrder(o.wire) },
                        )
                    }
                }
            },
            onSort = { showSort = true },
            onFilter = { showManageChannels = true },
            searchEnabled = true,
            searchActive = searchActive,
            query = query,
            onQueryChange = { query = it },
            onSearchToggle = { searchActive = !searchActive; if (!searchActive) query = "" },
            searchPlaceholder = stringResource(R.string.media_search_recordings),
            isSearching = isSearching,
            pills = if (kindPills.size > 1) kindPills.map { stringResource(it.labelRes) } else emptyList(),
            selectedPill = selectedKind?.let { stringResource(it.labelRes) },
            onPill = { label -> selectedKind = kindPills.firstOrNull { context.getString(it.labelRes) == label } },
            gridItems = filteredLibrary,
            gridKey = { it.id },
            cell = { rec -> DvrPosterCard(rec, channelLogo(rec), progressOf(rec), now, onClick = { play(rec) }, menu = { close -> menuItems(rec, close) }) },
            emptyContent = {
                if (state.isLoading) CircularProgressIndicator()
                else Text(stringResource(R.string.media_no_recordings), color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            railLetters = available,
            railIndexOf = { letter -> filteredLibrary.indexOfFirst { bucket(it.title) == letter } },
            isRefreshing = state.isLoading && recordings.isNotEmpty(),
            onRefresh = { viewModel.refresh() },
        )
    }

    if (showManageChannels) {
        if (rememberLiveTvFormFactor().isTv) {
            com.aeriotv.android.ui.tv.TvFilterPage(
                groups = channelNames,
                hiddenGroups = hiddenChannels,
                onChange = { settingsVm.setHiddenDvrChannels(it) },
                onDismiss = { showManageChannels = false },
            )
        } else com.aeriotv.android.feature.livetv.ManageGroupsSheet(
            allGroups = channelNames,
            hiddenGroups = hiddenChannels,
            onSave = { settingsVm.setHiddenDvrChannels(it) },
            onDismiss = { showManageChannels = false },
        )
    }
    infoTarget?.let { ProgramInfoSheet(target = it, onDismiss = { infoTarget = null }) }
    pendingEdit?.let { rec ->
        EditRecordingSheet(
            recording = rec,
            onDismiss = { pendingEdit = null },
            onSave = { newStart, newEnd, newTitle, newDescription ->
                val id = rec.serverId()
                pendingEdit = null
                if (id <= 0) { toast(context.getString(R.string.media_invalid_recording_id)); return@EditRecordingSheet }
                scope.launch {
                    viewModel.editServerRecording(recordingId = id, startMillis = newStart, endMillis = newEnd, title = newTitle, description = newDescription)
                        .fold(onSuccess = { toast(context.getString(R.string.media_recording_updated)) }, onFailure = { toast(context.getString(R.string.media_update_failed, it.message ?: it::class.simpleName)) })
                }
            },
        )
    }
    pendingDelete?.let { rec ->
        val scheduledNow = rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Scheduled
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    when {
                        scheduledNow -> stringResource(R.string.media_cancel_recording)
                        rec.source == DvrViewModel.Source.Server -> stringResource(R.string.media_delete_from_server)
                        else -> stringResource(R.string.media_delete_from_device)
                    },
                )
            },
            text = {
                Text(
                    when {
                        scheduledNow -> stringResource(R.string.media_cancel_scheduled_confirm, rec.title)
                        rec.source == DvrViewModel.Source.Server ->
                            stringResource(R.string.media_delete_from_server_confirm, rec.title)
                        else -> stringResource(R.string.media_delete_from_device_confirm, rec.title)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = rec; pendingDelete = null
                    scope.launch { viewModel.deleteRecording(target).onFailure { toast(context.getString(R.string.media_delete_failed, it.message)) } }
                }) { Text(if (scheduledNow) stringResource(R.string.media_cancel_recording) else stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.media_keep)) } },
        )
    }
}

@Composable
private fun metaLine(rec: Rec, now: Long): String {
    val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(rec.startMillis))
    val s = rec.effectiveStatus(now)
    val second = when (s) {
        DvrViewModel.Recording.Status.Recording -> stringResource(R.string.media_recording_short)
        DvrViewModel.Recording.Status.Scheduled -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(rec.startMillis))
        else -> { val m = ((rec.endMillis - rec.startMillis) / 60_000L).toInt(); if (m >= 60) stringResource(R.string.media_duration_h_min, m / 60, m % 60) else stringResource(R.string.media_duration_min, m) }
    }
    val se = if ((rec.season ?: 0) > 0) stringResource(R.string.media_season_episode_short, rec.season ?: 0, rec.episode ?: 0) else null
    return listOfNotNull(day, second, se).joinToString(" · ")
}

/** 2:3 poster (Apple parity: DVRPosterCard): art or channel logo, REC badge, progress bar, two-line title, meta line. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DvrPosterCard(
    rec: Rec, channelLogo: String?, progress: Float, now: Long,
    onClick: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)) {
            when {
                !rec.posterUrl.isNullOrBlank() -> AsyncImage(model = rec.posterUrl, contentDescription = rec.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                !channelLogo.isNullOrBlank() -> AsyncImage(model = channelLogo, contentDescription = rec.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(18.dp))
                else -> Text(rec.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            }
            if (rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFFF4757)))
                    Text("REC", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF4757))
                }
            }
            if (progress > 0f) {
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.45f))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(MaterialTheme.colorScheme.primary))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
        }
        // Centered under the poster (Logan 2026-09-10, all platforms).
        Text(rec.title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            // Two 14sp lines (+2dp): sp-derived so the grid stays aligned at every Text Size.
            modifier = Modifier.fillMaxWidth().height(with(androidx.compose.ui.platform.LocalDensity.current) { 28.sp.toDp() } + 2.dp))
        Text(metaLine(rec, now), fontSize = 10.sp.subtext(), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
    }
}

/** Hero card (Apple parity: DVRHero on iPhone, inDeck). Tap on the card opens Program Info. */
@Composable
fun DvrHeroCard(
    rec: Rec, channelName: String, channelLogo: String?, progress: Float, now: Long,
    onPrimary: () -> Unit, onSecondary: () -> Unit, onStop: () -> Unit, onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    /** Capability.CanManageDvr: a "view" account never sees Stop Recording. */
    canManage: Boolean = true,
    /** The row's menu behind the right-most options circle (Logan 2026-09-10). */
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    val bg = MaterialTheme.colorScheme.background
    var menuOpen by remember { mutableStateOf(false) }
    val recording = rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording
    val canPlay = recording && rec.inProgressUrl != null || !recording && rec.playbackUrl != null
    Box(modifier = modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onInfo)) {
        when {
            !rec.posterUrl.isNullOrBlank() -> AsyncImage(model = rec.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            !channelLogo.isNullOrBlank() -> AsyncImage(model = channelLogo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth(0.28f).fillMaxHeight().align(Alignment.CenterEnd).padding(24.dp))
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.05f), bg.copy(alpha = 0.92f)))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (recording) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF4757)))
                    Text(stringResource(R.string.media_recording_now), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4757))
                }
            }
            Text(rec.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            rec.subTitle?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val meta = listOfNotNull(channelName.takeIf { it.isNotBlank() }, metaLine(rec, now)).joinToString(" · ")
            Text(meta, fontSize = 13.sp.subtext(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (recording) {
                    if (canPlay) {
                        HeroPill(stringResource(R.string.media_watch_from_start), Icons.Filled.PlayArrow, primary = true, onPrimary)
                        HeroRound(Icons.Filled.Sensors, stringResource(R.string.media_jump_to_live), onSecondary)
                    }
                    if (canManage) HeroRound(Icons.Filled.Stop, stringResource(R.string.media_stop_recording), onStop)
                } else {
                    HeroPill(if (progress > 0f) stringResource(R.string.media_resume) else stringResource(R.string.common_play), Icons.Filled.PlayArrow, primary = true, onPrimary)
                    if (progress > 0f) HeroRound(Icons.Filled.Replay, stringResource(R.string.media_play_from_beginning), onSecondary)
                }
                HeroRound(Icons.Outlined.Info, stringResource(R.string.media_details), onInfo)
                if (menu != null) Box {
                    HeroRound(Icons.Filled.MoreHoriz, stringResource(R.string.media_options)) { menuOpen = true }
                    com.aeriotv.android.ui.scale.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        menu { menuOpen = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 40.dp).clip(CircleShape)
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun HeroRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp)) }
}


/**
 * DVR on Android TV: the shared [com.aeriotv.android.feature.movies.tv.TvMediaPage]
 * with tvOS DVRView's content: hero pages for every started-but-unfinished
 * recording (in progress first), Recording Now / Scheduled / Recent
 * Recordings shelves of 16:9 cards, the All Recordings header with Sort,
 * kind pills, a five-column 16:9 grid and the rail once more than 15 rows.
 */
@Composable
private fun TvDvrPage(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    now: Long,
    continueWatching: List<Rec>,
    recordingNow: List<Rec>,
    scheduled: List<Rec>,
    recentRecordings: List<Rec>,
    filteredLibrary: List<Rec>,
    available: Set<Char>,
    channelName: (Rec) -> String,
    channelLogo: (Rec) -> String?,
    progressOf: (Rec) -> Float,
    kindPills: List<DvrKind>,
    selectedKind: DvrKind?,
    onKind: (DvrKind?) -> Unit,
    sortOrder: DvrSortOrder,
    onSort: (DvrSortOrder) -> Unit,
    searchActive: Boolean,
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onClearSearch: () -> Unit,
    onFilter: () -> Unit,
    filterActive: Boolean,
    /** The Filter surface is open over the page: focus returns to the circle on close. */
    filterOpen: Boolean,
    menuActions: (Rec) -> List<com.aeriotv.android.core.tv.TvMenuAction>,
    onPlay: (Rec) -> Unit,
    onPlayFromStart: (Rec) -> Unit,
    onJumpToLive: (Rec) -> Unit,
    onStop: (Rec) -> Unit,
    onInfo: (Rec) -> Unit,
    isLoading: Boolean,
    /** Capability.CanManageDvr: a "view" account never sees Stop Recording. */
    canManageDvr: Boolean = true,
) {
    val red = Color(0xFFFF4757)
    val context = LocalContext.current
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    fun timeRange(rec: Rec): String = context.getString(R.string.media_time_range, timeFmt.format(Date(rec.startMillis)), timeFmt.format(Date(rec.endMillis)))
    fun durationLabel(rec: Rec): String {
        val m = ((rec.endMillis - rec.startMillis) / 60_000L).toInt()
        return if (m >= 60) context.getString(R.string.media_duration_h_min, m / 60, m % 60) else context.getString(R.string.media_duration_min, m)
    }
    fun seLabel(rec: Rec): String? =
        if ((rec.season ?: 0) > 0) context.getString(R.string.media_season_episode_short, rec.season ?: 0, rec.episode ?: 0) else null
    // A hero action opens a ROUTE over MAIN: arm the return state so Back
    // lands back on the button that was pressed (the page records WHICH one).
    val armHero: () -> Unit = { com.aeriotv.android.feature.movies.tv.TvReturnMemory.arm("dvr", null, gridState) }
    fun hero(rec: Rec): com.aeriotv.android.feature.movies.tv.TvHeroPage {
        val recording = rec.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording
        val progress = progressOf(rec)
        val canPlay = recording && rec.inProgressUrl != null || !recording && rec.playbackUrl != null
        val se = seLabel(rec)
        // tvOS DVRHero metaParts: channel, "Sep 5 1:36 AM to 2:00 AM", S/E or duration.
        val dateRange = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMd"), java.util.Locale.getDefault()).format(Date(rec.startMillis)) + " " + timeRange(rec)
        val meta = listOfNotNull(channelName(rec).takeIf { it.isNotBlank() }, dateRange, se ?: durationLabel(rec))
        val buttons = buildList {
            if (recording) {
                if (canPlay) {
                    add(com.aeriotv.android.feature.movies.tv.TvHeroButton(context.getString(R.string.media_watch_from_start), Icons.Filled.PlayArrow, primary = true, id = "Primary") { armHero(); onPlayFromStart(rec) })
                    add(com.aeriotv.android.feature.movies.tv.TvHeroButton(context.getString(R.string.media_jump_to_live), Icons.Filled.Sensors, id = "JumpToLive") { armHero(); onJumpToLive(rec) })
                }
                if (canManageDvr || rec.source != DvrViewModel.Source.Server) {
                    add(com.aeriotv.android.feature.movies.tv.TvHeroButton(context.getString(R.string.media_stop_recording), Icons.Filled.Stop, id = "Stop") { onStop(rec) })
                }
            } else {
                add(com.aeriotv.android.feature.movies.tv.TvHeroButton(if (progress > 0f) context.getString(R.string.media_resume) else context.getString(R.string.common_play), Icons.Filled.PlayArrow, primary = true, id = "Primary") { armHero(); onPlay(rec) })
                if (progress > 0f) add(com.aeriotv.android.feature.movies.tv.TvHeroButton(context.getString(R.string.media_play_from_beginning), Icons.Filled.Replay, id = "FromStart") { armHero(); onPlayFromStart(rec) })
            }
            // Details is appended after the recording / finished branch, so a
            // recording-now page has it too (DVRView.swift:1620-1625).
            add(com.aeriotv.android.feature.movies.tv.TvHeroButton(context.getString(R.string.media_details), Icons.Outlined.Info, id = "Details") { armHero(); onInfo(rec) })
        }
        return com.aeriotv.android.feature.movies.tv.TvHeroPage(
            key = rec.id, title = rec.title.ifBlank { context.getString(R.string.media_recording) }, artUrl = rec.backdropUrl ?: rec.posterUrl, logoUrl = channelLogo(rec),
            subtitle = rec.subTitle, meta = meta, plot = rec.description.takeIf { it.isNotBlank() },
            // The "Continue watching" label moved OFF the banner card and into the
            // section header over the hero (Logan 2026-09-11); "Recording now"
            // stays, it is the live-capture dot, not a section name.
            eyebrow = if (recording) context.getString(R.string.media_recording_now) else null,
            eyebrowColor = if (recording) red else Color.Unspecified, eyebrowDot = recording,
            buttons = buttons, longPressActions = menuActions(rec),
        )
    }
    val card: @Composable (Rec, Modifier, () -> Unit) -> Unit = { rec, modifier, onClick ->
        val s = rec.effectiveStatus(now)
        val se = seLabel(rec)
        val sub = rec.subTitle?.takeIf { it.isNotBlank() }
        val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(rec.startMillis))
        val meta = when {
            s == DvrViewModel.Recording.Status.Scheduled -> "$day · " + timeRange(rec)
            se != null && sub != null -> "$se · $sub"
            sub != null -> sub
            se != null -> se
            else -> day
        }
        com.aeriotv.android.feature.movies.tv.TvRecordingCard(
            title = rec.title.ifBlank { stringResource(R.string.media_recording) }, meta = meta, artUrl = rec.backdropUrl ?: rec.posterUrl, logoUrl = channelLogo(rec),
            channelName = channelName(rec),
            trailing = if (s == DvrViewModel.Recording.Status.Scheduled) timeFmt.format(Date(rec.startMillis)) else durationLabel(rec),
            progress = progressOf(rec),
            badge = when (s) {
                DvrViewModel.Recording.Status.Recording -> com.aeriotv.android.feature.movies.tv.TvRecordingBadge("REC", red, dot = true)
                DvrViewModel.Recording.Status.Scheduled -> com.aeriotv.android.feature.movies.tv.TvRecordingBadge(day, Color.Black.copy(alpha = 0.6f), icon = Icons.Outlined.Schedule)
                else -> if (rec.partial) com.aeriotv.android.feature.movies.tv.TvRecordingBadge(stringResource(R.string.media_partial), Color(0xFFFF9F43).copy(alpha = 0.85f)) else null
            },
            onClick = onClick, modifier = modifier, longPressActions = menuActions(rec),
        )
    }
    fun shelf(title: String, items: List<Rec>, onClick: (Rec) -> Unit) = com.aeriotv.android.feature.movies.tv.TvShelf(
        title = title, items = items, key = { it.id }, cardWidth = 170.dp,
    ) { rec, modifier ->
        card(rec, modifier) {
            com.aeriotv.android.feature.movies.tv.TvReturnMemory.arm("dvr", rec.id, gridState)
            onClick(rec)
        }
    }
    // tvOS heroPages: Continue Watching when it has more than one entry,
    // else the single heroRecording pick, which falls through a playable
    // in-progress capture, any in-progress capture, the newest partly-watched
    // finished recording and finally the newest finished recording
    // (DVRView.swift:227-245). A settled library still shows a hero.
    val heroRecs = remember(continueWatching, recordingNow, recentRecordings, now) {
        if (continueWatching.size > 1) continueWatching
        else {
            val newestFinished = recentRecordings.sortedByDescending { it.startMillis }
            val heroPick = recordingNow.firstOrNull { it.inProgressUrl != null }
                ?: recordingNow.firstOrNull()
                ?: newestFinished.firstOrNull { val p = progressOf(it); p > 0f && p < 0.97f }
                ?: newestFinished.firstOrNull()
            listOfNotNull(heroPick)
        }
    }
    val heroPages = remember(heroRecs, now) { heroRecs.map(::hero) }
    // The section title over the banner, the same rule the phone deck uses
    // (line 423): every hero page capturing now reads "Recording Now".
    val heroSectionTitle = remember(heroRecs, now) {
        when {
            heroRecs.isEmpty() -> null
            heroRecs.all { it.effectiveStatus(now) == DvrViewModel.Recording.Status.Recording } -> context.getString(R.string.media_recording_now_title)
            else -> context.getString(R.string.media_continue_watching)
        }
    }
    com.aeriotv.android.feature.movies.tv.TvMediaPage(
        gridState = gridState,
        heroPages = heroPages,
        heroSectionTitle = heroSectionTitle,
        shelves = listOf(
            // tvOS: every card, shelf or grid, calls actions.play(rec), i.e.
            // RESUME (DVRView.swift:1198, 1300-1307).
            shelf(stringResource(R.string.media_recording_now_title), recordingNow) { onPlay(it) },
            shelf(stringResource(R.string.media_scheduled), scheduled) { onInfo(it) },
            shelf(stringResource(R.string.media_recent_recordings), if (recentRecordings.size > 1) recentRecordings else emptyList()) { onPlay(it) },
        ),
        headerTitle = if (isSearching) stringResource(R.string.media_results) else stringResource(R.string.media_all_recordings),
        headerCount = filteredLibrary.size,
        columns = 5,
        onFilter = onFilter,
        filterActive = filterActive,
        searchEnabled = true,
        searchActive = searchActive,
        query = query,
        onQueryChange = onQueryChange,
        onSearchToggle = onSearchToggle,
        onClearSearch = onClearSearch,
        searchPlaceholder = stringResource(R.string.media_search_recordings),
        isSearching = isSearching,
        gridRowSpacing = 22.dp,
        sortActions = DvrSortOrder.entries.map { o ->
            val label = stringResource(o.labelRes)
            com.aeriotv.android.core.tv.TvMenuAction(if (o == sortOrder) label + "  \u2713" else label) { onSort(o) }
        },
        pills = if (kindPills.size > 1) kindPills.map { stringResource(it.labelRes) } else emptyList(),
        selectedPill = selectedKind?.let { stringResource(it.labelRes) },
        onPill = { label -> onKind(kindPills.firstOrNull { context.getString(it.labelRes) == label }) },
        gridItems = filteredLibrary,
        gridKey = { it.id },
        cell = { rec, cellScope ->
            card(rec, cellScope.modifier) {
                // Return to the exact offset AND to the focusable that opened
                // it (tvOS keeps the tab alive).
                com.aeriotv.android.feature.movies.tv.TvReturnMemory.arm("dvr", rec.id, gridState)
                onPlay(rec)
            }
        },
        emptyContent = {
            if (isLoading) CircularProgressIndicator()
            else Text(stringResource(R.string.media_no_recordings), color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        isLoading = isLoading,
        railLetters = available,
        railIndexOf = { letter -> filteredLibrary.indexOfFirst { bucket(it.title) == letter } },
        railMinimumCount = 16,
        // tvOS DVR does not gate the rail on search (DVRView.swift:517).
        hideRailWhileSearching = false,
        // tvOS DVR: 620 pt / 310 dp with a hero, 260 pt / 130 dp without
        // (DVRView.swift:863).
        barHideThreshold = if (heroPages.isNotEmpty()) 310.dp else 130.dp,
        filterOpen = filterOpen,
        returnKey = remember { com.aeriotv.android.feature.movies.tv.TvReturnMemory.pending["dvr"] },
        returnOffset = remember { com.aeriotv.android.feature.movies.tv.TvReturnMemory.pendingOffset["dvr"] },
        returnSource = remember { com.aeriotv.android.feature.movies.tv.TvReturnMemory.pendingSource["dvr"] },
        onReturnHandled = { com.aeriotv.android.feature.movies.tv.TvReturnMemory.clear("dvr") },
        pageId = "dvr",
    )
}
