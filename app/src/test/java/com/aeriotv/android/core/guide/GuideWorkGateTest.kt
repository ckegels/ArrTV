package com.aeriotv.android.core.guide

import com.aeriotv.android.core.playback.PlaybackActivityTracker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Heavy guide work waits while a channel starts or memory is tight (GuideWorkGate). */
class GuideWorkGateTest {
    private var starting = false
    private var heap = 0.3
    private var now = 0L
    private val logged = ArrayList<String>()
    private val saved = listOf(GuideWorkGate.starting, GuideWorkGate.heapUse, GuideWorkGate.clock, GuideWorkGate.log)

    @Before
    fun fake() {
        GuideWorkGate.starting = { starting }
        GuideWorkGate.heapUse = { heap }
        GuideWorkGate.clock = { now }
        GuideWorkGate.log = { logged += it }
    }

    @After
    @Suppress("UNCHECKED_CAST")
    fun restore() {
        GuideWorkGate.starting = saved[0] as () -> Boolean
        GuideWorkGate.heapUse = saved[1] as () -> Double
        GuideWorkGate.clock = saved[2] as () -> Long
        GuideWorkGate.log = saved[3] as (String) -> Unit
    }

    @Test
    fun calmGoesStraightThroughWithoutALogLine() = runBlocking {
        GuideWorkGate.awaitCalm("guide rebuild")
        assertTrue(logged.isEmpty())
        assertNull(GuideWorkGate.reason())
    }

    @Test
    fun aChannelStartingHoldsTheWorkUntilItSettled() = runBlocking {
        starting = true
        val polls = Thread {
            Thread.sleep(1200)
            starting = false
        }
        polls.start()
        GuideWorkGate.awaitCalm("guide download")
        polls.join()
        assertTrue(logged[0], logged[0].contains("guide download waits: a channel is starting"))
        assertTrue(logged[1], logged[1].contains("guide download goes ahead"))
    }

    @Test
    fun aTightHeapSaysHowFull() {
        heap = 0.83
        assertEquals("memory is tight (heap 83 % full)", GuideWorkGate.reason())
        heap = 0.69
        assertNull(GuideWorkGate.reason())
    }

    @Test
    fun neverHeldBackForGood() = runBlocking {
        heap = 0.95
        GuideWorkGate.clock = { now.also { now += 7_000 } }
        GuideWorkGate.awaitCalm("enrichment")
        assertTrue(logged.last(), logged.last().contains("enrichment goes ahead after"))
        assertTrue(logged.last(), logged.last().contains("memory is tight"))
    }

    @Test
    fun aTuneCountsAsStartingUntilFiveSecondsAfterItsFirstFrame() {
        val t = 1_000_000L
        PlaybackActivityTracker.tuneStartedAt(t)
        assertTrue(PlaybackActivityTracker.startingAt(t + 1_000))
        assertTrue(PlaybackActivityTracker.startingAt(t + 19_000))
        assertFalse("a tune with no frame holds nothing back past 20 s", PlaybackActivityTracker.startingAt(t + 21_000))
        val f = t + 3_000
        PlaybackActivityTracker.firstFrameAt(f)
        assertTrue(PlaybackActivityTracker.startingAt(f + 4_000))
        assertFalse(PlaybackActivityTracker.startingAt(f + 6_000))
        // A new tune starts the wait again, and an old frame does not count for it
        PlaybackActivityTracker.tuneStartedAt(f + 10_000)
        assertTrue(PlaybackActivityTracker.startingAt(f + 11_000))
    }
}
