package com.aeriotv.android.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.core.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Periodic background refresh of the active playlist's channel list + EPG.
 * Audit task #48: addresses Archie's "loading times shouldn't be an issue if
 * caching is working" complaint by keeping the cache warm so the user never
 * cold-paints blank UI.
 *
 * Each successful run rewrites:
 *   - channel_snapshot (Phase 130) so the rail re-paints instantly on the
 *     next cold launch, no network needed.
 *   - epg_programme (Phase 121) so now-playing / the guide is current.
 *
 * Runs every 6h on an unmetered network with a charging-not-required +
 * battery-not-low constraint. Periodic WorkManager fires opportunistically
 * once those constraints become true; users on mobile data will only get
 * a refresh on their next Wi-Fi+power session, which is exactly the
 * iOS BGTaskScheduler behaviour we're matching.
 *
 * The user can toggle this entirely via [AppPreferences.backgroundRefreshEnabled]
 * (Network Settings). Default ON. With the toggle off, [enqueuePeriodic] is
 * a no-op and any previously-scheduled work is canceled.
 *
 * A run never starts within [LAUNCH_QUIET_MS] of the app starting or while a
 * picture plays, and on a Dispatch More v272 server it first asks the start-up
 * check and downloads only the part that changed (fork/guide-loading-plan.md).
 * A miss (no active playlist, no network mid-window, launch or playback, etc)
 * returns Result.success() so WorkManager re-schedules the next interval at
 * the normal cadence, not a backoff penalty.
 */
@HiltWorker
class PlaylistRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: PlaylistRepository,
    private val prefs: AppPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        if (!prefs.backgroundRefreshEnabled.first()) {
            Log.i(TAG, "Background refresh disabled in settings; skipping")
            return@runCatching Result.success()
        }
        if (com.aeriotv.android.core.playback.PlaybackActivityTracker.isMultiStreamActive) {
            // 2026-08-31 Streamer multiview stutter hunt: a refresh's download +
            // gunzip + XMLTV parse at normal priority starves the MediaCodec
            // loops when decoders own every core. Yield and let WorkManager
            // retry after backoff; the cache staying warm is never worth
            // visible judder on what the user is watching right now. A single
            // stream does not block the refresh: its player instance lives
            // for the whole session, so waiting on it meant never refreshing.
            Log.i(TAG, "Multiview active; deferring background refresh")
            return@runCatching Result.retry()
        }
        // Never inside a launch or next to playback (fork/guide-loading-plan.md): a run
        // interrupted by the app being killed counts as due, so it used to start again in the
        // first seconds of the next launch and download the lineup and every guide record of
        // the server next to the app's own start -- on a Chromecast HD the heap filled and the
        // picture froze. The app checks for itself at launch; this run waits for the next period.
        val sinceStartMs = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
        if (sinceStartMs < LAUNCH_QUIET_MS) {
            Log.i(TAG, "App started ${sinceStartMs / 1000}s ago; leaving the refresh to its next period")
            return@runCatching Result.success()
        }
        if (com.aeriotv.android.core.playback.PlaybackActivityTracker.isPlaying) {
            Log.i(TAG, "Playing; leaving the refresh to its next period")
            return@runCatching Result.success()
        }
        val playlist = repository.activePlaylist()
        if (playlist == null) {
            Log.i(TAG, "No active playlist; nothing to refresh")
            return@runCatching Result.success()
        }
        // Dispatch More v272: ask what changed and download only that
        val verdict = repository.startupCheck(playlist)
        if (verdict?.lineup == com.aeriotv.android.core.data.sync.SyncChange.SAME &&
            verdict.guides == com.aeriotv.android.core.data.sync.SyncChange.SAME
        ) {
            Log.i(TAG, "Start-up check: lineup and guide unchanged; nothing to download")
            return@runCatching Result.success()
        }
        // Channel refresh: writes channel_snapshot via the same path the
        // user-initiated refresh uses. saveChannelsToCache is best-effort
        // inside PlaylistRepository.refresh -- a cache write failure won't
        // fail the network fetch, and vice versa.
        val channels = if (verdict?.lineup == com.aeriotv.android.core.data.sync.SyncChange.SAME) {
            kotlin.Result.success(repository.loadCachedChannels(playlist.id))
        } else {
            repository.refresh(playlist)
        }
        if (channels.isFailure) {
            Log.w(TAG, "Channel refresh failed", channels.exceptionOrNull())
            return@runCatching Result.retry()
        }
        // An open app repaints from this instead of waiting for the next launch.
        repository.announceCacheUpdate(
            PlaylistRepository.CacheUpdate.Channels(playlist.id, channels.getOrThrow()),
        )
        // EPG refresh: writes epg_programme. loadEpg already updates the
        // playlist's lastEpgRefreshedAt on success. Pass the candidate-key
        // set (P3 #13) so the XMLTV parser filters dead programmes inline.
        val knownKeys = com.aeriotv.android.core.data.buildChannelEpgKeyBridge(
            channels.getOrThrow(),
        ).keys
        // E-6: honour the same cache-freshness gate the foreground load uses.
        // The worker used to refetch and rewrite the whole guide on every
        // wakeup regardless of how recently the app had already done it -- a
        // full ~139k-row merge, four indexes per row, and a retention sweep,
        // for data that could be minutes old. If a foreground refresh just ran,
        // there is nothing here worth the disk churn.
        val newestEpg = runCatching { repository.newestEpgFetch(playlist.id) }.getOrNull()
        if (verdict?.guides == com.aeriotv.android.core.data.sync.SyncChange.SAME ||
            newestEpg != null && System.currentTimeMillis() - newestEpg < EPG_FRESH_ENOUGH_MS
        ) {
            Log.i(TAG, "EPG cache is fresh; skipping the background EPG rewrite")
            return@runCatching Result.success()
        }
        val epg = repository.loadEpg(playlist, knownKeys)
        if (epg.isFailure) {
            Log.w(TAG, "EPG refresh failed", epg.exceptionOrNull())
            // EPG miss is recoverable: channels are still fresh.
            return@runCatching Result.retry()
        }
        // iOS parity (EPGGuideView.swift lines 1395-1414): bridge programme
        // channelIds onto the canonical key the M3UChannel will look up under,
        // so Dispatcharr `/output/epg` (channel-number keyed) and Dummy EPG
        // (UUID keyed) feeds populate the rail even when tvgID is blank.
        // Saved-as-bridged means a relaunch can paint cached programmes onto
        // the rail without the in-memory bridge step at all.
        val bridged = com.aeriotv.android.core.data.bridgeChannelIds(
            epg.getOrThrow(),
            channels.getOrThrow(),
        )
        runCatching { repository.saveEpgToCache(playlist.id, bridged) }
            .onSuccess {
                repository.announceCacheUpdate(PlaylistRepository.CacheUpdate.Guide(playlist.id))
                runCatching { repository.rememberGuides(playlist, verdict) }
            }
            .onFailure { Log.w(TAG, "saveEpgToCache failed", it) }
        Log.i(
            TAG,
            "Background refresh OK: ${channels.getOrThrow().size} channels, " +
                "${epg.getOrThrow().size} programmes",
        )
        Result.success()
    }.getOrElse { t ->
        Log.w(TAG, "Background refresh threw", t)
        Result.retry()
    }

    companion object {
        const val TAG = "PlaylistRefreshWorker"
        const val UNIQUE_NAME = "aeriotv-playlist-refresh"

        /** E-6: skip the background EPG rewrite when the cache was refreshed
         *  this recently. Deliberately looser than the foreground TTL (30
         *  minutes): the worker runs on a 6-hour cadence, so anything newer
         *  than an hour means the app already did this work. */
        private const val EPG_FRESH_ENOUGH_MS = 60L * 60L * 1000L

        /** No refresh this soon after the app process started: the launch does its own check. */
        private const val LAUNCH_QUIET_MS = 10L * 60L * 1000L

        /** The interval the schedule was last registered with (see [enqueuePeriodic]). */
        private const val PREFS = "playlist_refresh_schedule"
        private const val KEY_INTERVAL = "interval_mins"

        /**
         * Idempotent registration. Call from app startup with the user's
         * configured interval (default 360 minutes = 6 hours, matching the
         * prior hardcoded `PERIOD_HOURS`). Uses `UPDATE` policy so a changed
         * interval re-registers immediately instead of waiting for the
         * existing schedule to expire -- the iOS `bgRefreshIntervalMins`
         * setting takes effect within seconds of toggling.
         */
        fun enqueuePeriodic(context: Context, intervalMins: Int = 360) {
            val safeMins = intervalMins.coerceAtLeast(15)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(
                safeMins.toLong(), TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                // A first run one whole interval out, never at once: registering happens at app
                // start, which is exactly when a refresh must not run
                .setInitialDelay(safeMins.toLong(), TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            // UPDATE only when the interval really changed (so a new pick in Settings applies
            // at once). Re-registering the same schedule at every start with UPDATE stopped a
            // run in progress and started it again in the middle of the launch (seen at every
            // cold start on a Chromecast HD, 2026-10-20). Installs from before this registered
            // without the interval saved: they take UPDATE once, which brings the initial delay.
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val changed = prefs.getInt(KEY_INTERVAL, -1) != safeMins
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                if (changed) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            if (changed) prefs.edit().putInt(KEY_INTERVAL, safeMins).apply()
        }

        /** Cancel the schedule -- called when the user disables background refresh. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
