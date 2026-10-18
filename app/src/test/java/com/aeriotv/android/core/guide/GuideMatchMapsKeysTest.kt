package com.aeriotv.android.core.guide

import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideMatchMapsKeysTest {
    private val h = 3_600_000L
    private fun disp(uuid: String, key: String, number: String, rawTvg: String? = null) = M3UChannel(
        id = "disp:$uuid", name = uuid, url = "http://srv/$uuid", tvgID = key, channelNumber = number,
        rawAttributes = rawTvg?.let { mapOf("tvg-id" to it) } ?: emptyMap(),
    )
    private fun p(key: String, title: String, start: Long, end: Long) =
        EPGProgramme(channelId = key, title = title, description = "", startMillis = start, endMillis = end, category = "")

    @Test
    fun rawKeysMirrorWhatTheMapsResolve() {
        val ch = disp("AAAA", " CBS.us ", "7", rawTvg = "cbs-hd")
        assertEquals(listOf("cbs.us", "cbs-hd", "aaaa"), GuideMatchMaps.rawKeysOf(ch, withNumber = false))
        assertEquals(listOf("cbs.us", "cbs-hd", "aaaa", "7"), GuideMatchMaps.rawKeysOf(ch, withNumber = true))
        val maps = GuideMatchMaps.build(listOf(ch))
        GuideMatchMaps.rawKeysOf(ch, withNumber = false).forEach { key ->
            assertEquals(key, setOf(ch.guideChannelId()), maps.resolve(key, GuideSource.GRID))
        }
    }

    @Test
    fun channelsSharingAGridKeyAreAllFound() {
        val a = disp("a", "shared.us", "1"); val b = disp("b", "shared.us", "2"); val c = disp("c", "own.us", "3")
        val maps = GuideMatchMaps.build(listOf(a, b, c))
        assertEquals(
            setOf(a.guideChannelId().value, b.guideChannelId().value),
            maps.channelsForGridKeys(GuideMatchMaps.rawKeysOf(a, withNumber = false)),
        )
    }

    @Test
    fun aStaleRawRowWinsOverTheNewRowUntilItIsDeleted() {
        // The 2026-09-25 Shield case: the day-chunk sweep stored 'Sheriff
        // Country' under the raw key; the live window wrote 'Big Brother'
        // under the canonical id. Dedup keeps the earlier row.
        val ch = disp("a", "cbs.us", "1")
        val stale = p("cbs.us", "Sheriff Country", 3 * h, 4 * h)
        val fresh = p(ch.guideChannelId().value, "Big Brother", 3 * h, 4 * h)
        val both = GuideCatalog.build(listOf(ch), listOf(stale, fresh), 0, 6 * h)
        assertEquals("Sheriff Country", both.index(ch.guideChannelId())!!.cellAt(3 * h + 1)?.title)
        val afterDelete = GuideCatalog.build(listOf(ch), listOf(fresh), 0, 6 * h)
        assertEquals("Big Brother", afterDelete.index(ch.guideChannelId())!!.cellAt(3 * h + 1)?.title)
    }
}
