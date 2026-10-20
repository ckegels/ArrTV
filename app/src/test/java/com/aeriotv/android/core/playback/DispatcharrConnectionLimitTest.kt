package com.aeriotv.android.core.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DispatcharrConnectionLimitTest {

    @Before
    fun setUp() = com.aeriotv.android.testutil.EnglishStrings.install()

    @After
    fun tearDown() {
        DispatcharrConnectionLimit.directConnectActive = false
    }

    @Test
    fun anotherTvTookTheStreamAndIsNamed() {
        val notice = DispatcharrConnectionLimit.fromResponse(
            409,
            """{"error": "Playing on another device: Bedroom TV", "device_replaced": true, "by": "Bedroom TV"}""",
        )!!
        assertEquals(DispatcharrConnectionLimit.Kind.OTHER_DEVICE, notice.kind)
        assertEquals("Playing on Bedroom TV", notice.title)
        // An older server does not say which device: no Join
        assertEquals(null, notice.byDevice)
    }

    @Test
    fun theDeviceThatTookOverCanBeJoined() {
        val notice = DispatcharrConnectionLimit.fromResponse(
            409,
            """{"error": "Playing on another device: SHIELD", "device_replaced": true, "by": "SHIELD", "by_device": "shield-1"}""",
        )!!
        assertEquals("shield-1", notice.byDevice)
    }

    @Test
    fun anXtreamPlaylistOnDispatchMoreIsToldToo() {
        // Only Dispatch More sends it, so the Direct Connect gate does not apply
        DispatcharrConnectionLimit.directConnectActive = false
        val notice = DispatcharrConnectionLimit.fromResponse(409, """{"device_replaced": true, "by": ""}""")!!
        assertEquals("Playing on another device", notice.title)
    }

    @Test
    fun anyOther409IsNotIt() {
        DispatcharrConnectionLimit.directConnectActive = true
        // The surfing delay's answer for a channel passed while zapping
        assertNull(DispatcharrConnectionLimit.fromResponse(409, """{"error": "A newer channel was requested by this player"}"""))
        assertNull(DispatcharrConnectionLimit.fromResponse(409, """{"device_replaced": false}"""))
        assertNull(DispatcharrConnectionLimit.fromResponse(503, """{"device_replaced": true}"""))
    }
}
