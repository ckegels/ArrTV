package com.aeriotv.android.core.guide

import com.aeriotv.android.core.data.EPGProgramme

/**
 * Which channels' schedules in [fromMs, toMs) differ between the guide on
 * screen and a freshly fetched window, both as built cells (see
 * GuideCatalog.changedChannels, which builds the fresh side first), so a
 * server EPG refresh can write and
 * repaint only those channels instead of rewriting and rebuilding the whole
 * guide (a ~20 s rebuild plus a burst of database writes on a Shield, enough
 * to underrun a playing stream's audio).
 *
 * A programme counts when it STARTS inside the window: rows straddling the
 * window start are cut differently by different fetches and would read as
 * spurious changes. Compared on start, end, title and subtitle; a
 * description-only edit is left to the periodic full sweep.
 *
 * Only channels present in [fresh] are judged. A channel the fetch returned
 * nothing for is not reported: a window merge cannot express "delete these",
 * and an empty answer is more often a feed hiccup than a cleared schedule.
 */
object GuideWindowDiff {
    fun changedChannels(
        current: Map<String, List<EPGProgramme>>,
        fresh: Map<String, List<EPGProgramme>>,
        fromMs: Long,
        toMs: Long,
    ): Set<String> {
        val changed = HashSet<String>()
        for ((channelId, programmes) in fresh) {
            val before = signatures(current[channelId].orEmpty(), fromMs, toMs)
            val after = signatures(programmes, fromMs, toMs)
            if (after.isNotEmpty() && before != after) changed.add(channelId)
        }
        return changed
    }

    /**
     * Why [channelId] reads as changed: up to [limit] signatures only on each
     * side, for the log. Programme times and titles only.
     */
    fun explain(
        current: List<EPGProgramme>,
        fresh: List<EPGProgramme>,
        fromMs: Long,
        toMs: Long,
        limit: Int = 3,
    ): String {
        val before = signatures(current, fromMs, toMs)
        val after = signatures(fresh, fromMs, toMs)
        fun fmt(sigs: Set<List<Any?>>) = sigs.sortedBy { it[0] as Long }.take(limit).joinToString { sig ->
            val start = java.time.Instant.ofEpochMilli(sig[0] as Long)
            val end = java.time.Instant.ofEpochMilli(sig[1] as Long)
            "$start..$end '${sig[2]}'${sig[3]?.let { " / '$it'" } ?: ""}"
        }
        return "screen ${before.size}, fresh ${after.size}; only on screen [${fmt(before - after)}]; only fresh [${fmt(after - before)}]"
    }

    private fun signatures(programmes: List<EPGProgramme>, fromMs: Long, toMs: Long): Set<List<Any?>> =
        programmes.asSequence()
            .filter { !it.isPlaceholder && it.startMillis >= fromMs && it.startMillis < toMs }
            .map { listOf(it.startMillis, it.endMillis, it.title, it.subTitle) }
            .toSet()
}
