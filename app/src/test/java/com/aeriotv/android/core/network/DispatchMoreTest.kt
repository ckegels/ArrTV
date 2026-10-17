package com.aeriotv.android.core.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DispatchMoreTest {
    private val wan = "https://tv.example.com"
    private val lan = "http://192.168.2.142:9191"
    private val a = "11111111-2222-3333-4444-555555555555"
    private val b = "66666666-7777-8888-9999-000000000000"

    private fun direct(base: String, uuid: String) = "$base/proxy/ts/stream/$uuid"

    @Before
    fun setUp() {
        DispatchMore.setDevice("3f2a9c1e-0b7d-4e21-9a55-0f1c2d3e4f50", "Living room SHIELD")
        DispatchMore.register(listOf(wan, lan), DispatchMore.Server(reports = true))
    }

    @After
    fun tearDown() {
        DispatchMore.unregister(listOf(wan, lan, "http://stock:9191"))
        DispatchMore.endMultiview()
        DispatchMore.setMaxVideo(0)
        com.aeriotv.android.core.playback.ArrTvOptimizations.identifyDevice = true
        com.aeriotv.android.core.playback.ArrTvOptimizations.sendMaxVideo = true
    }

    @Test
    fun theDecodeLimitGoesWithTheDeviceAndFollowsItsSwitch() {
        DispatchMore.setMaxVideo(1080)
        assertEquals("1080", DispatchMore.streamHeaders(direct(lan, a))[DispatchMore.HEADER_MAX_VIDEO])
        com.aeriotv.android.core.playback.ArrTvOptimizations.sendMaxVideo = false
        assertNull(DispatchMore.streamHeaders(direct(lan, a))[DispatchMore.HEADER_MAX_VIDEO])
        // The device itself is still said
        assertTrue(DispatchMore.streamHeaders(direct(lan, a)).containsKey(DispatchMore.HEADER_DEVICE))
    }

    @Test
    fun identifyOffSendsNothingAtAll() {
        DispatchMore.setMaxVideo(1080)
        com.aeriotv.android.core.playback.ArrTvOptimizations.identifyDevice = false
        assertTrue(DispatchMore.streamHeaders(direct(lan, b), previousUrl = direct(lan, a)).isEmpty())
        assertTrue(DispatchMore.deviceHeaders(direct(lan, a)).isEmpty())
    }

    @Test
    fun aStockServerGetsNothingNew() {
        assertTrue(DispatchMore.streamHeaders("http://stock:9191/proxy/ts/stream/$a").isEmpty())
        assertTrue(DispatchMore.deviceHeaders("http://stock:9191/api/channels/").isEmpty())
    }

    @Test
    fun aDispatchMoreServerIsToldTheDeviceOnBothRoutes() {
        for (base in listOf(wan, lan)) {
            val h = DispatchMore.streamHeaders(direct(base, a))
            assertEquals("3f2a9c1e-0b7d-4e21-9a55-0f1c2d3e4f50", h[DispatchMore.HEADER_DEVICE])
            assertEquals("Living room SHIELD", h[DispatchMore.HEADER_DEVICE_NAME])
            assertNull(h[DispatchMore.HEADER_PREVIOUS])
        }
        // The default port is the same server with or without it written
        assertTrue(DispatchMore.deviceHeaders("https://tv.example.com:443/api/core/version/").isNotEmpty())
    }

    @Test
    fun aChannelChangeNamesTheChannelLeftInTheFormItWasOpened() {
        assertEquals(a, DispatchMore.streamHeaders(direct(lan, b), previousUrl = direct(lan, a))[DispatchMore.HEADER_PREVIOUS])
        // Xtream: the number id, whatever the extension
        assertEquals(
            "1234",
            DispatchMore.previousChannel("$lan/live/tv/secret/5678.ts", "$lan/live/tv/secret/1234.m3u8"),
        )
    }

    @Test
    fun aRetryOrFailoverOfOneChannelLeavesNothing() {
        assertNull(DispatchMore.previousChannel(direct(lan, a), direct(lan, a)))
        assertNull(DispatchMore.previousChannel(direct(lan, a) + "?output_profile=2", direct(lan, a)))
    }

    @Test
    fun nothingIsLeftOnAnotherServerOrFromSomethingThatIsNotAChannel() {
        assertNull(DispatchMore.previousChannel(direct(lan, b), direct(wan, a)))
        assertNull(DispatchMore.previousChannel(direct(lan, b), "$lan/api/timeshift/abc/segment.ts"))
        assertNull(DispatchMore.previousChannel(direct(lan, b), "$lan/movie/tv/secret/99.mkv"))
        assertNull(DispatchMore.previousChannel(direct(lan, b), null))
    }

    @Test
    fun aTileCarriesTheMultiviewSession() {
        DispatchMore.startMultiview()
        val session = DispatchMore.multiviewSession!!
        val h = DispatchMore.streamHeaders(direct(lan, a), multiview = DispatchMore.multiviewSession)
        assertEquals(session, h[DispatchMore.HEADER_MULTIVIEW])
        // One session for as long as Multiview is open
        DispatchMore.startMultiview()
        assertEquals(session, DispatchMore.multiviewSession)
        DispatchMore.endMultiview()
        assertNull(DispatchMore.multiviewSession)
    }

    @Test
    fun theInstallIdIsKeptOrMadeAndTheNameIsSendable() {
        val made = DispatchMore.setDevice(null, "Salón — TV")
        assertTrue(Regex("^[A-Za-z0-9-]{8,64}$").matches(made))
        assertEquals("Salon TV", DispatchMore.deviceName)
        // A stored id in the wrong shape is replaced, never sent
        assertFalse(DispatchMore.setDevice("not an id!", "x") == "not an id!")
        assertEquals("keep-this-id-1", DispatchMore.setDevice("keep-this-id-1", "x"))
    }

    @Test
    fun aRefusedStallStopsStallsToThatServerOnly() {
        DispatchMore.register(listOf(lan), DispatchMore.Server(stallSwitch = true))
        DispatchMore.register(listOf(wan), DispatchMore.Server(stallSwitch = true))
        DispatchMore.stallRefused(direct(lan, a))
        assertEquals(false, DispatchMore.serverFor(direct(lan, a))?.stallSwitch)
        assertEquals(true, DispatchMore.serverFor(direct(wan, a))?.stallSwitch)
        // Still a Dispatch More server: the device headers go on
        assertTrue(DispatchMore.deviceHeaders(direct(lan, a)).isNotEmpty())
    }

    @Test
    fun theServersSurviveARestart() {
        val stored = DispatchMore.snapshot()
        DispatchMore.unregister(listOf(wan, lan))
        assertNull(DispatchMore.serverFor(direct(lan, a)))
        DispatchMore.restore(stored)
        assertEquals(true, DispatchMore.serverFor(direct(lan, a))?.reports)
        DispatchMore.restore("not json")
    }
}
