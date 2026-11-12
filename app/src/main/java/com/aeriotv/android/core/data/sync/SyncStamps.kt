package com.aeriotv.android.core.data.sync

import com.aeriotv.android.core.network.DispatchMoreGuideMarks
import com.aeriotv.android.core.network.DispatchMoreSync
import kotlinx.serialization.json.Json

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
    fun forgetGuides(playlistId: String) {
        store.put(guidesKey(playlistId), null)
        store.put(marksKey(playlistId), null)
    }

    fun forget(playlistId: String) {
        store.put(lineupKey(playlistId), null)
        forgetGuides(playlistId)
    }

    /**
     * Dispatch More v275: the guide cache holds what these per-guide marks stand for. Saves
     * their stamp as the guides stamp too, so the two always describe the same guide.
     */
    fun saveMarks(playlistId: String, marks: DispatchMoreGuideMarks) {
        store.put(marksKey(playlistId), json.encodeToString(DispatchMoreGuideMarks.serializer(), marks))
        store.put(guidesKey(playlistId), marks.guides)
    }

    /**
     * The marks saved with the guide cache; null when there are none or they stand for another
     * guide than the saved stamp (the stamp was saved since without marks).
     */
    fun savedMarks(playlistId: String): DispatchMoreGuideMarks? {
        val raw = store.get(marksKey(playlistId)) ?: return null
        val marks = runCatching { json.decodeFromString(DispatchMoreGuideMarks.serializer(), raw) }.getOrNull()
        return marks?.takeIf { it.guides.isNotEmpty() && it.guides == store.get(guidesKey(playlistId)) }
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
    private fun marksKey(playlistId: String) = "$playlistId.marks"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The guides to fetch again: those whose mark moved, and those the cache has no mark for
         * (a channel joined on a guide no other channel used). A guide no channel uses any more
         * needs nothing.
         */
        fun changedGuides(saved: Map<String, String>, now: Map<String, String>): Set<Int> =
            now.entries.filter { (id, mark) -> saved[id] != mark }.mapNotNullTo(HashSet()) { it.key.toIntOrNull() }
    }
}
