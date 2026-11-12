package com.aeriotv.android.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Movies and series decode whatever shape Dispatcharr sends their extras in (from AerioTV c4e82d09, PR 92). */
class VodLenientDecodeTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun customPropertiesSentAsAJsonStringStillDecode() {
        val asString = json.decodeFromString(
            DispatcharrVODEpisode.serializer(),
            """{"id":1,"name":"Pilot","custom_properties":"{\"movie_image\":\"http://a/still.jpg\"}"}""",
        )
        assertEquals("http://a/still.jpg", asString.customProperties?.stringField("movie_image"))
    }

    @Test
    fun anEpisodeTakesItsStillFromTheTopLevelWhenItIsThere() {
        val e = json.decodeFromString(
            DispatcharrVODEpisode.serializer(),
            """{"id":2,"name":"Two","movie_image":"http://a/top.jpg"}""",
        )
        assertTrue(e.toString(), e.movieImage == "http://a/top.jpg")
    }
}
