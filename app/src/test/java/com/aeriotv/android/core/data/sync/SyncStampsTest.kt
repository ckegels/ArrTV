package com.aeriotv.android.core.data.sync

import com.aeriotv.android.core.network.DispatchMoreSync
import org.junit.Assert.assertEquals
import org.junit.Test

/** The start-up check's comparison (fork/guide-loading-plan.md): what a launch downloads. */
class SyncStampsTest {

    private val saved = HashMap<String, String>()
    private val stamps = SyncStamps(object : SyncStamps.Store {
        override fun get(key: String) = saved[key]
        override fun put(key: String, value: String?) {
            if (value == null) saved.remove(key) else saved[key] = value
        }
    })

    private fun answer(lineup: String = "L1", links: String = "G1", guides: String = "P1") =
        DispatchMoreSync(sync = 1, lineup = lineup, guideLinks = links, guides = guides)

    @Test
    fun nothingSavedIsUnknownSoTheTimeRulesDecide() {
        val v = stamps.verdict("p", answer())
        assertEquals(SyncChange.UNKNOWN, v.lineup)
        assertEquals(SyncChange.UNKNOWN, v.guides)
    }

    @Test
    fun savedStampsCompareEqualUntilTheServerChangesThem() {
        stamps.saveLineup("p", answer())
        stamps.saveGuides("p", answer())
        assertEquals(SyncChange.SAME, stamps.verdict("p", answer()).lineup)
        assertEquals(SyncChange.SAME, stamps.verdict("p", answer()).guides)
        assertEquals(SyncChange.CHANGED, stamps.verdict("p", answer(lineup = "L2")).lineup)
        assertEquals(SyncChange.CHANGED, stamps.verdict("p", answer(guides = "P2")).guides)
    }

    @Test
    fun aChannelOnAnotherGuideIsALineupChange() {
        stamps.saveLineup("p", answer())
        val v = stamps.verdict("p", answer(links = "G2"))
        assertEquals(SyncChange.CHANGED, v.lineup)
        assertEquals(SyncChange.UNKNOWN, v.guides)
    }

    @Test
    fun theGuidesStampIsForgottenWithTheCache() {
        stamps.saveLineup("p", answer())
        stamps.saveGuides("p", answer())
        stamps.forgetGuides("p")
        assertEquals(SyncChange.SAME, stamps.verdict("p", answer()).lineup)
        assertEquals(SyncChange.UNKNOWN, stamps.verdict("p", answer()).guides)
    }

    @Test
    fun playlistsKeepTheirOwnStamps() {
        stamps.saveLineup("a", answer())
        assertEquals(SyncChange.UNKNOWN, stamps.verdict("b", answer()).lineup)
    }

    @Test
    fun anEmptyServerStampNeverReadsAsUnchanged() {
        stamps.saveGuides("p", answer())
        assertEquals(SyncChange.UNKNOWN, stamps.verdict("p", answer(guides = "")).guides)
    }
}
