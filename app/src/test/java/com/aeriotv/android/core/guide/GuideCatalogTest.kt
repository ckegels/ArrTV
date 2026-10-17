package com.aeriotv.android.core.guide

import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideCatalogTest {
    private val h = 3_600_000L
    private fun ch(name: String, tvg: String, url: String = "http://p/$name") =
        M3UChannel(id = "m3u:$name", name = name, url = url, tvgID = tvg)
    private fun p(key: String, title: String, start: Long, end: Long) =
        EPGProgramme(channelId = key, title = title, description = "", startMillis = start, endMillis = end, category = "")

    @Test
    fun buildsIndicesAndKeepsTheMapContractForOldConsumers() {
        val espn = ch("ESPN", "espn.us"); val fox = ch("FOX", "fox.us"); val quiet = ch("Quiet", "quiet.us")
        val cat = GuideCatalog.build(
            listOf(espn, fox, quiet),
            listOf(p("ESPN.us", "SportsCenter", 6 * h, 7 * h), p("fox.us", "News", 0, h)),
            windowStartMs = 0, windowEndMs = 8 * h,
        )
        // Old consumers: epgByChannel[channel.guideMatchKey]
        val espnList = cat[espn.guideChannelId().value]
        assertNotNull(espnList)
        assertEquals(listOf("No info", "SportsCenter", "No info"), espnList!!.map { it.title })
        assertEquals(listOf("News", "No info"), cat[fox.guideChannelId().value]!!.map { it.title })
        // A channel with nothing gets the channel-name placeholder.
        val q = cat[quiet.guideChannelId().value]!!.single()
        assertTrue(q.isPlaceholder); assertEquals("Quiet", q.title)
        // Direct index access for the new renderer.
        assertEquals("SportsCenter", cat.index(espn.guideChannelId())!!.cellAt(6 * h + 1)?.title)
        assertEquals(3, cat.size); assertTrue(cat.containsKey(fox.guideChannelId().value))
    }

    @Test
    fun aLineupChangeKeepsTheOthersAndBuildsOnlyTheNewOnes() {
        val espn = ch("ESPN", "espn.us"); val fox = ch("FOX", "fox.us"); val cnn = ch("CNN", "cnn.us")
        val cat = GuideCatalog.build(
            listOf(espn, fox),
            listOf(p("espn.us", "SportsCenter", 0, h), p("fox.us", "News", 0, h)),
            windowStartMs = 0, windowEndMs = 2 * h,
        )
        val espnCells = cat[espn.guideChannelId().value]
        val next = cat.forLineup(listOf(espn, cnn), setOf(cnn.guideChannelId().value), listOf(p("cnn.us", "Newsroom", 0, h)))
        // FOX went, CNN came with its own rows, ESPN kept the very same cells
        assertEquals(null, next[fox.guideChannelId().value])
        assertEquals("Newsroom", next[cnn.guideChannelId().value]!!.first().title)
        assertSame(espnCells, next[espn.guideChannelId().value])
        // The same identity a full build of the new lineup has, so later
        // guide windows patch it instead of rebuilding
        assertEquals(GuideIdentityHash.of(listOf(espn, cnn)), next.identityHash)
    }

    @Test
    fun rowsAlreadyKeyedByCanonicalIdAreTakenDirectly() {
        val espn = ch("ESPN", "espn.us")
        val cat = GuideCatalog.build(listOf(espn), listOf(p(espn.guideChannelId().value, "Direct", 0, h)), 0, 2 * h)
        assertEquals("Direct", cat.index(espn.guideChannelId())!!.cellAt(1)?.title)
    }

    @Test
    fun unchangedChannelsKeepTheirPreviousListInstance() {
        val espn = ch("ESPN", "espn.us"); val fox = ch("FOX", "fox.us")
        val rows = listOf(p("espn.us", "A", 0, h), p("fox.us", "B", 0, h))
        val first = GuideCatalog.build(listOf(espn, fox), rows, 0, 2 * h)
        val second = GuideCatalog.build(listOf(espn, fox), rows + p("fox.us", "C", h, 2 * h), 0, 2 * h, previous = first)
        assertSame(first[espn.guideChannelId().value], second[espn.guideChannelId().value])
        assertTrue(first[fox.guideChannelId().value] !== second[fox.guideChannelId().value])
    }

    @Test
    fun duplicatesFromTwoSourcesCollapse() {
        val espn = ch("ESPN", "espn.us")
        val cat = GuideCatalog.build(
            listOf(espn),
            listOf(p("espn.us", "Game", 0, 2 * h), p("espn.us", "Game", 30_000, 2 * h)),
            0, 2 * h,
        )
        assertEquals(1, cat.index(espn.guideChannelId())!!.cells.size)
    }

    @Test
    fun patchedChannelsMatchAFullBuildOverTheSameRows() {
        val espn = ch("ESPN", "espn.us"); val fox = ch("FOX", "fox.us"); val quiet = ch("Quiet", "quiet.us")
        val lineup = listOf(espn, fox, quiet)
        val before = listOf(p("espn.us", "A", 0, h), p("fox.us", "B", 0, h))
        val catalog = GuideCatalog.build(lineup, before, 0, 4 * h)
        // ESPN's schedule changed; the new rows arrive under the canonical id AND a legacy raw key.
        val espnId = espn.guideChannelId().value
        val after = listOf(p(espnId, "A2", 0, 2 * h), p("ESPN.us", "Late", 3 * h, 4 * h), p("fox.us", "B", 0, h))
        val patched = catalog.patched(lineup, setOf(espnId), after.filter { it.channelId != "fox.us" })!!
        val full = GuideCatalog.build(lineup, after, 0, 4 * h)
        for (c in lineup) {
            val id = c.guideChannelId().value
            assertEquals(id, full[id], patched[id])
        }
        // Untouched channels keep their list instance: nothing recomposes.
        assertSame(catalog[fox.guideChannelId().value], patched[fox.guideChannelId().value])
        assertEquals("A2", patched.index(espn.guideChannelId())!!.cellAt(1)?.title)
    }

    @Test
    fun patchingAChannelToNoRowsGivesTheNamePlaceholder() {
        val espn = ch("ESPN", "espn.us")
        val catalog = GuideCatalog.build(listOf(espn), listOf(p("espn.us", "A", 0, h)), 0, 2 * h)
        val id = espn.guideChannelId().value
        val patched = catalog.patched(listOf(espn), setOf(id), emptyList())!!
        assertEquals(GuideCatalog.build(listOf(espn), emptyList(), 0, 2 * h)[id], patched[id])
    }

    @Test
    fun aDifferentLineupCannotBePatched() {
        val espn = ch("ESPN", "espn.us"); val fox = ch("FOX", "fox.us")
        val catalog = GuideCatalog.build(listOf(espn), emptyList(), 0, h)
        assertEquals(null, catalog.patched(listOf(espn, fox), setOf(espn.guideChannelId().value), emptyList()))
    }

    @Test
    fun patchingNothingReturnsTheSameCatalog() {
        val espn = ch("ESPN", "espn.us")
        val catalog = GuideCatalog.build(listOf(espn), emptyList(), 0, h)
        assertSame(catalog, catalog.patched(listOf(espn), emptySet(), emptyList()))
    }

    @Test
    fun anUnchangedFeedWithOverlapsIsNotReportedAsChanged() {
        // Building clips the later of two overlapping programmes; the raw feed
        // still carries the overlap. Same feed again must read as unchanged.
        val espn = ch("ESPN", "espn.us"); val fox = ch("FOX", "fox.us")
        val lineup = listOf(espn, fox)
        val feed = listOf(
            p("espn.us", "A", 0, h + 60_000), p("espn.us", "B", h, 2 * h),
            p("fox.us", "C", 0, h), p("fox.us", "C", 0, h),
        )
        val catalog = GuideCatalog.build(lineup, feed, 0, 4 * h)
        assertEquals(emptySet<String>(), catalog.changedChannels(lineup, feed, 0, 4 * h))
        val moved = feed.map { if (it.title == "B") it.copy(startMillis = h + 30 * 60_000) else it }
        assertEquals(setOf(espn.guideChannelId().value), catalog.changedChannels(lineup, moved, 0, 4 * h))
    }

    @Test
    fun stableOrderPicksTheSameWinnerWhateverTheInputOrder() {
        // kval.us on the Shield's server: two programmes for one slot, in a
        // different order per request.
        val cbs = ch("CBS", "kval.us")
        val a = p("kval.us", "Big Brother", 3 * h, 4 * h)
        val b = p("kval.us", "Sheriff Country", 3 * h, 4 * h)
        val id = cbs.guideChannelId()
        fun winner(rows: List<EPGProgramme>, stable: Boolean) =
            GuideCatalog.build(listOf(cbs), rows, 0, 6 * h, stableOrder = stable).index(id)!!.cellAt(3 * h + 1)?.title
        assertEquals("Big Brother", winner(listOf(a, b), stable = true))
        assertEquals("Big Brother", winner(listOf(b, a), stable = true))
        // Stock order: input order decides (unchanged behaviour with live updates off).
        assertEquals("Sheriff Country", winner(listOf(b, a), stable = false))
        // The same window arriving again compares as unchanged against the
        // guide built from what the cache stored for it.
        val window = listOf(b, a)
        val catalog = GuideCatalog.build(listOf(cbs), GuideCatalog.asStored(window), 0, 6 * h, stableOrder = true)
        assertEquals(emptySet<String>(), catalog.changedChannels(listOf(cbs), window, 0, 6 * h))
    }

    @Test
    fun twoProgrammesStartingTogetherCompareAsTheStoredSurvivor() {
        // nbc16ksan.us on the Shield's server: two schedules, both at 11:00.
        // The cache keeps one row per (channel, start): the last one written.
        val nbc = ch("NBC", "nbc16ksan.us")
        val judy = p("nbc16ksan.us", "Judge Judy", 11 * h, 11 * h + 30 * 60_000)
        val wild = p("nbc16ksan.us", "Mutual of Omaha's Wild Kingdom", 11 * h, 11 * h + 30 * 60_000)
        val window = listOf(judy, wild)
        assertEquals(listOf(wild), GuideCatalog.asStored(window))
        // The guide on screen was built from what the cache holds.
        val onScreen = GuideCatalog.build(listOf(nbc), GuideCatalog.asStored(window), 0, 24 * h, stableOrder = true)
        assertEquals(emptySet<String>(), onScreen.changedChannels(listOf(nbc), window, 0, 24 * h))
    }

    @Test
    fun theStoredSurvivorDoesNotDependOnServerOrder() {
        // nbcwave.us: 'Local Programming' 11:00-12:30 and 'Wild Kingdom'
        // 11:00-11:30 both start at 11:00; the server's order varies per request.
        val nbc = ch("NBC", "nbcwave.us")
        val local = p("nbcwave.us", "Local Programming", 11 * h, 12 * h + 30 * 60_000)
        val wild = p("nbcwave.us", "Mutual of Omaha's Wild Kingdom", 11 * h, 11 * h + 30 * 60_000)
        val earth = p("nbcwave.us", "Earth Odyssey", 11 * h + 30 * 60_000, 12 * h)
        val first = listOf(local, wild, earth); val second = listOf(earth, wild, local)
        assertEquals(GuideCatalog.asStored(first).toSet(), GuideCatalog.asStored(second).toSet())
        val onScreen = GuideCatalog.build(listOf(nbc), GuideCatalog.asStored(first), 0, 24 * h, stableOrder = true)
        assertEquals(emptySet<String>(), onScreen.changedChannels(listOf(nbc), second, 0, 24 * h))
    }
}

