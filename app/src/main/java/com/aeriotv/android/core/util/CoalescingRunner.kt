package com.aeriotv.android.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [work] one at a time, and folds every request that arrives while it runs into ONE next
 * run: at most one running and one waiting, never a pile.
 *
 * Why (2026-10-20, Chromecast HD): the guide was rebuilt from about ten places -- launch,
 * download, enrichment, a channel update, a jump, back in the foreground -- and nothing stopped
 * two of them running at once; two full rebuilds of 56,000 rows ran side by side for 24-28 s
 * each, both holding the whole window in memory, while the heap sat at its limit. Each request
 * still gets a rebuild that started after it asked, so nothing it wrote is missed.
 *
 * The work runs in [scope], not in the caller: a caller that is cancelled (a scroll superseded
 * by another) stops waiting, but the run others are waiting on still finishes.
 */
class CoalescingRunner<T>(
    private val scope: CoroutineScope,
    private val work: suspend (reasons: List<String>, latest: T) -> Unit,
) {
    private val running = Mutex()
    private val monitor = Any()
    private var next: Deferred<Unit>? = null
    private val reasons = ArrayList<String>()
    private var latest: T? = null

    /** Ask for a run with [reason] and [argument] (the newest argument wins); returns once a
     *  run that started after this call has finished. */
    suspend fun request(reason: String, argument: T) {
        val run = synchronized(monitor) {
            reasons += reason
            latest = argument
            next ?: scope.async(start = CoroutineStart.LAZY) {
                running.withLock {
                    val (taken, value) = synchronized(monitor) {
                        // From here on a new request starts the run after this one
                        next = null
                        val taken = reasons.toList()
                        reasons.clear()
                        @Suppress("UNCHECKED_CAST")
                        taken to (latest as T)
                    }
                    work(taken, value)
                }
            }.also { next = it }
        }
        run.await()
    }
}
