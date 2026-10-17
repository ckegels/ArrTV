package com.aeriotv.android.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aeriotv.android.core.data.db.entity.EpgProgrammeEntity

@Dao
interface EpgProgrammeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<EpgProgrammeEntity>)

    @Query("SELECT * FROM epg_programme WHERE playlistId = :playlistId")
    suspend fun forPlaylist(playlistId: String): List<EpgProgrammeEntity>

    /**
     * Time-windowed variant of [forPlaylist] (iOS GuideStore parity --
     * EPGGuideView.swift `loadFromCache` predicate). Returns only programmes
     * whose airing window overlaps [fromMillis]..[toMillis], so cold launch
     * paint is ~5-10% the size of the full cache instead of all 58K+ rows.
     *
     * Overlap predicate: `endMillis > fromMillis AND startMillis < toMillis`.
     * This is symmetric in start/end (programmes that started before the
     * window AND end inside it are kept; ones that start inside the window
     * AND end after it are kept; ones fully inside are kept).
     *
     * Served by the composite `(playlistId, endMillis)` index on the entity.
     * That index is load bearing: SQLite uses ONE index per table reference,
     * so with only the single-column `playlistId` and `endMillis` indices to
     * choose from it seeks on playlistId and then visits every row the
     * playlist owns to filter the time range. Measured on the Google TV
     * Streamer 2026-09-12 (gtvlogs/session7.txt 14:29:17.640): 7605 ms to
     * return a two-day window out of a 267K-row table. Do not remove the
     * composite index without re-measuring this read.
     */
    /**
     * The row ids of [forPlaylistInWindowPage]'s window, from the
     * (playlistId, endMillis) index alone: the index holds the row id, so no
     * row is read. [byIdsStartingBefore] then reads the rows in id order.
     *
     * Read in time order the rows are scattered over the whole table: they
     * are stored as they were saved, a day's guide at a time, channel after
     * channel, so the next programme in time is a different page every time.
     * On a Chromecast HD with memory full nothing stays cached and each is a
     * read from flash (29K rows in 28-38 s at launch, the reading threads
     * waiting on the disk). In id order the same rows are a few stretches of
     * the file read front to back.
     *
     * No startMillis bound here (not in the index): rows starting after the
     * window are dropped by [byIdsStartingBefore].
     */
    @Query(
        "SELECT id FROM epg_programme INDEXED BY index_epg_programme_playlistId_endMillis " +
            "WHERE playlistId = :playlistId AND endMillis > :fromMillis " +
            "AND endMillis < :toMillis + 93600000"
    )
    suspend fun idsInWindow(playlistId: String, fromMillis: Long, toMillis: Long): List<Long>

    /**
     * [idsInWindow] for one slice of end times, [fromMillis, untilMillis). Read slice by slice
     * (PlaylistRepository.idsInWindowSliced): one query for the whole window returned 177,000
     * ids, more than Android's 2 MB cursor window holds, and on a Shield the read failed at row
     * 43,575 ("Couldn't read row ... from CursorWindow") -- every rebuild of the guide after it
     * failed and the guide stayed on "No info" (2026-10-02).
     */
    @Query(
        "SELECT id FROM epg_programme INDEXED BY index_epg_programme_playlistId_endMillis " +
            "WHERE playlistId = :playlistId AND endMillis >= :fromMillis AND endMillis < :untilMillis"
    )
    suspend fun idsEndingBetween(playlistId: String, fromMillis: Long, untilMillis: Long): List<Long>

    /** Rows by id (at most 900 ids: SQLite's bound-parameter limit), those
     *  starting before [toMillis], in id order. */
    @Query("SELECT * FROM epg_programme WHERE id IN (:ids) AND startMillis < :toMillis ORDER BY id")
    suspend fun byIdsStartingBefore(ids: List<Long>, toMillis: Long): List<EpgProgrammeEntity>

    /**
     * One page of [forPlaylistInWindow], in (endMillis, id) order after
     * ([afterEnd], [afterId]); the first page passes ([fromMillis],
     * Long.MAX_VALUE). Big reads must go through pages: Android copies query
     * results into 2 MB cursor windows and, when one fills, re-runs the query
     * from the start and steps past every row already delivered, so a single
     * 40K-row read (tens of MB with descriptions) costs quadratic work.
     * Profiled on a Chromecast HD: 22 s for the launch guide read, 66 % of all
     * CPU. Each page here fits one window.
     *
     * The order is the (playlistId, endMillis) index's own, so each page seeks
     * into that index and reads on from where the last one stopped. Paged by
     * id instead, SQLite walked the playlistId index in id order: every row of
     * the playlist, the whole cache, was visited to keep the window's (41K
     * rows in 15.5 s on a Chromecast HD, whose 105 MB cache comes off storage
     * with memory and swap full). An overlapping programme ends before
     * [toMillis] plus its own length, so `endMillis < to + 26 h` (93600000)
     * bounds the range without losing rows; a programme longer than 26 h is
     * not drawn.
     */
    @Query(
        "SELECT * FROM epg_programme WHERE playlistId = :playlistId " +
            "AND (endMillis, id) > (:afterEnd, :afterId) " +
            "AND endMillis < :toMillis + 93600000 AND startMillis < :toMillis " +
            "ORDER BY endMillis, id LIMIT :limit"
    )
    suspend fun forPlaylistInWindowPage(
        playlistId: String,
        toMillis: Long,
        afterEnd: Long,
        afterId: Long,
        limit: Int,
    ): List<EpgProgrammeEntity>

    @Query(
        "SELECT * FROM epg_programme WHERE playlistId = :playlistId " +
            "AND endMillis > :fromMillis AND startMillis < :toMillis"
    )
    suspend fun forPlaylistInWindow(
        playlistId: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<EpgProgrammeEntity>

    /**
     * Every cached row that can resolve to a given set of channels, for a
     * partial guide repaint (GuideCatalog.patched): rows stored under the
     * channels' canonical ids plus legacy rows under a raw feed key
     * ([rawKeys] are GuideMatchMaps-normalized: trimmed, lower-case; SQLite
     * lower() folds ASCII only). Served by the (playlistId, endMillis) index.
     */
    /**
     * One page of [forChannelKeysInWindow], in id order after [afterId]. A
     * result bigger than one 2 MB cursor window is refilled by re-running the
     * query; a write in between (the sweep, the window's own save) then makes
     * the refill disagree and the read fails with "Couldn't read row N from
     * CursorWindow" (seen on a Shield: the live guide window was lost). A
     * page always fits one window.
     */
    @Query(
        "SELECT * FROM epg_programme WHERE playlistId = :playlistId " +
            "AND endMillis > :fromMillis AND startMillis < :toMillis " +
            "AND (channelId IN (:canonicalIds) OR lower(trim(channelId)) IN (:rawKeys)) " +
            "AND id > :afterId ORDER BY id LIMIT :limit"
    )
    suspend fun forChannelKeysInWindowPage(
        playlistId: String,
        canonicalIds: List<String>,
        rawKeys: List<String>,
        fromMillis: Long,
        toMillis: Long,
        afterId: Long,
        limit: Int,
    ): List<EpgProgrammeEntity>

    /**
     * One channel's rows under its canonical id, from the unique (playlistId,
     * channelId, startMillis) index: that channel's rows alone are visited.
     * [forChannelKeysInWindowPage] pages by id, which makes SQLite walk the
     * whole table in id order; on a Chromecast HD (a 105 MB cache, memory and
     * swap full, so nothing stays cached) reading one channel's 43 rows took
     * 9.5 s that way.
     */
    @Query(
        "SELECT * FROM epg_programme WHERE playlistId = :playlistId " +
            "AND channelId = :channelId AND startMillis < :toMillis AND endMillis > :fromMillis"
    )
    suspend fun forChannelInWindow(
        playlistId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<EpgProgrammeEntity>

    @Query(
        "SELECT * FROM epg_programme WHERE playlistId = :playlistId " +
            "AND endMillis > :fromMillis AND startMillis < :toMillis " +
            "AND (channelId IN (:canonicalIds) OR lower(trim(channelId)) IN (:rawKeys))"
    )
    suspend fun forChannelKeysInWindow(
        playlistId: String,
        canonicalIds: List<String>,
        rawKeys: List<String>,
        fromMillis: Long,
        toMillis: Long,
    ): List<EpgProgrammeEntity>

    /**
     * EPG-scope search for the global Search surface (parity task #41 / iOS
     * SearchView EPG scope). Matches title OR description, case-insensitive
     * (Room LIKE is case-insensitive for ASCII), time-windowed to now-forward
     * (endMillis > :nowMillis) so already-ended programmes don't clutter
     * results. Ordered by start time so the soonest airing surfaces first.
     * Caller (PlaylistRepository.searchEpg) injects '%'||q||'%' wildcards.
     */
    @Query(
        "SELECT * FROM epg_programme WHERE playlistId = :playlistId " +
            "AND endMillis > :nowMillis " +
            "AND (title LIKE :like OR description LIKE :like) " +
            "ORDER BY startMillis ASC LIMIT :limit"
    )
    suspend fun searchInWindow(
        playlistId: String,
        like: String,
        nowMillis: Long,
        limit: Int = 60,
    ): List<EpgProgrammeEntity>

    /** Most recent fetch time for this source, or null when nothing is cached. */
    @Query("SELECT MAX(fetchedAt) FROM epg_programme WHERE playlistId = :playlistId")
    suspend fun newestFetchedAt(playlistId: String): Long?

    /** Earliest cached start for this source, or null when nothing is cached. */
    @Query("SELECT MIN(startMillis) FROM epg_programme WHERE playlistId = :playlistId")
    suspend fun earliestStart(playlistId: String): Long?

    /** Latest cached end for this source, or null when nothing is cached. */
    @Query("SELECT MAX(endMillis) FROM epg_programme WHERE playlistId = :playlistId")
    suspend fun latestEnd(playlistId: String): Long?

    @Query("DELETE FROM epg_programme WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: String)

    /**
     * Up to [limit] of a source's rows; returns how many went. A whole
     * source in ONE statement (100K+ rows, five indexes) held the database's
     * write connection for minutes on a Chromecast HD, and every other write
     * queued behind it; batches let other work in between.
     */
    @Query(
        "DELETE FROM epg_programme WHERE id IN " +
            "(SELECT id FROM epg_programme WHERE playlistId = :playlistId LIMIT :limit)"
    )
    suspend fun deleteBatchForPlaylist(playlistId: String, limit: Int): Int

    /** Prune one source's programmes that ended before its retention cutoff
     *  (catch-up task #135: retention is per playlist now, so the old blanket
     *  cross-source ended-1h-ago delete is gone). Returns the row count so the
     *  catch-up reach prune can report what it dropped; existing callers
     *  ignore it. */
    @Query("DELETE FROM epg_programme WHERE playlistId = :playlistId AND endMillis < :before")
    suspend fun deleteEndedBeforeForPlaylist(playlistId: String, before: Long): Int

    /**
     * Catch-up reach prune (Logan 2026-09-12: "delete EPG from the previous
     * days that are no longer reachable for catch-up").
     *
     * One indexed DELETE per CHANNEL GROUP, never per row and never per
     * channel: the caller buckets channels by their catch-up days and issues
     * one statement per bucket (chunked at 900 ids for SQLite's host-parameter
     * cap). Served by the `(playlistId, channelId)` index, with `endMillis`
     * filtered on the rows that index already narrows to.
     *
     * Returns the number of rows deleted.
     */
    @Query(
        "DELETE FROM epg_programme WHERE playlistId = :playlistId " +
            "AND endMillis < :before AND channelId IN (:channelIds)"
    )
    suspend fun deleteEndedBeforeForChannels(
        playlistId: String,
        before: Long,
        channelIds: List<String>,
    ): Int

    /** Delete the airing-and-future region the fresh feed owns outright, for
     *  the CHANNELS it actually carries; [mergeForPlaylist]'s helper. */
    @Query(
        "DELETE FROM epg_programme WHERE playlistId = :playlistId " +
            "AND endMillis > :fromMillis AND channelId IN (:channelIds)"
    )
    suspend fun deleteCoveredWindowForChannels(
        playlistId: String,
        fromMillis: Long,
        channelIds: List<String>,
    )

    /** Delete only the region a feed ACTUALLY delivered for these channels:
     *  rows overlapping [fromMillis, toMillis). Sister to
     *  [deleteCoveredWindowForChannels], which deletes everything from
     *  `fromMillis` onward and is therefore only correct for a feed that
     *  covers a channel's whole present and future. */
    @Query(
        "DELETE FROM epg_programme WHERE playlistId = :playlistId " +
            "AND endMillis > :fromMillis AND startMillis < :toMillis " +
            "AND channelId IN (:channelIds)"
    )
    suspend fun deleteCoveredSpanForChannels(
        playlistId: String,
        fromMillis: Long,
        toMillis: Long,
        channelIds: List<String>,
    )

    /**
     * [deleteCoveredSpanForChannels] for ONE channel. With a single channel the
     * query can only be answered from the unique (playlistId, channelId,
     * startMillis) index, which visits that channel's rows alone. With several
     * channels in the IN list SQLite picks (playlistId, endMillis) instead and
     * walks every future row of the whole guide per statement: a live guide
     * window that touched 1323 channels (hundreds of distinct spans) spent six
     * minutes in these deletes on a Chromecast HD, 33 s for 484 channels on a
     * SHIELD. Measured on a copy of the table (460K rows, 1323 channels): 9.2 s
     * grouped, 0.18 s one channel at a time.
     */
    @Query(
        "DELETE FROM epg_programme WHERE playlistId = :playlistId " +
            "AND channelId = :channelId AND startMillis < :toMillis AND endMillis > :fromMillis"
    )
    suspend fun deleteCoveredSpanForChannel(
        playlistId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Int

    /**
     * The distinct channel ids stored under raw grid keys (not a canonical
     * disp: / m3u: id), read from the (playlistId, channelId) index alone: the
     * programme rows are never touched. The raw keys used to be matched
     * normalized (lower(trim(channelId))), which no index can answer, so
     * every server guide update scanned the whole table -- 11.8 s to save 802
     * rows on a Chromecast HD whose table comes off storage.
     */
    @Query(
        "SELECT DISTINCT channelId FROM epg_programme INDEXED BY index_epg_programme_playlistId_channelId " +
            "WHERE playlistId = :playlistId AND channelId NOT LIKE 'disp:%' AND channelId NOT LIKE 'm3u:%'"
    )
    suspend fun rawKeyedChannelIds(playlistId: String): List<String>

    /**
     * Merge a fresh feed into one source's cached guide in a single
     * transaction so a reader never sees a half-written batch. The feed owns
     * the PRESENT AND FUTURE for THE CHANNELS IT CARRIES (that region is
     * deleted and re-inserted), while ALREADY-AIRED rows are left in place so
     * history accumulates for catch-up (task #135/#137). Any past rows the
     * feed still carries replace their cached copies via the unique
     * (playlistId, channelId, startMillis) index + REPLACE conflict strategy
     * instead of duplicating. An earlier revision deleted everything after the
     * feed's EARLIEST start; feeds trim their own history between refreshes,
     * so recently-ended programmes inside that window but absent from the new
     * feed were silently erased.
     *
     * The channel scoping is load-bearing (Logan 2026-08-10). This used to
     * delete the whole present+future for the PLAYLIST, which is correct only
     * when `rows` is the complete feed - and PlaylistRepository's upstream
     * layering deliberately calls saveEpgToCache ONCE PER SOURCE so a kill
     * mid-phase loses at most one feed. With a playlist-wide delete each of
     * those saves wiped the previous one, so a 3-source layering left only the
     * last source's rows: a 6518-programme grid collapsed to ~545, the guide
     * lost everything past "now", and because the cache was non-empty the
     * freshness check then skipped the network on every relaunch, so it never
     * healed. Scoping the delete to the incoming feed's channels lets the
     * sources compose instead of clobber.
     */
    @Transaction
    suspend fun mergeForPlaylist(
        playlistId: String,
        rows: List<EpgProgrammeEntity>,
        nowMillis: Long,
        replaceCoveredWindow: Boolean = true,
    ) {
        if (rows.isEmpty()) return
        // Drop degenerate programmes before they reach the cache. A row whose
        // stop is at or before its start cannot be drawn: on Apple the same
        // data rendered ~10px-wide slivers with the title wrapped to one
        // character per line (tvOS guide, 2026-08-11). The ingest paths only
        // ever checked that a programme OVERLAPS the requested window, which a
        // zero-length or inverted row satisfies, so nothing filtered them.
        //
        // 30s floor rather than 0: sub-30s entries are feed noise (placeholder
        // or truncated rows), not schedule data. Filtering here, at the single
        // point every source persists through, rather than in each parser.
        val drawable = rows.filter { it.endMillis - it.startMillis >= 30_000L }
        if (drawable.isEmpty()) return
        if (replaceCoveredWindow) {
            // Delete only what this feed is ACTUALLY authoritative for: per
            // channel, the span between its earliest and latest row here. The
            // old code deleted everything from `now` onward for every channel
            // the feed mentioned, which assumes any feed carrying a channel
            // carries its whole present and future. That is false in two ways
            // we have now seen on Logan's Streamer (2026-08-20, channels 17 and
            // 18 blank at the current time while Dispatcharr and the upstream
            // Teamarr feed both had the airing block): a feed can simply be
            // sparser than the one before it, and a budget-truncated XMLTV
            // parse returns a PARTIAL list that still went through this delete,
            // wiping rows it never carried.
            //
            // One DELETE per channel (see deleteCoveredSpanForChannel): grouping
            // channels by span into IN lists made SQLite scan the whole
            // guide's future once per group.
            val spanByChannel = HashMap<String, LongArray>()
            for (r in drawable) {
                val cur = spanByChannel[r.channelId]
                if (cur == null) {
                    spanByChannel[r.channelId] = longArrayOf(r.startMillis, r.endMillis)
                } else {
                    if (r.startMillis < cur[0]) cur[0] = r.startMillis
                    if (r.endMillis > cur[1]) cur[1] = r.endMillis
                }
            }
            for ((channelId, span) in spanByChannel) {
                // Never reach back before `now`: already-aired rows are the
                // catch-up archive and stay put (task #135/#137).
                val from = maxOf(nowMillis, span[0])
                if (span[1] <= from) continue
                deleteCoveredSpanForChannel(playlistId, channelId, from, span[1])
            }
        }
        insertAll(drawable)
    }
}
