package com.aeriotv.android.core.data.sync

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.aeriotv.android.core.data.ChannelListDiff
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import com.aeriotv.android.core.data.repository.EpgSweepGate
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.core.network.DispatcharrLiveConnection
import com.aeriotv.android.core.network.DispatcharrLiveEligibility
import com.aeriotv.android.core.network.DispatcharrLiveEvent
import com.aeriotv.android.core.playback.PlaybackActivityTracker
import com.aeriotv.android.core.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps a Dispatcharr playlist in step with its server while the app is open:
 * a finished playlist refresh that changed channels reloads the lineup, a
 * finished EPG import refreshes the guide. Driven by [DispatcharrLiveConnection];
 * results reach the screen through [PlaylistRepository.cacheUpdates].
 *
 * Runs only while the setting is on, the active playlist is eligible
 * ([DispatcharrLiveEligibility]) and the app is in the foreground. Channel
 * edits made by hand in Dispatcharr send no event, so the lineup is also
 * compared every [CHANNEL_CHECK_INTERVAL_MS] while connected.
 *
 * Load: channel reloads are debounced. EPG events (dozens of sources refresh
 * throughout the day) are coalesced into one window check (one request) with
 * a cooldown; the open app writes and repaints only the channels whose
 * schedule changed, also while the guide is on screen. While this runs, the
 * stock full sweep waits while the guide is on screen
 * ([EpgSweepGate.guideOnScreen]). Nothing runs during multiview.
 */
@Singleton
class DispatcharrLiveUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val repository: PlaylistRepository,
    private val connection: DispatcharrLiveConnection,
    private val exoWindowState: com.aeriotv.android.feature.player.ExoWindowState,
) {
    private var started = false

    /** Guide window fetches asked for from elsewhere (a guide chosen in the player). */
    private val guideRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    /**
     * Fetch the guide window again shortly: a channel was just put on another
     * guide from the player ("Wrong guide? Choose another"), and the server
     * reads the new guide's programmes in the background after the save. Only
     * the channels whose programmes changed are repainted. Nothing while live
     * updates are off.
     */
    fun refreshGuideSoon(reason: String) {
        if (guideRequests.subscriptionCount.value > 0) {
            guideRequests.tryEmit(reason)
            return
        }
        // Live updates are off (or the app is not following a Dispatcharr
        // source): the choice is still followed, once, without the socket.
        val scope = appScope ?: return
        scope.launch {
            val playlist = repository.activePlaylist() ?: return@launch
            delay(GUIDE_CHOICE_RELOAD_MS)
            runCatching { checkChannels(playlist.id, "$reason (without live updates)", withGuide = true) }
                .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "guide choice reload failed", it) }
        }
    }

    /** The application scope [start] was given, for work outside a live-updates session. */
    @Volatile private var appScope: CoroutineScope? = null

    /** What identifies a connection; other row changes (counts, timestamps) must not reconnect. */
    private data class Target(val playlist: PlaylistEntity) {
        override fun equals(other: Any?): Boolean {
            val o = (other as? Target)?.playlist ?: return false
            val p = playlist
            return p.id == o.id && p.urlString == o.urlString && p.lanUrlString == o.lanUrlString &&
                p.username == o.username && p.password == o.password
        }
        override fun hashCode(): Int = playlist.id.hashCode()
    }

    /** Idempotent; called once from Application.onCreate. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        appScope = scope
        // Full screen vs corner player feeds PlaybackActivityTracker.watching.
        scope.launch {
            exoWindowState.mode.collect {
                PlaybackActivityTracker.fullscreenChanged(it == com.aeriotv.android.feature.player.ExoWindowState.Mode.Fullscreen)
            }
        }
        scope.launch {
            combine(prefs.dispatcharrLiveUpdates, repository.observeActivePlaylist(), foreground()) { on, playlist, fg ->
                val eligible = DispatcharrLiveEligibility.of(playlist) == DispatcharrLiveEligibility.AVAILABLE
                if (on && fg && eligible && playlist != null) Target(playlist) else null
            }
                .distinctUntilChanged()
                .collectLatest { target ->
                    if (target == null) {
                        Log.i(TAG, "live updates idle")
                        return@collectLatest
                    }
                    Log.i(TAG, "live updates on for ${target.playlist.id.take(8)}")
                    try {
                        follow(target.playlist)
                    } finally {
                        Log.i(TAG, "live updates stopped for ${target.playlist.id.take(8)}")
                    }
                }
        }
    }

    /** Socket, event handling and the hand-edit check, until cancelled. */
    private suspend fun follow(playlist: PlaylistEntity): Unit = try {
        EpgSweepGate.holdWhileWatching = true
        followConnected(playlist)
    } finally {
        EpgSweepGate.holdWhileWatching = false
    }

    private suspend fun followConnected(playlist: PlaylistEntity): Unit = coroutineScope {
        launch { connection.run(playlist) { repository.effectiveBaseUrl(playlist) } }

        var channelJob: Job? = null
        // A guide change is owed a guide window even when the lineup comes back
        // unchanged ("guide read": the channel already had the new key)
        var guideOwed = false
        fun scheduleChannelCheck(reason: String, afterMs: Long, guide: Boolean = false) {
            if (guide) guideOwed = true
            channelJob?.cancel()
            channelJob = launch {
                delay(afterMs)
                val withGuide = guideOwed
                guideOwed = false
                checkChannels(playlist.id, reason, withGuide)
            }
        }

        var epgJob: Job? = null
        var lastWindowAt = 0L
        fun scheduleGuideWindow(reason: String) {
            // Coalesce: a burst of source imports becomes one window check.
            if (epgJob?.isActive == true) return
            epgJob = launch {
                val sinceLast = System.currentTimeMillis() - lastWindowAt
                delay(maxOf(EPG_DEBOUNCE_MS, EPG_COOLDOWN_MS - sinceLast))
                awaitNoMultiview()
                lastWindowAt = System.currentTimeMillis()
                announceGuideWindow(playlist.id, reason)
            }
        }

        // Days past the live window are the stock sweep's job. A change owes
        // one; it runs as soon as the guide is not on screen (right away while
        // the user watches) instead of waiting for the next 15-minute tick. The sweep is gated server-side by the sources
        // fingerprint, so an owed sweep with nothing new costs one request.
        var sweepOwed = false
        // As if one had just run: the launch has its own sweep.
        var lastSweepAt = System.currentTimeMillis()
        var sweepTimer: Job? = null
        // Runs the owed sweep when allowed: the guide not on screen, and at
        // most once per SWEEP_MIN_INTERVAL_MS (sources refresh every few
        // minutes; days past tomorrow are not urgent). Otherwise it stays owed
        // and is retried when the interval ends or the guide leaves the screen.
        fun tryOwedSweep(reason: String) {
            if (!sweepOwed || EpgSweepGate.guideOnScreen) return
            val wait = SWEEP_MIN_INTERVAL_MS - (System.currentTimeMillis() - lastSweepAt)
            if (wait > 0) {
                if (sweepTimer?.isActive != true) sweepTimer = launch { delay(wait); tryOwedSweep(reason) }
                return
            }
            sweepOwed = false
            lastSweepAt = System.currentTimeMillis()
            runOwedSweep(playlist.id, reason)
        }
        fun owe(reason: String) {
            sweepOwed = true
            tryOwedSweep(reason)
        }
        launch {
            EpgSweepGate.guideOnScreenFlow.collect { onScreen ->
                if (!onScreen) tryOwedSweep("guide left the screen")
            }
        }

        launch {
            // collectLatest: a second choice restarts the round for both
            guideRequests.collectLatest { reason ->
                // The channel's guide key changed: the lineup is read again
                // first (the new key), then its programmes. The server reads a
                // guide nobody had read in the background, so the window is
                // looked at again a little later; a Dispatch More that says
                // when it has read it (v219) makes these a formality.
                delay(GUIDE_CHOICE_RELOAD_MS)
                awaitNoMultiview()
                lastWindowAt = System.currentTimeMillis()
                checkChannels(playlist.id, reason, withGuide = true)
                for (again in GUIDE_CHOICE_AGAIN_MS) {
                    delay(again)
                    awaitNoMultiview()
                    lastWindowAt = System.currentTimeMillis()
                    announceGuideWindow(playlist.id, "$reason, again")
                }
            }
        }

        // Hand edits in Dispatcharr send no event: compare the lineup regularly.
        launch {
            while (true) {
                delay(CHANNEL_CHECK_INTERVAL_MS)
                if (channelJob?.isActive != true) scheduleChannelCheck("periodic", 0)
            }
        }

        connection.events.collect { event ->
            when (event) {
                // Also after every reconnect: catch up on anything missed while
                // disconnected. Both checks are cheap when nothing changed.
                DispatcharrLiveEvent.Connected -> {
                    scheduleChannelCheck("connected", CONNECT_SETTLE_MS)
                    // Not on the first connect of this app run: the launch loads
                    // the guide itself, and both at once wrote the same day twice
                    // (a Chromecast HD after a restart: 95 s of guide window next
                    // to the launch download and the sweep, everything lagging).
                    // A reconnect later catches up on what was missed.
                    if (firstConnectDone) scheduleGuideWindow("connected")
                    else Log.i(TAG, "guide window (connected): skipped on the first connect, the launch loads the guide")
                    firstConnectDone = true
                    // No sweep owed here: the app's launch check already runs
                    // one when the sources changed, and a connect is a launch
                    // or a return (both sweeps ran together at every start).
                }
                is DispatcharrLiveEvent.PlaylistRefreshed ->
                    if (event.channelsChanged) scheduleChannelCheck("playlist ${event.accountId} refreshed", CHANNEL_DEBOUNCE_MS)
                    else Log.i(TAG, "playlist ${event.accountId} refreshed without channel changes")
                is DispatcharrLiveEvent.EpgRefreshed -> {
                    scheduleGuideWindow("EPG source ${event.sourceId} refreshed")
                    owe("EPG source ${event.sourceId} refreshed")
                }
                // A plugin changed channels (Show Groups moving copies in and out of
                // its group): the lineup is compared at once -- a copy that joins
                // for a show must not wait for the 10-minute check.
                is DispatcharrLiveEvent.ChannelsChanged ->
                    scheduleChannelCheck(
                        "${event.source ?: "server"} changed ${event.channelUuids.size} channels" +
                            if (event.guide) " (guide)" else "",
                        if (event.guide) GUIDE_CHANGED_DEBOUNCE_MS else CHANNELS_CHANGED_DEBOUNCE_MS,
                        guide = event.guide,
                    )
                DispatcharrLiveEvent.RecordingsChanged -> Unit
            }
        }
    }

    /**
     * Re-fetch the lineup and hand it to the open app when it changed. New
     * channels, and channels put on another guide, have no programmes on screen
     * under their new key yet, so a window check follows; [withGuide] asks for
     * one whatever the lineup says (the server said a guide changed or was read).
     */
    private suspend fun checkChannels(playlistId: String, reason: String, withGuide: Boolean = false) {
        awaitNoMultiview()
        val playlist = repository.activePlaylist()?.takeIf { it.id == playlistId } ?: return
        val before = repository.loadCachedChannels(playlistId)
        val result = withContext(Dispatchers.IO) { repository.refresh(playlist) }
        val channels = result.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "channel check ($reason) failed: ${it::class.simpleName}: ${it.message}")
            return
        }
        val diff = ChannelListDiff.between(before, channels)
        val beforeKeys = before.associate { it.id to it.tvgID }
        val rekeyed = channels.count { ch -> beforeKeys[ch.id]?.let { it != ch.tvgID } == true }
        Log.i(TAG, "channel check ($reason): $diff" + if (rekeyed > 0) ", $rekeyed on another guide" else "")
        if (diff.hasChanges) {
            repository.announceCacheUpdate(PlaylistRepository.CacheUpdate.Channels(playlistId, channels))
        }
        // New channels and channels on another guide have nothing under their
        // key yet: the window check fetches and patches them like any other
        // changed channel. (It used to follow only new channels, so a guide
        // changed on the server showed its old programmes -- or none -- until
        // the next full sweep.)
        when {
            diff.added > 0 -> announceGuideWindow(playlistId, "new channels")
            rekeyed > 0 -> announceGuideWindow(playlistId, "$rekeyed channels on another guide")
            withGuide -> announceGuideWindow(playlistId, "guide changed on the server")
        }
    }

    /**
     * Fetch Dispatcharr's live window (now-1h..now+24h, one request) and hand
     * it to the open app, which writes and repaints only the channels whose
     * schedule changed. Days further ahead are left to the stock sweep, which
     * with live updates on runs while nothing is being watched.
     */
    private suspend fun announceGuideWindow(playlistId: String, reason: String) {
        val playlist = repository.activePlaylist()?.takeIf { it.id == playlistId } ?: return
        val now = System.currentTimeMillis()
        val from = now - HOUR_MS
        val to = now + DAY_MS
        val programmes = try {
            withContext(Dispatchers.IO) { repository.fetchLiveGridProgrammes(playlist, from, to) }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "guide window ($reason) failed: ${t::class.simpleName}: ${t.message}")
            return
        }
        Log.i(TAG, "guide window ($reason): ${programmes.size} programmes fetched")
        if (programmes.isEmpty()) return
        repository.announceCacheUpdate(PlaylistRepository.CacheUpdate.GuideWindow(playlistId, programmes, from, to))
    }

    /** The first socket connect of this app run has happened (see Connected). */
    @Volatile private var firstConnectDone = false

    private fun runOwedSweep(playlistId: String, reason: String) {
        Log.i(TAG, "full EPG sweep ($reason)")
        // Starts in the repository's own scope; a sweep already running is reused.
        repository.startEpgBackgroundSweep(playlistId)
    }

    /** Multiview owns the decoders; server-triggered work waits until it closes. */
    private suspend fun awaitNoMultiview() {
        while (PlaybackActivityTracker.isMultiStreamActive) delay(MULTIVIEW_POLL_MS)
    }

    /**
     * Foreground, with a grace period before reporting background: some TV
     * builds deliver a stale ON_STOP right after a relaunch (see
     * CompanionHostController), and a short trip to the system settings
     * should not drop and rebuild the socket.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun foreground(): Flow<Boolean> =
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
            .distinctUntilChanged()
            .transformLatest { fg ->
                if (fg) {
                    emit(true)
                } else {
                    delay(BACKGROUND_GRACE_MS)
                    emit(isProcessForeground())
                }
            }
            .distinctUntilChanged()

    private fun isProcessForeground(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val pid = android.os.Process.myPid()
        return am.runningAppProcesses?.any {
            it.pid == pid && it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } == true
    }

    private companion object {
        const val TAG = "DispatcharrLive"
        const val HOUR_MS = 60L * 60_000L
        const val DAY_MS = 24L * HOUR_MS
        const val BACKGROUND_GRACE_MS = 30_000L
        /** Let the server finish writing channels before re-reading them. */
        const val CHANNEL_DEBOUNCE_MS = 5_000L
        /** A plugin's pass sends one message; a second pass a minute later is
         *  its own check. */
        const val CHANNELS_CHANGED_DEBOUNCE_MS = 2_000L
        /** A guide changed on the server: long enough to take a whole Guides apply as one. */
        const val GUIDE_CHANGED_DEBOUNCE_MS = 1_000L
        const val CONNECT_SETTLE_MS = 10_000L
        const val CHANNEL_CHECK_INTERVAL_MS = 10L * 60_000L
        const val EPG_DEBOUNCE_MS = 60_000L
        /** The window check is one request of ~35K programmes plus a per-channel
         *  compare: seconds of work and garbage on a Chromecast HD, with dozens of
         *  sources refreshing through the day. At most every 10 minutes. */
        const val EPG_COOLDOWN_MS = 10L * 60_000L
        /** After a guide is chosen in the player: the server reads the new
         *  guide's programmes in the background after the save. */
        const val GUIDE_CHOICE_RELOAD_MS = 2_000L
        /** Then the window again after these (30 s and 90 s after the choice). */
        val GUIDE_CHOICE_AGAIN_MS = listOf(28_000L, 60_000L)
        const val MULTIVIEW_POLL_MS = 60_000L
        /** Owed full sweeps (every Guide Days day, back and ahead) run at most
         *  this often: the live window keeps the next 24 h current, and a sweep
         *  every 10 minutes kept a Chromecast HD busy all day. */
        const val SWEEP_MIN_INTERVAL_MS = 6L * 60L * 60_000L
    }
}
