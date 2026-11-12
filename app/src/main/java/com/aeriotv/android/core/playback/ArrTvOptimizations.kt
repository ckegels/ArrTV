package com.aeriotv.android.core.playback

/**
 * The switches for arrTV's own playback and server optimizations (Settings >
 * General > arrTV optimizations). Each one on by default; switched off, the
 * part it guards does nothing and the app behaves as it did before it existed,
 * so any of them can be ruled out when something goes wrong.
 *
 * Plain volatile flags: read on hot paths (every request, every watchdog
 * tick), written by the collectors in AerioTVApplication from AppPreferences.
 */
object ArrTvOptimizations {
    /** Tell a Dispatch More server which device this is: the device headers,
     *  the channel being left, the Multiview session. Off: no header at all,
     *  and nothing below that needs one (decode limit, stutter report). */
    @Volatile var identifyDevice = true

    /** Send the tallest picture this device decodes (X-Dispatch-Max-Video),
     *  so the server never starts it on a stream it cannot play. */
    @Volatile var sendMaxVideo = true

    /** Reopen the stream when the picture froze after its first frame while
     *  data kept arriving (a stream swap inside the connection). */
    @Volatile var freezeRescue = true

    /** Short waits before trying a channel's next stream (IPTV answers in
     *  about a second); off: the long waits sized for antenna tuners. */
    @Volatile var fastFailover = true

    /** Walk to the next stream when no first picture came within the wait
     *  (pictureWaitMs): a dead, slow-to-connect or trickling stream, whatever
     *  its bytes are doing. */
    @Volatile var slowStart = true

    /** Scale that wait by how many other streams the channel could switch to
     *  (the server's X-Dispatch-Alternatives): less where there are several,
     *  more where one is left, none where there is nothing to go to. Off: one
     *  wait for every channel, the "one other stream" one. */
    @Volatile var adaptiveWait = true

    /** The waits, in seconds (Settings, editable): at least this long with
     *  two or more / one other stream, and never longer than max. Two tiers:
     *  a channel here has at most three streams, so at most two others. */
    @Volatile var waitManySecs = 3
    @Volatile var waitOneSecs = 5
    @Volatile var waitMaxSecs = 10

    /**
     * How long to wait for a first picture before walking on: the channel's
     * usual start time times a factor, at least the tier's own minimum, never
     * past the maximum. [alternatives] null = not known (counted as one);
     * 0 = nothing to go to, which the caller does not walk for.
     */
    fun pictureWaitMs(learnedFirstFrameMs: Long?, alternatives: Int?): Long {
        val left = if (adaptiveWait) (alternatives ?: 1) else 1
        val (factor, minSecs) = when {
            left >= 2 -> 1.5 to waitManySecs
            else -> 2.0 to waitOneSecs
        }
        val fromLearned = ((learnedFirstFrameMs ?: 0L) * factor).toLong()
        return maxOf(minSecs * 1000L, fromLearned).coerceAtMost(maxOf(waitMaxSecs, minSecs) * 1000L)
    }

    /** Walk to the next stream when data arrives and nothing becomes
     *  playable (a stream this device cannot decode). */
    @Volatile var skipUnplayable = true

    /** Tell the server the moment the picture stalls, so it can move the
     *  channel to its next stream (server switch "Change stream when arrTV
     *  stutters"). */
    @Volatile var reportStalls = true
}

/**
 * The rows of Settings > General > arrTV optimizations, in order: the stored
 * key (default on) and what switching it sets. Adding an optimization is one
 * entry here plus the flag above.
 */
enum class ArrTvOptimization(
    val prefKey: String,
    val title: String,
    val subtitle: String,
    val apply: (Boolean) -> Unit,
) {
    IDENTIFY_DEVICE(
        "arrtv_opt_identify_device",
        "Tell Dispatch More which TV this is",
        "Device id and name, the channel being left, the Multiview session. Off: nothing below that needs it works.",
        { ArrTvOptimizations.identifyDevice = it },
    ),
    SEND_MAX_VIDEO(
        "arrtv_opt_send_max_video",
        "Only start streams this TV can play",
        "Tells the server the tallest picture this TV decodes, so it never starts it on a 4K stream it cannot play.",
        { ArrTvOptimizations.sendMaxVideo = it },
    ),
    FAST_FAILOVER(
        "arrtv_opt_fast_failover",
        "Faster switch to the next stream",
        "Waits about 6 s instead of 28 s for a stream that sends nothing, and 6 s instead of 15 s for one it cannot play.",
        { ArrTvOptimizations.fastFailover = it },
    ),
    SLOW_START(
        "arrtv_opt_slow_start",
        "Leave a stream that is slow to start",
        "No picture within the wait below: the channel's next stream is tried. A stream that sends nothing at all " +
            "is left to the server when its own faster failover is on.",
        { ArrTvOptimizations.slowStart = it },
    ),
    ADAPTIVE_WAIT(
        "arrtv_opt_adaptive_wait",
        "Wait less when a channel has more streams",
        "The wait for a picture follows how many other streams the server can switch to right now " +
            "(its \"Tell arrTV how many other streams a channel has\" switch). Off: the \"one other stream\" wait everywhere.",
        { ArrTvOptimizations.adaptiveWait = it },
    ),
    SKIP_UNPLAYABLE(
        "arrtv_opt_skip_unplayable",
        "Skip streams this TV cannot play",
        "When data arrives but no picture comes, asks the server for the channel's next stream.",
        { ArrTvOptimizations.skipUnplayable = it },
    ),
    FREEZE_RESCUE(
        "arrtv_opt_freeze_rescue",
        "Reopen a frozen stream",
        "When the picture stops after it started while data keeps arriving, opens the channel again on a new connection.",
        { ArrTvOptimizations.freezeRescue = it },
    ),
    REPORT_STALLS(
        "arrtv_opt_report_stalls",
        "Tell the server when the picture stutters",
        "So Dispatch More can move the channel to another stream at once (its own switch has to be on too).",
        { ArrTvOptimizations.reportStalls = it },
    ),
}

/** The editable waits of Settings > General > arrTV optimizations, in seconds. */
enum class ArrTvWait(
    val prefKey: String,
    val title: String,
    val default: Int,
    val range: IntRange,
    val apply: (Int) -> Unit,
) {
    MANY("arrtv_wait_many", "Wait with 2 or more other streams", 3, 1..20, { ArrTvOptimizations.waitManySecs = it }),
    ONE("arrtv_wait_one", "Wait with 1 other stream", 5, 1..20, { ArrTvOptimizations.waitOneSecs = it }),
    MAX("arrtv_wait_max", "Longest wait for a slow channel", 10, 2..30, { ArrTvOptimizations.waitMaxSecs = it }),
}
