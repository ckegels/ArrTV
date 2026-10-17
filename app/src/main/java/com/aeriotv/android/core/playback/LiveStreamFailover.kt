package com.aeriotv.android.core.playback

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Client-driven stream failover for live tunes (Apple parity: TSHLSRemuxer
 * firstByteDeadline / handleNoFirstByte / stepFailover, commit dc52f2a).
 *
 * A Dispatcharr ingest can CONNECT and then stay silent: the server's own health
 * checks cannot fail a connected-but-silent stream over for roughly 75 s
 * (60 s channel_init_grace_period plus three checks at 5 s), and the holder's
 * live no-data ceiling is 50 s. So the client arms its own silent-start
 * deadline per live ingest (deliberately SEPARATE from any HTTP timeout) and, on
 * a Dispatcharr Direct Connect admin account, walks the channel's member streams
 * with change_stream instead of waiting.
 *
 * The deadline measures SILENCE, never elapsed time: while bytes keep arriving,
 * however slowly, the stream is never abandoned. The first ingest of a tune gets
 * a long budget (an over-the-air tuner has to lock before it can send anything)
 * stretched further on a channel this device has LEARNED to start slowly; every
 * later ingest of the same tune gets the short, responsive budget. A definitive
 * server answer (503 with a reason, or a hard player error) still walks at once.
 *
 * The ExoPlayer connection is KEPT OPEN across a step: Dispatcharr swaps the
 * upstream in place behind the same /proxy/ts/stream/<uuid> URL, so re-priming
 * here would only drop the one connection the channel has and cold-resolve back
 * to the channel's default stream.
 *
 * Non-admin / Xtream / M3U / single-stream channels get the "Reconnecting..."
 * status at the deadline and nothing else: their existing retry ladder
 * (AerioExoPlayerHolder's no-data net plus the Task #150 unavailable overlay) is
 * untouched.
 *
 * Owned by [AerioExoPlayerHolder]; never driven from a composable.
 */
class LiveStreamFailover(
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) {

    /**
     * Server access, wired by PlayerScreen (the same shape as the holder's
     * onTerminalErrorRebuildUrl hook: the channel identity arrives as a
     * PARAMETER, never captured, so a channel flip can never point the walk at
     * the previous channel).
     */
    data class Hooks(
        /** Dispatcharr Direct Connect + admin + an integer channel pk. */
        val canSwitch: (channelId: String) -> Boolean,
        /** GET .../channels/<pk>/streams/ mapped to stream pks, priority order. */
        val listStreamIds: suspend (channelId: String) -> List<Int>,
        /** GET /proxy/ts/status/<uuid> stream_id; trusted only to seed the walk. */
        val currentStreamId: suspend (channelUuid: String) -> Int?,
        /** POST /proxy/ts/change_stream/<uuid>; throws when the server refuses. */
        val changeStream: suspend (channelUuid: String, streamId: Int) -> Unit,
    )

    @Volatile var hooks: Hooks? = null

    /** A step was refused with 409 (a stream of its own): the holder reopens
     *  the connection instead of walking. */
    @Volatile var onOwnStream: (() -> Unit)? = null

    /** Called when every member stream has been walked without a byte; the
     *  holder hands over to its standing retry / unavailable ladder. */
    @Volatile var onExhausted: (() -> Unit)? = null

    private val _statusText = MutableStateFlow<String?>(null)

    /** Live loading status for the player overlay ("Trying another stream...",
     *  "Reconnecting...", "Channel unavailable. Retrying..."), null otherwise. */
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    /** This channel's learned time-to-first-byte in ms, or null when nothing is
     *  known (or the learned value has decayed). Wired by the holder from the
     *  persisted learner; the channel identity arrives as a PARAMETER so a
     *  channel flip can never read the previous channel's value. */
    @Volatile var learnedFirstByteMs: ((channelId: String) -> Int?)? = null

    /** A clean, un-stepped tune produced its first byte after this long; the
     *  holder persists it. Only successes are learned. */
    @Volatile var onLearnFirstByte: ((channelId: String, ms: Int) -> Unit)? = null

    private var deadlineJob: Job? = null

    /** The picture deadline (arrTV optimization "leave a stream that is slow
     *  to start"): no first frame within the budget walks to the next stream,
     *  whatever the bytes are doing. */
    private var pictureJob: Job? = null
    @Volatile private var firstFrameSeen = false

    /** The server's own faster failover is on for the stream being played
     *  (capabilities, Dispatch More v212): it leaves a silent stream after
     *  5 s and one check, so the app waits longer and lets it move first --
     *  both moving at once would skip a stream. Set by the holder. */
    @Volatile var serverFastFailover: () -> Boolean = { false }

    /** Whether a picture is to be expected at all: false for Audio Only and
     *  for a stream found to carry no video (radio), which never render a
     *  frame and must not be walked for it. Set by the holder; main thread. */
    @Volatile var pictureExpected: () -> Boolean = { true }

    /** How many other streams the server counted for this channel's stream
     *  (X-Dispatch-Alternatives), null when it said nothing. Set by the holder. */
    @Volatile var alternatives: () -> Int? = { null }

    /**
     * The picture is playing now: the player plays and has rendered frames.
     * The picture deadline is met by this as well as by a first frame: after
     * a stream swapped inside the open connection the player reports no new
     * first frame, and a deadline that waited for one walked on from streams
     * that played (Chromecast HD, AL JAZEERA: seven switches in a row). And a
     * step checks it once more right before asking for the next stream: a
     * picture that came while the step got ready is never switched away.
     * Set by the holder; main thread.
     */
    @Volatile var picturePlaying: () -> Boolean = { false }
    private var stepJob: Job? = null
    private var channelId: String? = null
    private var channelName: String = "?"
    private var firstByteSeen = false

    /** The walk was started by [onUnplayable]: bytes are arriving, so a first
     *  byte having been seen does not end it. */
    @Volatile private var unplayableWalk = false

    /**
     * Last moment ANY byte was seen on this pipeline (0 = none since arm).
     * Written from loader threads, so volatile and free of any coroutine work.
     * The deadline measures SILENCE from here: a slow but live feed can never
     * trigger the walk (Glitzbr 2026-09-15, an upstream that degraded to 0.68
     * of real time was walked away from onto worse backups).
     */
    @Volatile private var lastByteAtMs = 0L

    /** When the CURRENT deadline was armed. */
    @Volatile private var armedAtMs = 0L

    /** When this tune first armed a deadline, so the learned value measures the
     *  whole tap-to-first-byte, not just the last step. */
    private var tuneStartedAtMs = 0L

    /** The budget the current deadline is running with, for the log line. */
    private var budgetMs = FIRST_TUNE_FIRST_BYTE_MS
    private var pictureBudgetMs = 0L
    /** Streams already walked this tune; the walk never revisits one. */
    private val tried = mutableSetOf<Int>()
    private var activeStreamId: Int? = null
    private var steps = 0
    private var walkStartedAtMs = 0L
    /** The server's verbatim 503 reason for this tune, so the walk's own status
     *  lines can keep quoting it instead of inventing a cause. */
    private var serverReason: String? = null

    /**
     * A live stream has just been primed. [userInitiated] is true for a real
     * channel change (the holder's playUrl was given a channel id), which wipes
     * the walk; an internal re-prime keeps the tried set so a fresh pipeline on
     * streams already proved silent cannot loop.
     */
    fun onLiveTune(channelId: String?, channelName: String?, userInitiated: Boolean) {
        if (channelId == null) {
            // A tune with no channel to walk: forget the previous channel's,
            // so nothing walks this one on its behalf.
            resetWalk()
            this.channelId = null
            return
        }
        if (userInitiated || channelId != this.channelId) resetWalk()
        this.channelId = channelId
        channelName?.takeIf { it.isNotBlank() }?.let { this.channelName = it }
        _statusText.value = null
        armDeadline(firstAttempt = true)
    }

    /**
     * ANY byte arrived on the live pipeline. Called from loader threads at most
     * a few times a second (PlaybackTracer throttles), so it does exactly one
     * volatile write and nothing else. Bytes NEVER get abandoned: the deadline
     * counts silence from the last byte, so a feed that is crawling at a
     * fraction of real time keeps the stream alive instead of starting a walk.
     */
    fun noteBytes() {
        lastByteAtMs = SystemClock.elapsedRealtime()
    }

    /** First byte on the wire: disarm, and say what recovered us if we stepped. */
    fun noteFirstByte() {
        lastByteAtMs = SystemClock.elapsedRealtime()
        scope.launch {
            if (firstByteSeen) return@launch
            firstByteSeen = true
            deadlineJob?.cancel()
            deadlineJob = null
            // Learn only clean successes on this channel's own stream: a time
            // measured after a change_stream walk is the backup's, not the
            // channel's normal lock time.
            val id = channelId
            if (steps == 0 && id != null && tuneStartedAtMs != 0L) {
                val ttfb = (SystemClock.elapsedRealtime() - tuneStartedAtMs).toInt()
                if (ttfb > 0) {
                    Log.i(TAG, "[FAILOVER] channel=$channelName firstByte in ${ttfb}ms; learning")
                    onLearnFirstByte?.invoke(id, ttfb)
                }
            }
            if (steps > 0) {
                val ms = if (walkStartedAtMs == 0L) 0L else SystemClock.elapsedRealtime() - walkStartedAtMs
                Log.i(
                    TAG,
                    "[FAILOVER] channel=$channelName recovered on stream " +
                        "id=${activeStreamId ?: "unknown"} after ${ms}ms",
                )
            }
            _statusText.value = null
        }
    }

    /**
     * The server answered a live tune with a 503 it already explained (see
     * [Dispatcharr503]): "No available streams for this channel", "Channel
     * resources unavailable", or a specific upstream error_reason. The server has
     * already tried this channel's streams, so there is nothing to wait for:
     * start the SAME walk the first-byte deadline uses, right now.
     *
     * [reason] is the server's own text, shown verbatim so the user never sees a
     * guessed cause.
     */
    fun onServer503(reason: String) {
        scope.launch {
            val id = channelId ?: return@launch
            val h = hooks
            serverReason = reason
            // The deadline is moot: we have a definitive answer already.
            deadlineJob?.cancel()
            deadlineJob = null
            if (h == null || !h.canSwitch(id)) {
                Log.i(TAG, "[FAILOVER] 503 reason=\"$reason\" -> standing retry")
                _statusText.value = "Channel unavailable. Retrying..."
                return@launch
            }
            Log.i(TAG, "[FAILOVER] 503 reason=\"$reason\" -> next stream")
            _statusText.value = "Server: $reason. Trying another stream..."
            if (stepJob?.isActive == true) return@launch
            if (walkStartedAtMs == 0L) walkStartedAtMs = SystemClock.elapsedRealtime()
            stepJob = scope.launch { step(h, id) }
        }
    }

    /**
     * Bytes have been arriving on this ingest for a while and nothing has
     * become playable: a stream this device cannot decode (a 4K stream on a
     * 1080p Chromecast HD). The server sees a healthy stream and never fails
     * it over, and a reconnect gets the same stream again, so walk to the next
     * one (the same walk the silent-start deadline uses). Called by the
     * holder's watchdog; see [bytesFlowingForMs].
     */
    fun onUnplayable(detail: String) {
        scope.launch {
            val id = channelId ?: return@launch
            val h = hooks
            if (h == null || !h.canSwitch(id)) {
                Log.i(TAG, "[FAILOVER] channel=$channelName $detail; no switchable streams")
                return@launch
            }
            if (stepJob?.isActive == true) return@launch
            unplayableWalk = true
            serverReason = null
            Log.i(TAG, "[FAILOVER] channel=$channelName $detail; walking to the next stream")
            if (walkStartedAtMs == 0L) walkStartedAtMs = SystemClock.elapsedRealtime()
            stepJob = scope.launch { step(h, id) }
        }
    }

    /** How long bytes have been arriving on the current ingest (since it was
     *  armed, when a byte came after that); 0 when none has. */
    fun bytesFlowingForMs(): Long {
        val armed = armedAtMs
        if (armed == 0L || lastByteAtMs <= armed) return 0L
        return SystemClock.elapsedRealtime() - armed
    }

    /** Time since the last byte on the wire (any ingest of this tune);
     *  Long.MAX_VALUE when none has come since the deadline was armed. */
    fun msSinceLastByte(): Long {
        val last = lastByteAtMs
        return if (last == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - last
    }

    /** A step is in flight (the holder does not stack another). */
    val stepping: Boolean get() = stepJob?.isActive == true

    /** Publish a status line the holder owns (for example the "Reconnecting..."
     *  shown while a "Channel is stopping" 503 is waited out). */
    fun publishServerStatus(text: String?) {
        scope.launch { _statusText.value = text }
    }

    /** Teardown / pipeline stop: cancel the deadline, KEEP the tried set. */
    fun disarm() {
        deadlineJob?.cancel()
        deadlineJob = null
        firstByteSeen = false
        // Nothing is armed, so no bytes are "flowing on this ingest": a stale
        // arm time made a tune that armed nothing look minutes old to the
        // unplayable rule, which walked a channel a second after it opened.
        armedAtMs = 0L
        pictureJob?.cancel()
        pictureJob = null
    }

    /** Channel change or full teardown: forget everything about the walk. */
    fun resetWalk() {
        disarm()
        stepJob?.cancel()
        stepJob = null
        tried.clear()
        activeStreamId = null
        steps = 0
        walkStartedAtMs = 0L
        serverReason = null
        unplayableWalk = false
        _statusText.value = null
    }

    /**
     * Arm the silent-start deadline.
     *
     * [firstAttempt] is the first ingest of this tune: an over-the-air tuner has
     * to LOCK before it can send anything, so it gets the long budget (and a
     * longer one still on a channel this device has learned to be slow). Every
     * later attempt is a stream the server has already swapped in behind the
     * same connection, so it gets the short, responsive budget.
     *
     * The wait measures SILENCE, not wall clock: each poll restarts the budget
     * from the last byte seen.
     */
    private fun armDeadline(firstAttempt: Boolean) {
        firstByteSeen = false
        lastByteAtMs = 0L
        armedAtMs = SystemClock.elapsedRealtime()
        if (firstAttempt) tuneStartedAtMs = armedAtMs
        val id = channelId
        val learned = id?.let { cid -> runCatching { learnedFirstByteMs?.invoke(cid) }.getOrNull() }
        // arrTV optimization "faster failover": IPTV answers within a second or
        // two (learned first-byte times here are 0.1-3 s), so the budgets sized
        // for an antenna tuner's lock cost 28 s per dead stream for nothing.
        val fast = ArrTvOptimizations.fastFailover
        val serverLeads = fast && runCatching { serverFastFailover() }.getOrDefault(false)
        budgetMs = if (!firstAttempt) {
            if (fast) FAST_STEP_FIRST_BYTE_MS else STEP_FIRST_BYTE_MS
        } else {
            val fromLearned = learned?.let { (it * LEARNED_HEADROOM_NUM / LEARNED_HEADROOM_DEN).toLong() } ?: 0L
            if (fast) {
                maxOf(FAST_FIRST_TUNE_FIRST_BYTE_MS, fromLearned).coerceAtMost(FAST_FIRST_BYTE_BUDGET_MAX_MS)
            } else {
                maxOf(FIRST_TUNE_FIRST_BYTE_MS, fromLearned).coerceAtMost(FIRST_BYTE_BUDGET_MAX_MS)
            }
        }
        if (serverLeads) budgetMs = maxOf(budgetMs, SERVER_LEADS_MS)
        armPictureDeadline(serverLeads)
        Log.i(
            TAG,
            "[FAILOVER] channel=$channelName silent-start budget ${budgetMs}ms " +
                "(learned ${learned?.let { "${it}ms" } ?: "none"}, " +
                "${if (firstAttempt) "first attempt" else "walk step"})",
        )
        deadlineJob?.cancel()
        deadlineJob = scope.launch {
            while (true) {
                delay(SILENCE_POLL_MS)
                if (firstByteSeen) return@launch
                val now = SystemClock.elapsedRealtime()
                val since = now - maxOf(armedAtMs, lastByteAtMs)
                if (since >= budgetMs) {
                    handleNoFirstByte(since)
                    return@launch
                }
            }
        }
    }

    /**
     * No first picture in time: the stream is too slow to start (dead, slow to
     * connect, or trickling in), whatever its bytes are doing, and the channel
     * walks to its next stream. How long is "in time" follows how many other
     * streams the server says the channel could switch to now
     * (ArrTvOptimizations.pictureWaitMs, editable in Settings): less where
     * there are several, more where one is left, and no walk at all where
     * there is none. Scaled by the channel's usual start time this session,
     * so a channel that is always a little slow is not left every time.
     */
    private fun armPictureDeadline(serverLeads: Boolean) {
        firstFrameSeen = false
        pictureJob?.cancel()
        pictureJob = null
        if (!ArrTvOptimizations.slowStart) return
        val id = channelId ?: return
        val armedAt = SystemClock.elapsedRealtime()
        pictureJob = scope.launch {
            // Re-read every poll: the server's count arrives with the stream's
            // response, a moment after the tune.
            while (true) {
                delay(PICTURE_POLL_MS)
                if (firstFrameSeen || channelId != id) return@launch
                if (runCatching { picturePlaying() }.getOrDefault(false)) return@launch
                // What the server counted for the tune, less the streams this walk left
                val counted = runCatching { alternatives() }.getOrNull()
                val left = counted?.let { (it - steps).coerceAtLeast(0) }
                // The channel's start time this session, else an estimate from its
                // learned first byte (kept across launches): right after a launch
                // the bare minimum alone left channels that start in 3-4 s.
                val usual = learnedFirstFrame[id]
                    ?: runCatching { learnedFirstByteMs?.invoke(id) }.getOrNull()?.let { it + FIRST_BYTE_TO_FRAME_MS }
                val budget = ArrTvOptimizations.pictureWaitMs(usual, left)
                pictureBudgetMs = budget
                val waited = SystemClock.elapsedRealtime() - armedAt
                if (waited < budget) continue
                // Nothing to go to: keep trying this stream (the other nets stay)
                if (left == 0) return@launch
                if (!runCatching { pictureExpected() }.getOrDefault(true)) return@launch
                // A silent stream is the server's to leave when its own faster
                // failover is on; this deadline takes streams whose data arrives
                // but gives no picture, so the two never move at once.
                if (serverLeads && lastByteAtMs <= armedAt) {
                    if (waited < SERVER_LEADS_MS) continue
                }
                val h = hooks
                if (h == null || !h.canSwitch(id)) {
                    Log.i(TAG, "[FAILOVER] channel=$channelName no picture after ${waited}ms; no switchable streams")
                    return@launch
                }
                if (stepJob?.isActive == true) return@launch
                // Bytes may well be arriving: this walk is not ended by them.
                unplayableWalk = true
                serverReason = null
                Log.i(
                    TAG,
                    "[FAILOVER] channel=$channelName no picture after ${waited}ms " +
                        "(wait ${budget}ms, ${left ?: "unknown"} other streams); walking to the next stream",
                )
                if (walkStartedAtMs == 0L) walkStartedAtMs = SystemClock.elapsedRealtime()
                stepJob = scope.launch { step(h, id) }
                return@launch
            }
        }
    }

    /** The first frame of this ingest rendered: the picture deadline is met.
     *  A clean start (no walk) teaches the channel's start time. */
    fun noteFirstFrame() {
        scope.launch {
            if (firstFrameSeen) return@launch
            firstFrameSeen = true
            pictureJob?.cancel()
            pictureJob = null
            val id = channelId
            if (steps == 0 && id != null && tuneStartedAtMs != 0L) {
                learnedFirstFrame[id] = SystemClock.elapsedRealtime() - tuneStartedAtMs
            }
            // A picture ends the walk. A stream the server swapped in behind
            // the open connection sends no new "first byte" (the bytes never
            // stopped), so waiting for one left "Trying another stream..."
            // on screen over a playing channel for good.
            if (!firstByteSeen) {
                firstByteSeen = true
                deadlineJob?.cancel()
                deadlineJob = null
            }
            unplayableWalk = false
            if (steps > 0) {
                Log.i(TAG, "[FAILOVER] channel=$channelName picture on stream id=${activeStreamId ?: "unknown"}")
            }
            _statusText.value = null
        }
    }

    private fun handleNoFirstByte(silentMs: Long) {
        val id = channelId ?: return
        val h = hooks
        if (h == null || !h.canSwitch(id)) {
            // Nothing to fail over TO: say the player is working on it and leave
            // the existing retry ladder exactly as it is.
            _statusText.value = "Reconnecting..."
            Log.i(
                TAG,
                "[FAILOVER] channel=$channelName no bytes for ${silentMs}ms " +
                    "(budget ${budgetMs}ms); " +
                    "no switchable streams, staying on the retry path",
            )
            return
        }
        if (stepJob?.isActive == true) return
        if (walkStartedAtMs == 0L) walkStartedAtMs = SystemClock.elapsedRealtime()
        Log.i(
            TAG,
            "[FAILOVER] channel=$channelName no bytes for ${silentMs}ms " +
                "(budget ${budgetMs}ms); walking to the next stream",
        )
        stepJob = scope.launch { step(h, id) }
    }

    /**
     * One step of the walk: resolve the list (cached per channel for the
     * process), mark where we are, POST change_stream for the next untried
     * entry, re-arm the deadline.
     */
    private suspend fun step(h: Hooks, id: String) {
        val uuid = id.substringAfterLast(':')
        // Read fresh at the start of every walk: a stream added on the server
        // (for this device, a 1080p one next to a 4K one) is tried at once,
        // not after the app restarts. Later steps of the same walk reuse it.
        val cached = if (steps == 0) null else streamCache[id]
        val ids = cached ?: runCatching { h.listStreamIds(id) }.getOrNull()
            ?.also { if (it.isNotEmpty()) streamCache[id] = it }
        if (ids.isNullOrEmpty()) {
            _statusText.value = "Reconnecting..."
            Log.i(TAG, "[FAILOVER] channel=$channelName stream list unavailable; staying on the retry path")
            return
        }
        if ((firstByteSeen && !unplayableWalk) || channelId != id) return
        if (ids.size < 2) {
            _statusText.value = "Reconnecting..."
            Log.i(TAG, "[FAILOVER] channel=$channelName single stream; staying on the retry path")
            return
        }
        // Seed "where are we" once. /status is only trustworthy before any
        // in-session switch, which is exactly where the walk reads it.
        if (activeStreamId == null) {
            activeStreamId = withTimeoutOrNull(STATUS_READ_CAP_MS) {
                runCatching { h.currentStreamId(uuid) }.getOrNull()
            } ?: ids.first()
        }
        activeStreamId?.let { tried.add(it) }
        val startIndex = activeStreamId?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        var target: Int? = null
        for (offset in 1..ids.size) {
            val candidate = ids[(startIndex + offset) % ids.size]
            if (candidate !in tried) { target = candidate; break }
        }
        if (target == null) {
            Log.w(TAG, "[FAILOVER] channel=$channelName exhausted ${ids.size} streams")
            _statusText.value = "Channel unavailable. Retrying..."
            onExhausted?.invoke()
            return
        }
        // The last moment to find the picture came after all: never switch a
        // playing stream away (the walk would then run through every stream).
        if (unplayableWalk && runCatching { picturePlaying() }.getOrDefault(false)) {
            Log.i(TAG, "[FAILOVER] channel=$channelName the picture came; not switching")
            _statusText.value = null
            return
        }
        steps += 1
        tried.add(target)
        val step = steps
        try {
            h.changeStream(uuid, target)
        } catch (t: Throwable) {
            Log.w(TAG, "[FAILOVER] channel=$channelName change_stream to id=$target failed: ${t.message}")
            _statusText.value = "Reconnecting..."
            // Contract 8.3: 409 = this device plays the channel on a stream of
            // its own, which the server will not change under the others. Any
            // other step is refused the same way, so stop walking; a fresh
            // connection gets the device a stream chosen afresh.
            if (t.message?.contains("HTTP 409") == true) {
                Log.i(TAG, "[FAILOVER] channel=$channelName on a stream of its own (409); reopening instead")
                onOwnStream?.invoke()
            }
            return
        }
        if ((firstByteSeen && !unplayableWalk) || channelId != id) return
        activeStreamId = target
        // Quote the server when it told us why we are moving; otherwise this was
        // our own first-byte deadline and there is no server text to show.
        _statusText.value = serverReason
            ?.let { "Server: $it. Trying another stream..." }
            ?: "Trying another stream..."
        Log.i(
            TAG,
            "[FAILOVER] channel=$channelName stream $step/${ids.size} id=$target " +
                "reason=${serverReason ?: if (unplayableWalk) "no picture (bytes but nothing playable, or too slow to start)" else "no bytes within the ${budgetMs}ms silent-start budget"}",
        )
        armDeadline(firstAttempt = false)
    }

    companion object {
        private const val TAG = "AerioTrace"

        /**
         * How long a live ingest may stay connected-and-SILENT on the FIRST
         * attempt of a tune before the walk starts. Separate from the holder's
         * HTTP read timeout.
         *
         * Was 12 s. Raised 2026-09-15 (Glitzbr, over-the-air HDHomeRun tuners
         * through Dispatcharr): a tuner has to lock before a single byte
         * exists, and 12 s walked a working channel onto much worse backups.
         */
        const val FIRST_TUNE_FIRST_BYTE_MS = 28_000L

        /**
         * Silence budget for every ingest AFTER the first of a tune. The server
         * has already swapped a stream in behind the same connection, so there
         * is no tuner lock left to wait for and failover stays responsive.
         */
        const val STEP_FIRST_BYTE_MS = 12_000L

        /** Hard ceiling on a learned-stretched budget. */
        const val FIRST_BYTE_BUDGET_MAX_MS = 45_000L

        /** With "faster failover" on: the first attempt waits 6 s (or 1.5x the
         *  channel's learned first byte, at most 12 s), every later step 5 s. */
        const val FAST_FIRST_TUNE_FIRST_BYTE_MS = 6_000L
        const val FAST_FIRST_BYTE_BUDGET_MAX_MS = 12_000L
        const val FAST_STEP_FIRST_BYTE_MS = 5_000L

        /** How often the picture deadline re-reads its wait. */
        private const val PICTURE_POLL_MS = 250L

        /** From first byte to first picture, where the channel's own first
         *  picture is not known yet: the start gate plus a keyframe. */
        private const val FIRST_BYTE_TO_FRAME_MS = 1_500L

        /** With the server's own faster failover on, the app's first moves wait
         *  at least this long (the server leaves a silent stream after 5 s and a
         *  check, 5-10 s). */
        const val SERVER_LEADS_MS = 12_000L

        /** How long each channel took to its first picture on a clean start
         *  this session, for the picture deadline. */
        private val learnedFirstFrame = ConcurrentHashMap<String, Long>()

        /** A channel learned to start slowly gets 1.5x its learned
         *  time-to-first-byte (never less than the base budget). */
        private const val LEARNED_HEADROOM_NUM = 3
        private const val LEARNED_HEADROOM_DEN = 2

        /** How often the deadline re-checks silence. */
        private const val SILENCE_POLL_MS = 500L
        private const val STATUS_READ_CAP_MS = 3_000L

        /** A channel's member-stream pks (priority order) for the steps of one
         *  walk; refreshed when a walk starts. An empty answer is never cached. */
        private val streamCache = ConcurrentHashMap<String, List<Int>>()
    }
}
