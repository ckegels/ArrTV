package com.aeriotv.android.core.guide

import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel

/**
 * The whole playlist's guide, built ONCE from cached rows: one [GuideIndex]
 * per canonical channel id. Exposed as an immutable `Map<String, List>` so
 * every existing consumer that does `epgByChannel[channel.guideMatchKey]`
 * keeps working unchanged (phase 2 of the rebuild: new data layer behind the
 * old surface). The new grid renderer reads [index] directly.
 *
 * Replaces the four incremental map-surgery paths in the ViewModel (cached
 * paint, fresh fetch, layering landed, history merge) with a single
 * deterministic build: rows -> attach -> dedup -> index -> placeholders.
 * Anything that changes the cache writes to Room and rebuilds this.
 */
class GuideCatalog private constructor(
    private val indices: Map<String, GuideIndex>,
    private val lists: Map<String, List<EPGProgramme>>,
    /** Fingerprint of the channel list this catalog was built for. */
    val identityHash: String,
    /** The window [build] synthesized placeholders for; [patched] reuses it. */
    val windowStartMs: Long,
    val windowEndMs: Long,
    private val minGapMs: Long,
    /** Tie-break for rows sharing a slot; see [build]. Patches and comparisons reuse it. */
    private val stableOrder: Boolean,
) : Map<String, List<EPGProgramme>> by lists {

    fun index(channelId: GuideChannelId): GuideIndex? = indices[channelId.value]
    fun index(channelKey: String): GuideIndex? = indices[channelKey]

    /**
     * A copy in which only the channels in [changedIds] are rebuilt from
     * [rows]; every other channel keeps its entry (and list instance). Uses
     * the same per-channel steps as [build] over this catalog's own window,
     * so the patched channels come out exactly as a full build over the same
     * rows would make them, provided [rows] holds every cached row that
     * resolves to those channels (see PlaylistRepository.loadCachedEpgForChannels).
     *
     * Returns null when [channels] is not the lineup this catalog was built
     * for: a lineup change needs a full [build].
     */
    fun patched(channels: List<M3UChannel>, changedIds: Set<String>, rows: List<EPGProgramme>): GuideCatalog? {
        if (GuideIdentityHash.of(channels) != identityHash) return null
        if (changedIds.isEmpty()) return this
        val maps = GuideMatchMaps.build(channels)
        val byChannel = attach(rows, maps)
        val newIndices = HashMap(indices)
        val newLists = HashMap(lists)
        for (ch in channels) {
            val id = ch.guideChannelId()
            if (id.value !in changedIds) continue
            val (index, cells) = buildChannel(ch, byChannel[id.value], windowStartMs, windowEndMs, minGapMs, stableOrder)
            val prev = lists[id.value]
            newLists[id.value] = if (prev != null && prev == cells) prev else cells
            newIndices[id.value] = index
        }
        return GuideCatalog(newIndices, newLists, identityHash, windowStartMs, windowEndMs, minGapMs, stableOrder)
    }

    /**
     * This guide for a changed lineup, without rebuilding it: channels no
     * longer in [channels] are dropped, [rebuiltIds] (the added and changed
     * ones) are built from [rows], and every other channel keeps its cells.
     * A server lineup check that adds or removes a channel or two happens at
     * most launches; a full rebuild for it took 16 s on a Chromecast HD.
     */
    fun forLineup(channels: List<M3UChannel>, rebuiltIds: Set<String>, rows: List<EPGProgramme>): GuideCatalog {
        val present = channels.mapTo(HashSet(channels.size * 2)) { it.guideChannelId().value }
        val newIndices = HashMap<String, GuideIndex>(present.size * 2)
        val newLists = HashMap<String, List<EPGProgramme>>(present.size * 2)
        for ((id, index) in indices) if (id in present && id !in rebuiltIds) newIndices[id] = index
        for ((id, list) in lists) if (id in present && id !in rebuiltIds) newLists[id] = list
        if (rebuiltIds.isNotEmpty()) {
            val byChannel = attach(rows, GuideMatchMaps.build(channels))
            for (ch in channels) {
                val id = ch.guideChannelId()
                if (id.value !in rebuiltIds) continue
                val (index, cells) = buildChannel(ch, byChannel[id.value], windowStartMs, windowEndMs, minGapMs, stableOrder)
                newLists[id.value] = cells
                newIndices[id.value] = index
            }
        }
        return GuideCatalog(newIndices, newLists, GuideIdentityHash.of(channels), windowStartMs, windowEndMs, minGapMs, stableOrder)
    }

    /**
     * Canonical ids of the channels whose schedule in [fromMs, toMs) differs
     * between this catalog and [freshRows] (a server window). The fresh rows
     * go through the same attach / dedup / index steps as [build] before the
     * comparison, because building clips overlapping programmes and merges
     * duplicates: comparing raw rows with built cells reported hundreds of
     * unchanged channels as changed on a real feed.
     */
    fun changedChannels(channels: List<M3UChannel>, freshRows: List<EPGProgramme>, fromMs: Long, toMs: Long): Set<String> {
        val byId = channels.associateBy { it.guideChannelId().value }
        val attached = attach(asStored(freshRows), GuideMatchMaps.build(channels))
        val fresh = HashMap<String, List<EPGProgramme>>(attached.size * 2)
        for ((id, rows) in attached) {
            val ch = byId[id] ?: continue
            fresh[id] = buildChannel(ch, rows, windowStartMs, windowEndMs, minGapMs, stableOrder).second
        }
        val changed = GuideWindowDiff.changedChannels(lists, fresh, fromMs, toMs)
        lastExplanations = changed.take(3).map { id ->
            "$id: " + GuideWindowDiff.explain(lists[id].orEmpty(), fresh[id].orEmpty(), fromMs, toMs)
        }
        return changed
    }

    /** Diagnostics from the last [changedChannels] call: why up to three channels read as changed. */
    @Volatile var lastExplanations: List<String> = emptyList()
        private set

    companion object {
        val EMPTY = GuideCatalog(emptyMap(), emptyMap(), "", 0L, 0L, 5 * 60_000L, false)

        /**
         * Build for [channels] from cached [rows] (any source, any key shape:
         * rows are attached through the match maps here, one-to-many).
         *
         * [windowStartMs]..[windowEndMs] bounds the placeholder synthesis:
         * a channel with no programmes gets one channel-name placeholder
         * spanning the window (Dispatcharr "dummy EPG" parity); a channel
         * with holes of [minGapMs] or more gets focusable "No info" cells.
         *
         * [previous] lets unchanged channels keep their previous List
         * INSTANCE, so Compose rows keyed on list identity do not recompose
         * when a refresh landed the same programmes again.
         *
         * [stableOrder] breaks ties between rows with the same start and end
         * by title (then subtitle) instead of input order. Dedup keeps the
         * first row of a slot, and a server can carry two programmes for one
         * slot (seen: Dispatcharr returning 'Big Brother' and 'Sheriff
         * Country' for kval.us 03:00-04:00, in a different order per request),
         * so input order made the shown title depend on which download landed
         * last. Off keeps the stock order (Dispatcharr live updates turn it on
         * so their comparisons are stable).
         */
        fun build(
            channels: List<M3UChannel>,
            rows: List<EPGProgramme>,
            windowStartMs: Long,
            windowEndMs: Long,
            minGapMs: Long = 5 * 60_000L,
            previous: GuideCatalog? = null,
            stableOrder: Boolean = false,
        ): GuideCatalog {
            if (channels.isEmpty()) return EMPTY
            val maps = GuideMatchMaps.build(channels)
            val byChannel = attach(rows, maps)
            val indices = HashMap<String, GuideIndex>(channels.size * 2)
            val lists = HashMap<String, List<EPGProgramme>>(channels.size * 2)
            for (ch in channels) {
                val id = ch.guideChannelId()
                val (index, cells) = buildChannel(ch, byChannel[id.value], windowStartMs, windowEndMs, minGapMs, stableOrder)
                val prev = previous?.lists?.get(id.value)
                lists[id.value] = if (prev != null && prev == cells) prev else cells
                indices[id.value] = index
            }
            return GuideCatalog(indices, lists, GuideIdentityHash.of(channels), windowStartMs, windowEndMs, minGapMs, stableOrder)
        }

        /**
         * [rows] as the cache will hold them once written: the epg_programme
         * table is unique on (playlistId, channelId, startMillis) with REPLACE,
         * so of two rows starting together on one channel only the LAST one
         * written survives. A server with two schedules for one channel (seen:
         * 'Judge Judy' and 'Mutual of Omaha's Wild Kingdom' both at 11:00 on
         * nbc16ksan.us) would otherwise compare its in-memory pair against the
         * single stored survivor and read as changed on every check.
         */
        fun asStored(rows: List<EPGProgramme>): List<EPGProgramme> {
            val last = LinkedHashMap<Pair<String, Long>, EPGProgramme>(rows.size * 2)
            for (r in inStoreOrder(rows)) {
                val key = r.channelId to r.startMillis
                last.remove(key)
                last[key] = r
            }
            return if (last.size == rows.size) rows else last.values.toList()
        }

        /**
         * [rows] in the order they must be WRITTEN so the (channel, start)
         * survivor does not depend on the server's response order: Dispatcharr
         * returns parallel schedules in a different order per request, so the
         * stored survivor (the last written) flipped on every fetch. Within a
         * slot the survivor is the last by end, then title, then subtitle.
         */
        fun inStoreOrder(rows: List<EPGProgramme>): List<EPGProgramme> = rows.sortedWith(STORE_ORDER)

        private val STORE_ORDER: Comparator<EPGProgramme> = compareBy(
            { it.channelId }, { it.startMillis }, { it.endMillis }, { it.title }, { it.subTitle.orEmpty() },
        )

        private val STOCK_ORDER: Comparator<EPGProgramme> = compareBy({ it.startMillis }, { it.endMillis })
        private val STABLE_ORDER: Comparator<EPGProgramme> =
            compareBy({ it.startMillis }, { it.endMillis }, { it.title }, { it.subTitle.orEmpty() })

        // Rows are already stored under canonical ids once written by the
        // new pipeline; legacy rows (raw feed keys) still resolve through
        // the maps. Attach is idempotent for canonical keys because a
        // canonical id never equals a tvg-id/number/uuid key.
        private fun attach(rows: List<EPGProgramme>, maps: GuideMatchMaps): Map<String, List<EPGProgramme>> {
            val byChannel = HashMap<String, ArrayList<EPGProgramme>>()
            for (p in rows) {
                val direct = p.channelId
                if (direct.startsWith("disp:") || direct.startsWith("m3u:")) {
                    byChannel.getOrPut(direct) { ArrayList() }.add(p)
                    continue
                }
                for (id in maps.resolve(direct, p.source)) {
                    byChannel.getOrPut(id.value) { ArrayList() }.add(p.copy(channelId = id.value))
                }
            }
            return byChannel
        }

        private fun buildChannel(
            ch: M3UChannel,
            raw: List<EPGProgramme>?,
            windowStartMs: Long,
            windowEndMs: Long,
            minGapMs: Long,
            stableOrder: Boolean,
        ): Pair<GuideIndex, List<EPGProgramme>> {
            val id = ch.guideChannelId()
            return if (raw.isNullOrEmpty()) {
                GuideIndex.empty(id) to GuideIndex.nameCells(id, ch.name, windowStartMs, windowEndMs)
            } else {
                val order = if (stableOrder) STABLE_ORDER else STOCK_ORDER
                val deduped = GuideMerge.dedup(raw.sortedWith(order))
                val index = GuideIndex.build(id, deduped)
                index to index.withHoles(windowStartMs, windowEndMs, minGapMs)
            }
        }
    }
}
