package com.aeriotv.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelListDiffTest {
    private fun ch(id: String, name: String = "Ch $id", number: String? = id, group: String = "News") = M3UChannel(
        id = "disp:$id", name = name, url = "http://srv/proxy/ts/stream/$id",
        groupTitle = group, channelNumber = number,
    )

    private val lineup = listOf(ch("1"), ch("2"), ch("3"))

    @Test
    fun identicalListsHaveNoChanges() {
        val diff = ChannelListDiff.between(lineup, lineup.map { it.copy() })
        assertFalse(diff.hasChanges)
    }

    @Test
    fun fieldsTheSnapshotDoesNotRoundTripAreIgnored() {
        val reparsed = lineup.map { it.copy(rawAttributes = mapOf("tvg-id" to "x")) }
        assertFalse(ChannelListDiff.between(lineup, reparsed).hasChanges)
    }

    @Test
    fun addedAndRemovedChannelsAreCounted() {
        val diff = ChannelListDiff.between(lineup, listOf(ch("1"), ch("3"), ch("4"), ch("5")))
        assertEquals(2, diff.added)
        assertEquals(1, diff.removed)
        assertEquals(0, diff.changed)
        assertFalse(diff.reordered)
        assertTrue(diff.hasChanges)
    }

    @Test
    fun renamedRenumberedOrRegroupedChannelsCountAsChanged() {
        val edited = listOf(ch("1", name = "Renamed"), ch("2", number = "20"), ch("3", group = "Sports"))
        val diff = ChannelListDiff.between(lineup, edited)
        assertEquals(3, diff.changed)
        assertEquals(0, diff.added)
        assertEquals(0, diff.removed)
    }

    @Test
    fun sameChannelsInANewOrderAreReordered() {
        val diff = ChannelListDiff.between(lineup, lineup.reversed())
        assertTrue(diff.reordered)
        assertTrue(diff.hasChanges)
    }

    @Test
    fun emptyToFullIsAllAdded() {
        val diff = ChannelListDiff.between(emptyList(), lineup)
        assertEquals(3, diff.added)
        assertFalse(diff.reordered)
    }
}
