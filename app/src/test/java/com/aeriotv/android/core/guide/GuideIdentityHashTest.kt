package com.aeriotv.android.core.guide

import com.aeriotv.android.core.data.M3UChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideIdentityHashTest {
    private fun ch(n: Int, prefix: String = "disp:") =
        M3UChannel(id = "$prefix$n", name = "Channel $n", url = "http://p/$n", tvgID = "c$n.tv")

    @Test
    fun aFewChannelsMoreIsStillTheSameList() {
        val before = (1..1428).map { ch(it) }
        val after = before + (2000..2003).map { ch(it) }
        val s = GuideIdentityHash.similarity(GuideIdentityHash.signature(before), GuideIdentityHash.signature(after))
        assertTrue("similarity $s", s >= 0.9)
    }

    @Test
    fun aListKeyedAnotherWayIsNot() {
        val before = (1..1428).map { ch(it) }
        val rekeyed = (1..1428).map { ch(it, prefix = "m3u:") }
        val s = GuideIdentityHash.similarity(GuideIdentityHash.signature(before), GuideIdentityHash.signature(rekeyed))
        assertTrue("similarity $s", s < 0.5)
    }

    @Test
    fun theOrderDoesNotMatterAndNothingSignedIsNothingShared() {
        val list = (1..50).map { ch(it) }
        assertEquals(GuideIdentityHash.signature(list), GuideIdentityHash.signature(list.reversed()))
        assertEquals(0.0, GuideIdentityHash.similarity("", GuideIdentityHash.signature(list)), 0.0)
    }
}
