package com.aeriotv.android.core.data.sync

import com.aeriotv.android.core.network.DispatchMoreSync

/** What the start-up check found for one part of a playlist's cache. */
enum class SyncChange {
    /** The server's stamp is the one saved with the cache: download nothing. */
    SAME,

    /** The server changed it since the cache was written: download that part. */
    CHANGED,

    /** Nothing saved to compare with (first run on a server that has the check): the caller
     *  keeps its time-based rule, and saves the stamp when it found the cache fresh. */
    UNKNOWN,
}

/**
 * The start-up check's answer for one playlist. [lineup] covers the channel list and the guide
 * records its channels use (both are what [com.aeriotv.android.core.data.repository.PlaylistRepository.refresh]
 * downloads); [guides] covers the programmes.
 */
data class SyncVerdict(val lineup: SyncChange, val guides: SyncChange, val answer: DispatchMoreSync)

/**
 * Dispatch More's stamps (GET /api/core/app-sync/, v272), saved per playlist when the part they
 * stand for was downloaded, and compared at the next start: every launch used to download the
 * lineup, every guide record on the server and the guide, which kept a Chromecast HD's heap at
 * its limit for minutes (fork/guide-loading-plan.md).
 *
 * A stamp is saved only after the download it vouches for succeeded, and it is the stamp the
 * server gave before that download started: a change landing during the download then differs
 * at the next check instead of being covered up.
 */
class SyncStamps(private val store: Store) {

    /** Where the stamps live (SharedPreferences in the app, a map in tests). */
    interface Store {
        fun get(key: String): String?
        fun put(key: String, value: String?)
    }

    fun verdict(playlistId: String, answer: DispatchMoreSync): SyncVerdict = SyncVerdict(
        lineup = compare(store.get(lineupKey(playlistId)), lineupStamp(answer)),
        guides = compare(store.get(guidesKey(playlistId)), answer.guides),
        answer = answer,
    )

    fun saveLineup(playlistId: String, answer: DispatchMoreSync) =
        store.put(lineupKey(playlistId), lineupStamp(answer))

    fun saveGuides(playlistId: String, answer: DispatchMoreSync) =
        store.put(guidesKey(playlistId), answer.guides)

    /** The cached guide was thrown away: the next check must not call it current. */
    fun forgetGuides(playlistId: String) = store.put(guidesKey(playlistId), null)

    fun forget(playlistId: String) {
        store.put(lineupKey(playlistId), null)
        store.put(guidesKey(playlistId), null)
    }

    private fun compare(saved: String?, now: String): SyncChange = when {
        saved.isNullOrEmpty() || now.isEmpty() -> SyncChange.UNKNOWN
        saved == now -> SyncChange.SAME
        else -> SyncChange.CHANGED
    }

    // One stamp for both: a channel moved to another guide is a lineup download either way
    private fun lineupStamp(answer: DispatchMoreSync) = answer.lineup + "/" + answer.guideLinks
    private fun lineupKey(playlistId: String) = "$playlistId.lineup"
    private fun guidesKey(playlistId: String) = "$playlistId.guides"
}
