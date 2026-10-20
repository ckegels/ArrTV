package com.aeriotv.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * List endpoints are decoded straight from the stream into their rows (2026-10-20: the
 * JsonElement tree of /api/epg/epgdata/ filled a Chromecast HD's heap). Both shapes a server
 * answers must still come out the same.
 */
class DispatcharrListDecodeTest {

    private val client = DispatcharrClient()

    @Before
    fun setUp() = com.aeriotv.android.testutil.EnglishStrings.install()

    private fun decode(body: String) =
        client.decodeListPage(body.byteInputStream(), DispatcharrEpgData.serializer(), "http://x/api/epg/epgdata/")

    @Test
    fun aBareArrayIsOnePage() {
        val page = decode(""" [{"id": 1, "tvg_id": "DasErste.de", "epg_source": 3, "name": "Das Erste", "icon_url": null},
            {"id": 2, "tvg_id": null}] """)
        assertFalse(page.envelope)
        assertEquals(listOf(1, 2), page.results.map { it.id })
        assertEquals("DasErste.de", page.results[0].tvgId)
        assertEquals(3, page.results[0].epgSourceId)
        assertNull(page.next)
    }

    @Test
    fun anEnvelopeKeepsItsCursorAndCount() {
        val page = decode("""{"count": 3, "next": "http://x/api/epg/epgdata/?page=2", "previous": null,
            "results": [{"id": 7, "tvg_id": "ZDF.de", "epg_source": 3, "source_type": "xmltv"}]}""")
        assertTrue(page.envelope)
        assertEquals(listOf(7), page.results.map { it.id })
        assertEquals("http://x/api/epg/epgdata/?page=2", page.next)
        assertEquals(3, page.count)
    }

    @Test
    fun anEmptyBodyIsNoRows() {
        val page = decode("  ")
        assertTrue(page.results.isEmpty())
        assertNull(page.next)
    }

    @Test(expected = DispatcharrError.UnexpectedResponse::class)
    fun anythingElseIsUnexpected() {
        decode("<html>502</html>")
    }
}
