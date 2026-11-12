package com.aeriotv.android.feature.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommercialBreaksTest {
    private fun props(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `breaks come from comskip in milliseconds, in order`() {
        val breaks = CommercialBreaks.parse(props("""{"comskip": {"mode": "mark", "breaks": [[1500.0, 1680.0], [300.0, 480.5]]}}"""))
        assertEquals(listOf(300_000L..480_500L, 1_500_000L..1_680_000L), breaks)
    }

    @Test
    fun `no marks, no breaks`() {
        assertEquals(emptyList<LongRange>(), CommercialBreaks.parse(props("""{"comskip": {"status": "skipped"}}""")))
        assertEquals(emptyList<LongRange>(), CommercialBreaks.parse(props("""{"comskip": true}""")))
        assertEquals(emptyList<LongRange>(), CommercialBreaks.parse(null))
    }

    @Test
    fun `the break playing now, not its last second and a half`() {
        val breaks = listOf(300_000L..480_000L)
        assertEquals(breaks[0], CommercialBreaks.at(breaks, 300_000L))
        assertEquals(breaks[0], CommercialBreaks.at(breaks, 400_000L))
        assertNull(CommercialBreaks.at(breaks, 479_000L))
        assertNull(CommercialBreaks.at(breaks, 299_000L))
    }

    @Test
    fun `length reads as minutes`() {
        assertEquals("3:00", CommercialBreaks.length(300_000L..480_000L))
        assertEquals("45s", CommercialBreaks.length(0L..45_000L))
    }
}
