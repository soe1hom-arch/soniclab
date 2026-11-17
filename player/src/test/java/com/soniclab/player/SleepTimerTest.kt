/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {
    @Test
    fun expiryClearsStateAndStopsPlayback() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var finished = 0
        var requestedDelay = 0L
        val timer = SleepTimer(this, { finished++ }) { milliseconds ->
            requestedDelay = milliseconds
            gate.await()
        }
        timer.start(15)
        yield()
        assertEquals(15, timer.minutes.value)
        assertEquals(900_000L, requestedDelay)
        gate.complete(Unit)
        yield()
        assertEquals(0, timer.minutes.value)
        assertEquals(1, finished)
    }

    @Test
    fun replacingOrCancellingTimerDoesNotFireOldTimer() = runBlocking {
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        var finished = 0
        val timer = SleepTimer(this, { finished++ }) {
            val gate = CompletableDeferred<Unit>()
            gates.add(gate)
            gate.await()
        }
        timer.start(15)
        yield()
        timer.start(30)
        yield()
        gates[0].complete(Unit)
        yield()
        assertEquals(30, timer.minutes.value)
        assertEquals(0, finished)
        timer.start(0)
        gates[1].complete(Unit)
        yield()
        assertEquals(0, timer.minutes.value)
        assertEquals(0, finished)
    }
}
