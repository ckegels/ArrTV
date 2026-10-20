package com.aeriotv.android.core.playback

/**
 * Where a look back seek lands: a whole minute of wall-clock time, which is the finest start a
 * provider's catch-up archive has (its URLs carry the minute only). Asking for 18:35:31 played
 * from 18:35 again, so a +30 s press from early in a minute played the same part once more, and
 * the next press "jumped" a minute (the Shield, 2026-10-16). A press now moves to the next
 * minute forward, or the previous one back; a held scrub several minutes at once.
 */
object CatchupMinuteStep {
    const val MINUTE_MS = 60_000L

    private fun minute(wallMs: Long): Long = Math.floorDiv(wallMs, MINUTE_MS) * MINUTE_MS

    /**
     * The programme-relative position to play from for a seek to [targetMs] while at
     * [positionMs] (both relative to [programmeStartMs]), within [0, [durationMs]): at least
     * the next whole minute forward, or the previous one back, and further when the target is.
     */
    fun snap(programmeStartMs: Long, positionMs: Long, targetMs: Long, durationMs: Long): Long {
        val nowWall = programmeStartMs + positionMs
        val targetWall = programmeStartMs + targetMs
        val wall = if (targetMs >= positionMs) {
            maxOf(minute(targetWall), minute(nowWall) + MINUTE_MS)
        } else {
            minOf(minute(targetWall), minute(nowWall) - MINUTE_MS)
        }
        val last = (durationMs - 5_000L).coerceAtLeast(0L)
        return (wall - programmeStartMs).coerceIn(0L, last)
    }
}
