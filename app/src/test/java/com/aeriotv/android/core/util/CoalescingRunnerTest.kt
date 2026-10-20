package com.aeriotv.android.core.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** One guide rebuild at a time, the requests meanwhile folded into one more (CoalescingRunner). */
class CoalescingRunnerTest {

    @Test
    fun requestsDuringARunBecomeOneMoreRun() = runBlocking {
        val runs = java.util.Collections.synchronizedList(ArrayList<List<String>>())
        val gate = CompletableDeferred<Unit>()
        val runner = CoalescingRunner<String>(this) { reasons, _ ->
            runs += reasons
            if (runs.size == 1) gate.await()
        }
        val first = async { runner.request("cache", "p") }
        delay(50)
        val others = listOf("channels-update", "enrich", "fetch", "jump").map { r ->
            async { runner.request(r, "p") }
        }
        delay(50)
        assertEquals(1, runs.size)
        gate.complete(Unit)
        (others + first).forEach { it.await() }
        assertEquals(listOf(listOf("cache"), listOf("channels-update", "enrich", "fetch", "jump")), runs.toList())
    }

    @Test
    fun neverTwoAtOnce() = runBlocking {
        val inside = AtomicInteger()
        val most = AtomicInteger()
        val runner = CoalescingRunner<Int>(this + Dispatchers.Default) { _, _ ->
            most.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
            delay(30)
            inside.decrementAndGet()
        }
        val jobs = (1..20).map { i -> launch(Dispatchers.Default) { delay(i * 7L); runner.request("r$i", i) } }
        jobs.forEach { it.join() }
        assertEquals(1, most.get())
    }

    @Test
    fun theNewestArgumentIsTheOneUsed() = runBlocking {
        val used = java.util.Collections.synchronizedList(ArrayList<Int>())
        val gate = CompletableDeferred<Unit>()
        val runner = CoalescingRunner<Int>(this) { _, value ->
            used += value
            if (used.size == 1) gate.await()
        }
        val a = async { runner.request("a", 1) }
        delay(50)
        val b = async { runner.request("b", 2) }
        val c = async { runner.request("c", 3) }
        delay(50)
        gate.complete(Unit)
        listOf(a, b, c).forEach { it.await() }
        assertEquals(listOf(1, 3), used.toList())
    }

    @Test
    fun aCancelledCallerDoesNotCancelTheRunOthersWaitFor() = runBlocking {
        val finished = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val runner = CoalescingRunner<Unit>(this) { _, _ ->
            gate.await()
            finished.incrementAndGet()
        }
        val leaving = launch { runner.request("jump", Unit) }
        val staying = async { runner.request("enrich", Unit) }
        delay(50)
        leaving.cancel()
        gate.complete(Unit)
        staying.await()
        assertEquals(1, finished.get())
        assertTrue(leaving.isCancelled)
    }

    @Test
    fun aRequestAfterARunStartsANewOne() = runBlocking {
        var runs = 0
        val runner = CoalescingRunner<Unit>(this) { _, _ -> runs++ }
        runner.request("a", Unit)
        runner.request("b", Unit)
        assertEquals(2, runs)
    }
}
