package com.aeriotv.android.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One silent re-login a minute, a 429 waited out, a rejected login parked (DispatcharrLoginGate). */
class DispatcharrLoginGateTest {
    private var now = 1_000_000L

    @Before
    fun setUp() {
        DispatcharrLoginGate.reset()
        DispatcharrLoginGate.clock = { now }
        DispatcharrLoginGate.log = {}
        DispatcharrLoginGate.warn = {}
    }

    @After
    fun tearDown() = DispatcharrLoginGate.reset()

    @Test
    fun callersAtTheSameTimeShareOneLogin() = runBlocking {
        var logins = 0
        val keys = (1..5).map {
            async {
                DispatcharrLoginGate.silentRelogin("playlist") {
                    logins++
                    delay(50)
                    "fresh-key"
                }
            }
        }.awaitAll()
        assertEquals(1, logins)
        assertEquals(List(5) { "fresh-key" }, keys)
    }

    @Test
    fun onlyOneLoginAMinute() = runBlocking {
        var logins = 0
        DispatcharrLoginGate.silentRelogin("playlist") { logins++; "a" }
        now += 30_000
        assertEquals("a", DispatcharrLoginGate.silentRelogin("playlist") { logins++; "b" })
        now += 31_000
        assertEquals("c", DispatcharrLoginGate.silentRelogin("playlist") { logins++; "c" })
        assertEquals(2, logins)
    }

    @Test
    fun aRejectedLoginIsParkedNotRetried() = runBlocking {
        var logins = 0
        assertNull(DispatcharrLoginGate.silentRelogin("playlist") { logins++; throw DispatcharrError.InvalidCredentials("no") })
        assertTrue(DispatcharrLoginGate.isRejected("playlist"))
        now += 61_000
        // ...until the minute is over
        assertEquals("ok", DispatcharrLoginGate.silentRelogin("playlist") { logins++; "ok" })
        assertEquals(2, logins)
    }

    @Test
    fun a429ParksEveryLoginToThatServer() {
        DispatcharrLoginGate.parkServer("http://192.168.2.142:9191", 90)
        assertEquals(90, DispatcharrLoginGate.serverParkedSeconds("http://192.168.2.142:9191/"))
        now += 91_000
        assertEquals(0, DispatcharrLoginGate.serverParkedSeconds("http://192.168.2.142:9191"))
        assertEquals(120L, DispatcharrLoginGate.parseRetryAfter(" 120 "))
        assertNull(DispatcharrLoginGate.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
    }
}
