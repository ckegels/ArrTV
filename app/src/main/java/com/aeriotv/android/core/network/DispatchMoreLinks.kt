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

    /** The server said the other device left: this one watches on alone. v266: [reason] when
     *  another person's device ([name]) ended it for this one. */
    fun linkEnded(reason: String? = null, name: String? = null) {
        Log.i(TAG, "the other device left${reason?.let { " ($it)" }.orEmpty()}; watching alone")
        _linked.value = false
        followRequest.value = null
        val who = name?.takeIf { it.isNotBlank() } ?: com.aeriotv.android.core.locale.AppText.get(com.aeriotv.android.R.string.watch_together_unnamed_device)
        when (reason) {
            "removed" -> notify(com.aeriotv.android.R.string.watch_together_removed, who)
            "stayed" -> notify(com.aeriotv.android.R.string.watch_together_stayed, who)
            "not_allowed" -> notify(com.aeriotv.android.R.string.watch_together_not_allowed, who)
        }
        refresh()
    }

    // ── With other people in the house (Dispatch More v266) ──────────────────────────────

    /** Another person's device asks to join this one. */
    data class JoinRequest(val by: String, val name: String, val user: String, val channelName: String)

    /** Another person's device this one leads changed channel: follow it, or stay. */
    data class FollowRequest(val by: String, val name: String, val user: String, val channel: String, val channelName: String)

    /** Shown by the app-wide pop-up (WatchTogetherPrompts) until answered. */
    val joinRequest = MutableStateFlow<JoinRequest?>(null)
    val followRequest = MutableStateFlow<FollowRequest?>(null)

    /** What this device may join and who it is linked with, for the Join button and its list;
     *  refreshed while the app is open (DispatchMoreLinks). */
    val status = MutableStateFlow<DispatcharrClient.LinkState?>(null)

    /** The Watch together list is open (the Join button, the player's menu). */
    val sheetOpen = MutableStateFlow(false)

    /** At the start of the app, a device of this login or another person's that is watching:
     *  a small card top right over whatever plays, for ten seconds (WatchTogetherHint). On a
     *  TV, OK opens the Watch together list while it is up and Back puts it away. */
    val hint = MutableStateFlow<DispatcharrClient.LinkOffer?>(null)

    /** The Watch together buttons stand out while the start card is up, and a while after,
     *  so the eye finds them. */
    val highlight = MutableStateFlow(false)

    fun showHint(offer: DispatcharrClient.LinkOffer) {
        hint.value = offer
        highlight.value = true
    }

    /** The key the card took on its way down, so its release goes nowhere either. */
    @Volatile
    private var swallowUp = -1

    /** MainActivity.dispatchKeyEvent, before anything else: OK opens the list and Back puts the
     *  card away, only while it is up; every other key passes. */
    fun hintKey(event: android.view.KeyEvent): Boolean = hintKey(event.action, event.keyCode, event.repeatCount)

    internal fun hintKey(action: Int, keyCode: Int, repeatCount: Int): Boolean {
        if (action == android.view.KeyEvent.ACTION_UP && keyCode == swallowUp) {
            swallowUp = -1
            return true
        }
        if (hint.value == null || action != android.view.KeyEvent.ACTION_DOWN || repeatCount != 0) return false
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                swallowUp = keyCode
                hint.value = null
                openSheet()
                true
            }
            android.view.KeyEvent.KEYCODE_BACK -> {
                swallowUp = keyCode
                hint.value = null
                true
            }
            else -> false
        }
    }

    /** The other person's device asked to let this one in, while waiting for the answer. */
    @Volatile
    var asking: String? = null

    /** Installed by DispatchMoreLinks: the calls the screens make. */
    @Volatile var answerer: ((String, Boolean) -> Unit)? = null
    @Volatile var stayer: ((String) -> Unit)? = null
    @Volatile var remover: ((String) -> Unit)? = null
    @Volatile var refresher: (() -> Unit)? = null
    @Volatile var notifier: ((String) -> Unit)? = null

    fun askedToJoin(request: JoinRequest) {
        Log.i(TAG, "${request.name} asks to join")
        joinRequest.value = request
    }

    fun askedToFollow(request: FollowRequest) {
        Log.i(TAG, "${request.name} changed to ${request.channel}; follow or stay")
        followRequest.value = request
    }

    /** Allow / Decline on the join pop-up (ten seconds unanswered is Allow). */
    fun answer(allow: Boolean) {
        val request = joinRequest.value ?: return
        joinRequest.value = null
        answerer?.invoke(request.by, allow)
    }

    /** Follow on the follow pop-up (ten seconds unanswered is Follow): tune its channel. */
    fun follow() {
        val request = followRequest.value ?: return
        followRequest.value = null
        followChannel(request.channel)
    }

    /** Stay on the follow pop-up, or the pop-up dismissed: this channel, and the other leaves. */
    fun stay() {
        val request = followRequest.value ?: return
        followRequest.value = null
        stayer?.invoke(request.by)
    }

    /** The other person's device asked let this one in: linked, on its channel, tuned afresh. */
    fun accepted(channel: String) {
        asking = null
        _linked.value = true
        followChannel(channel, fresh = true)
        refresh()
    }

    fun declined(name: String, reason: String?) {
        asking = null
        notify(
            if (reason == "not_allowed") com.aeriotv.android.R.string.watch_together_declined_not_allowed
            else com.aeriotv.android.R.string.watch_together_declined,
            name,
        )
    }

    /** A leader takes another person's device out of its group (Disconnect). */
    fun disconnect(device: String) {
        remover?.invoke(device)
    }

    fun refresh() {
        refresher?.invoke()
    }

    fun openSheet() {
        hint.value = null
        sheetOpen.value = true
        refresh()
    }

    fun notify(res: Int, vararg args: Any) {
        notifier?.invoke(com.aeriotv.android.core.locale.AppText.get(res, *args))
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
        WatchTogether.answerer = { device, allow -> answerInBackground(device, allow) }
        WatchTogether.stayer = { device -> stayInBackground(device) }
        WatchTogether.remover = { device -> removeInBackground(device) }
        WatchTogether.refresher = { scope.launch { state() } }
        WatchTogether.notifier = { text ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        // The Join button's list: what may be joined changes as devices start and stop, so it is
        // asked again every so often while the app is in front
        scope.launch {
            while (true) {
                val inFront = withContext(Dispatchers.Main) {
                    androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                }
                if (inFront && target() != null) state()
                kotlinx.coroutines.delay(STATUS_EVERY_MS)
            }
        }
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
        }.getOrNull()?.also {
            WatchTogether.setLinked(it.linked)
            WatchTogether.status.value = it
        }
    }

    /** How a join went: the reason is what the viewer is told when it did not. */
    enum class Joined { YES, ASKED, SWITCHED_OFF, NOT_WATCHING, UNREACHABLE }

    /** Join [device]: links the two, and tunes to its channel (WatchTogether.follow). */
    suspend fun join(device: String): Joined = withContext(Dispatchers.IO) {
        // Not a Dispatch More server, or its Watch together switch is off (as the last
        // capabilities answer said): nothing to ask
        val (playlistId, base, path) = target() ?: return@withContext Joined.SWITCHED_OFF
        val answer = runCatching {
            auth.withApiKeyRetry(playlistId) { key -> client.joinLink(base, key, path, device) }
        }
        val joined = answer.getOrElse {
            Log.w(TAG, "join $device failed: ${it.message}")
            return@withContext Joined.UNREACHABLE
        } ?: return@withContext Joined.NOT_WATCHING
        if (joined is DispatcharrClient.JoinAnswer.Asked) {
            // Another person's device: it is asked, and the answer comes on the socket
            val who = listOf(joined.user, joined.name).filter { it.isNotBlank() }.joinToString(" · ")
            Log.i(TAG, "asked $device ($who) to join")
            WatchTogether.asking = who
            WatchTogether.notify(com.aeriotv.android.R.string.watch_together_asked, who)
            scope.launch {
                kotlinx.coroutines.delay(ASK_WAIT_MS)
                if (WatchTogether.asking == who) {
                    WatchTogether.asking = null
                    WatchTogether.notify(com.aeriotv.android.R.string.watch_together_no_answer, who)
                }
            }
            return@withContext Joined.ASKED
        }
        val channel = (joined as DispatcharrClient.JoinAnswer.Linked).channel
        Log.i(TAG, "joined $device on $channel")
        WatchTogether.setLinked(true)
        // Afresh even when it is the channel playing: the device joined tunes afresh now too
        WatchTogether.followChannel(channel, fresh = true)
        Joined.YES
    }

    fun joinInBackground(device: String, onFailed: (Joined) -> Unit = {}) {
        scope.launch {
            val joined = join(device)
            if (joined != Joined.YES && joined != Joined.ASKED) withContext(Dispatchers.Main) { onFailed(joined) }
            state()
        }
    }

    /** Allow or Decline another person's request to join this device. */
    fun answerInBackground(device: String, allow: Boolean) {
        scope.launch {
            val (playlistId, base, path) = target() ?: return@launch
            runCatching { auth.withApiKeyRetry(playlistId) { key -> client.answerLink(base, key, path, device, allow) } }
                .onFailure { Log.w(TAG, "answer to $device failed: ${it.message}") }
            state()
        }
    }

    /** Stay on this channel: the other person's device that changed it leaves. */
    fun stayInBackground(device: String) {
        scope.launch {
            val (playlistId, base, path) = target() ?: return@launch
            runCatching { auth.withApiKeyRetry(playlistId) { key -> client.stayInLink(base, key, path, device) } }
                .onFailure { Log.w(TAG, "stay failed: ${it.message}") }
            state()
        }
    }

    /** Disconnect: take another person's device out of the group this one leads. */
    fun removeInBackground(device: String) {
        scope.launch {
            val (playlistId, base, path) = target() ?: return@launch
            runCatching { auth.withApiKeyRetry(playlistId) { key -> client.removeFromLink(base, key, path, device) } }
                .onFailure { Log.w(TAG, "disconnect $device failed: ${it.message}") }
            state()
        }
    }

    /** Stop watching together (the Options menu, or a fresh start of the app). */
    fun leaveInBackground() {
        WatchTogether.setLinked(false)
        scope.launch {
            val (playlistId, base, path) = target() ?: return@launch
            runCatching { auth.withApiKeyRetry(playlistId) { key -> client.leaveLink(base, key, path) } }
                .onFailure { Log.w(TAG, "leave failed: ${it.message}") }
            state()
        }
    }

    private companion object {
        const val TAG = "WatchTogether"
        const val STEP_CHECK_MS = 10_000L
        const val STEP_SETTLE_MS = 20_000L
        // The Join list asked again this often while the app is in front
        const val STATUS_EVERY_MS = 20_000L
        // How long another person's answer is waited for (the server keeps the request 30 s)
        const val ASK_WAIT_MS = 25_000L
    }
}
