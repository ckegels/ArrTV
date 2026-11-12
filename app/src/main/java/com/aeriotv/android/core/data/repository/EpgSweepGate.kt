package com.aeriotv.android.core.data.repository

import android.os.SystemClock

/**
 * Process-wide "is it a good moment to spend a background request?" gate for
 * the quiet EPG sweep in [PlaylistRepository.startEpgBackgroundSweep]
 * (Logan 2026-09-12).
 *
 * The sweep must be invisible: it pauses while the app is backgrounded and
 * while a tune is still waiting on its first frame, and resumes at the chunk
 * it stopped on. Two volatile flags polled by the sweep, deliberately not a
 * flow: nothing here should cost a subscription or a recomposition.
 */
object EpgSweepGate {
    /** Set from the app's ProcessLifecycleOwner (MainScaffold). Starts true so a
     *  cold launch (which is by definition foreground) never stalls the sweep
     *  waiting for an ON_START it already missed. */
    @Volatile
    var appInForeground: Boolean = true

    @Volatile
    private var tuneStartedAtMs: Long = 0L

    /** A tune has begun; the sweep holds off until its first frame. */
    fun onTuneStart() {
        tuneStartedAtMs = SystemClock.elapsedRealtime()
    }

    /** First frame (or STATE_READY) landed; the sweep may spend requests again. */
    fun onTunePlaying() {
        tuneStartedAtMs = 0L
    }

    /**
     * True while a tune is before its first frame. Ceilinged at
     * [TUNE_MAX_MS]: a missed [onTunePlaying] (a tune that failed, a screen
     * torn down mid-prime) must not wedge the sweep for the life of the
     * process.
     */
    val tuneInProgress: Boolean
        get() {
            val started = tuneStartedAtMs
            return started != 0L && SystemClock.elapsedRealtime() - started < TUNE_MAX_MS
        }

    /**
     * Set while Dispatcharr live updates are active; writers then store rows
     * in store order (PlaylistRepository.saveEpgToCache). The sweep used to
     * wait while something was watched; it now waits while the guide is on
     * screen instead ([guideOnScreen]). False (stock) when the setting is off.
     */
    @Volatile
    var holdWhileWatching: Boolean = false

    /**
     * True while the guide is on screen (its tab showing, not covered by the
     * fullscreen player). Guide updates are applied while the user watches,
     * not while they browse the guide: the sweep waits, and the ViewModel
     * saves updates but only swaps them into the guide once it leaves the
     * screen (PlaylistViewModel.setGuideOnScreen).
     */
    var guideOnScreen: Boolean
        get() = guideOnScreenFlow.value
        set(value) { guideOnScreenFlow.value = value }

    /** [guideOnScreen] as a flow, for work that waits for the guide to leave. */
    val guideOnScreenFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** The one question the sweep asks between chunks: the app is in front,
     *  no tune is starting, the guide is not being browsed and no multiview
     *  is running. Watching a single stream is when it works. */
    val sweepAllowed: Boolean
        get() = appInForeground && !tuneInProgress && !guideOnScreen &&
            !com.aeriotv.android.core.playback.PlaybackActivityTracker.isMultiStreamActive

    private const val TUNE_MAX_MS = 30_000L
}
