package com.aeriotv.android.core.network

import android.util.Log
import com.aeriotv.android.core.data.db.dao.PlaylistDao
import com.aeriotv.android.core.data.repository.PlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watch together (Dispatch More v260, its app_links): arrTV devices of one login join each
 * other, and from then on whichever changes channel, the others follow. Both are always on
 * the same channel, one provider connection, so it also fits logins held to one device at a
 * time. Live channels only.
 *
 * What the screens share: whether this device is linked, the channel it was asked to follow
 * (by the server's socket message, or by joining), and which button of the other-device
 * notice the remote is on.
 */
object WatchTogether {
    /** A channel this device is to tune to, by its Dispatcharr uuid. [seq] tells two
     *  requests for the same channel apart. */
    data class Follow(val channelUuid: String, val seq: Long = System.nanoTime(), val fresh: Boolean = false)

    private val _follow = MutableStateFlow<Follow?>(null)

    /** The channel to tune to, until a screen has (see [consumed]). */
    val follow: StateFlow<Follow?> = _follow.asStateFlow()

    private val _linked = MutableStateFlow(false)
    val linked: StateFlow<Boolean> = _linked.asStateFlow()

    /** The full player is open: it tunes a follow in place; otherwise the app opens it. */
    @Volatile
    var playerOpen = false

    /** The offer to join a device watching is asked once per start of the app. */
    @Volatile
    var offerAsked = false

    /** The other-device notice on a TV: 0 = Watch here, 1 = Join (Left / Right move it). */
    val noticeChoice = MutableStateFlow(0)

    /** Installed by Navigation for the life of the app: join a device / stop watching
     *  together (DispatchMoreLinks), so screens without the helper can ask. */
    @Volatile
    var joiner: ((String) -> Unit)? = null

    @Volatile
    var leaver: (() -> Unit)? = null

    fun join(device: String) {
        joiner?.invoke(device)
    }

    fun leave() {
        leaver?.invoke()
    }

    /** [fresh]: tune it afresh even when it is the channel playing (joining: in step at once). */
    fun followChannel(channelUuid: String, fresh: Boolean = false) {
        Log.i(TAG, "follow channel $channelUuid${if (fresh) " (fresh)" else ""}")
        _follow.value = Follow(channelUuid, fresh = fresh)
    }

    private val _resync = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A device joined this one: tune the channel afresh, at the same moment the joiner tunes
     *  it, so the two start in step (DispatchMoreLinks). */
    val resync: kotlinx.coroutines.flow.SharedFlow<Unit> = _resync

    fun consumed(request: Follow) {
        _follow.compareAndSet(request, null)
    }

    internal fun setLinked(linked: Boolean) {
        _linked.value = linked
    }

    /** The server said another device joined this one (or told it to follow): linked. Only
     *  the joiner knew before, so the joined one never reported where it was and the two
     *  were never put in step (the Shield, 2026-10-17). */
    fun linkJoined(resync: Boolean = false) {
        if (!_linked.value) Log.i(TAG, "joined by another device; linked")
        _linked.value = true
        if (resync) _resync.tryEmit(Unit)
    }

    /** The server said the other device left: this one watches on alone. */
    fun linkEnded() {
        Log.i(TAG, "the other device left; watching alone")
        _linked.value = false
    }

    private const val TAG = "WatchTogether"
}

@Singleton
class DispatchMoreLinks @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: PlaylistDao,
    private val repository: PlaylistRepository,
    private val client: DispatcharrClient,
    private val auth: DispatcharrAuthBroker,
    private val holder: com.aeriotv.android.core.playback.AerioExoPlayerHolder,
) {
    /** Joins and leaves outlive the press (the notice or the menu closes at once). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // For the screens without this helper (the other-device notice, the Options menu)
        WatchTogether.joiner = { device ->
            joinInBackground(device) { why ->
                val text = com.aeriotv.android.core.locale.AppText.get(
                    when (why) {
                        Joined.SWITCHED_OFF -> com.aeriotv.android.R.string.player_join_switched_off
                        Joined.UNREACHABLE -> com.aeriotv.android.R.string.core_dispatchmore_unreachable_plain
                        else -> com.aeriotv.android.R.string.player_join_failed
                    },
                )
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        WatchTogether.leaver = { leaveInBackground() }
        // Leaving the app (Home, another app, closing it) leaves watching together: the other
        // device plays on, and this one does not follow it when it comes back later
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && WatchTogether.linked.value) {
                        Log.i(TAG, "left the app; leaving watching together")
                        leaveInBackground()
                    }
                },
            )
        }
        // In step: joined, this device tunes afresh with the joiner; while linked, the devices
        // compare where they are, and the one behind tunes afresh
        scope.launch {
            WatchTogether.resync.collect {
                withContext(Dispatchers.Main) { holder.freshTuneForInStep("joined") }
            }
        }
        scope.launch {
            WatchTogether.linked.collectLatest { linked -> if (linked) keepInStep() }
        }
    }

    /**
     * Every [STEP_CHECK_MS] while linked: this device's position (the stream timestamp on
     * screen) goes to the server and the others' come back; well behind (InStepCatchUp), this
     * device tunes afresh, which puts it as far behind live as a fresh connection is -- where
     * the other one is, or ahead of it, and then that one catches up the same way. Never a
     * wait. Cancelled when the link ends.
     */
    private suspend fun keepInStep() {
        val catchUp = com.aeriotv.android.core.playback.InStepCatchUp()
        while (true) {
            kotlinx.coroutines.delay(STEP_CHECK_MS)
            val target = target() ?: continue
            val (pts, channelId) = withContext(Dispatchers.Main) {
                holder.inStepPosition() to holder.currentChannelId
            }
            if (pts == null) continue
            val at = System.currentTimeMillis()
            val others = runCatching {
                auth.withApiKeyRetry(target.first) { key -> client.reportLinkPosition(target.second, key, target.third, pts, at) }
            }.getOrNull().orEmpty()
            // The one furthest ahead is the one this device is behind of
            val (ahead, of) = others
                .map { com.aeriotv.android.core.playback.InStep.aheadMs(pts, at, it.pts, it.atMs) to it }
                .minByOrNull { it.first } ?: continue
            val behind = -ahead
            if (!catchUp.decide(channelId, behind, android.os.SystemClock.elapsedRealtime())) continue
            Log.i(TAG, "in step: ${behind}ms behind ${of.name}; tuning afresh")
            withContext(Dispatchers.Main) { holder.freshTuneForInStep("${behind}ms behind ${of.name}") }
            // Let the fresh connection start and settle before looking again
            kotlinx.coroutines.delay(STEP_SETTLE_MS)
        }
    }

    /** The active playlist's server, when it is a Dispatch More server that offers it. */
    private suspend fun target(): Triple<String, String, String>? {
        val playlist = dao.firstActive()?.takeIf { !it.apiKey.isNullOrBlank() } ?: return null
        val base = runCatching { repository.effectiveBaseUrl(playlist) }.getOrNull() ?: return null
        val server = DispatchMore.serverFor(base)?.takeIf { it.linkDevices } ?: return null
        return Triple(playlist.id, base, server.linkPath)
    }

    fun available(): Boolean = DispatchMore.deviceId != null

    /** What this device may join now, and whether it is linked; null when the server says nothing. */
    suspend fun state(): DispatcharrClient.LinkState? = withContext(Dispatchers.IO) {
        val (playlistId, base, path) = target() ?: return@withContext null
        runCatching {
            auth.withApiKeyRetry(playlistId) { key -> client.fetchLinkState(base, key, path) }
        }.getOrNull()?.also { WatchTogether.setLinked(it.linked) }
    }

    /** How a join went: the reason is what the viewer is told when it did not. */
    enum class Joined { YES, SWITCHED_OFF, NOT_WATCHING, UNREACHABLE }

    /** Join [device]: links the two, and tunes to its channel (WatchTogether.follow). */
    suspend fun join(device: String): Joined = withContext(Dispatchers.IO) {
        // Not a Dispatch More server, or its Watch together switch is off (as the last
        // capabilities answer said): nothing to ask
        val (playlistId, base, path) = target() ?: return@withContext Joined.SWITCHED_OFF
        val answer = runCatching {
            auth.withApiKeyRetry(playlistId) { key -> client.joinLink(base, key, path, device) }
        }
        val channel = answer.getOrElse {
            Log.w(TAG, "join $device failed: ${it.message}")
            return@withContext Joined.UNREACHABLE
        } ?: return@withContext Joined.NOT_WATCHING
        Log.i(TAG, "joined $device on $channel")
        WatchTogether.setLinked(true)
        // Afresh even when it is the channel playing: the device joined tunes afresh now too
        WatchTogether.followChannel(channel, fresh = true)
        Joined.YES
    }

    fun joinInBackground(device: String, onFailed: (Joined) -> Unit = {}) {
        scope.launch {
            val joined = join(device)
            if (joined != Joined.YES) withContext(Dispatchers.Main) { onFailed(joined) }
        }
    }

    /** Stop watching together (the Options menu, or a fresh start of the app). */
    fun leaveInBackground() {
        WatchTogether.setLinked(false)
        scope.launch {
            val (playlistId, base, path) = target() ?: return@launch
            runCatching { auth.withApiKeyRetry(playlistId) { key -> client.leaveLink(base, key, path) } }
                .onFailure { Log.w(TAG, "leave failed: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "WatchTogether"
        const val STEP_CHECK_MS = 10_000L
        const val STEP_SETTLE_MS = 20_000L
    }
}
