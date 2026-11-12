package com.aeriotv.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DispatcharrLiveConnectionTest {
    @Test
    fun backoffGrowsAndCapsAtFiveMinutes() {
        val waits = (0..8).map { DispatcharrLiveConnection.backoffMs(it, rateLimited = false) }
        assertEquals(listOf(5_000L, 10_000L, 30_000L, 60_000L, 120_000L, 300_000L, 300_000L, 300_000L, 300_000L), waits)
    }

    @Test
    fun rateLimitedLoginWaitsAtLeastTwoMinutes() {
        assertEquals(120_000L, DispatcharrLiveConnection.backoffMs(0, rateLimited = true))
        assertEquals(300_000L, DispatcharrLiveConnection.backoffMs(9, rateLimited = true))
    }

    @Test
    fun negativeFailureCountIsTreatedAsFirst() {
        assertEquals(5_000L, DispatcharrLiveConnection.backoffMs(-1, rateLimited = false))
    }
}
