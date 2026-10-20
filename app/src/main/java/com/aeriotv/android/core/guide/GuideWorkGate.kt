package com.aeriotv.android.core.guide

import android.util.Log
import com.aeriotv.android.core.playback.PlaybackActivityTracker
import kotlinx.coroutines.delay

/**
 * Heavy guide work waits for a calm moment: a full guide rebuild, the guide download, category
 * enrichment, the look-back sources and the live guide window wait while a channel is starting
 * (PlaybackActivityTracker.starting: from the tune until a few seconds after its first frame) and
 * while this app's Java heap is more than [TIGHT] full.
 *
 * Why (2026-10-20, Chromecast HD, 1.4 GB): every cold start ran the guide's rebuilds and
 * downloads right next to the first channel; the heap sat at its 256 MB limit, the runtime
 * stopped every thread to collect, and the picture froze for up to 2.4 s with 13 s buffered.
 * Waiting a few seconds costs the guide nothing anyone sees; the frozen picture is what they
 * see. Never longer than [MAX_WAIT_MS] in one go, so work cannot be held back for good.
 * Plan: fork/guide-loading-plan.md (server repo), step 3.
 */
object GuideWorkGate {
    private const val TAG = "GuideWorkGate"
    const val TIGHT = 0.70
    const val MAX_WAIT_MS = 60_000L
    private const val POLL_MS = 500L

    // What the gate looks at; tests replace them
    @Volatile internal var starting: () -> Boolean = { PlaybackActivityTracker.starting }
    @Volatile internal var heapUse: () -> Double = {
        val rt = Runtime.getRuntime()
        (rt.totalMemory() - rt.freeMemory()).toDouble() / rt.maxMemory()
    }
    @Volatile internal var clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    @Volatile internal var log: (String) -> Unit = { Log.i(TAG, it) }

    /** Why heavy work should wait right now, or null when it need not. */
    fun reason(): String? {
        if (starting()) return "a channel is starting"
        val use = heapUse()
        if (use > TIGHT) return "memory is tight (heap ${(use * 100).toInt()} % full)"
        return null
    }

    /** Waits until [reason] is null or [MAX_WAIT_MS] passed; [what] names the work in the log. */
    suspend fun awaitCalm(what: String) {
        var why = reason() ?: return
        val began = clock()
        log("$what waits: $why")
        while (true) {
            delay(POLL_MS)
            why = reason() ?: break
            if (clock() - began >= MAX_WAIT_MS) {
                log("$what goes ahead after ${(clock() - began) / 1000} s ($why)")
                return
            }
        }
        log("$what goes ahead after ${(clock() - began) / 1000.0} s")
    }
}
