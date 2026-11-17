/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import org.junit.Assert.assertEquals
import org.junit.Test

class UpcomingQueueTest {
    @Test
    fun showsAllOccurrencesAfterCurrentInPlaybackOrder() {
        assertEquals(listOf(1, 2, 3), upcomingQueueIndices(0, 4) { it + 1 })
        assertEquals(listOf(2, 0), upcomingQueueIndices(1, 3) { listOf(-1, 2, 0)[it] })
    }

    @Test
    fun stopsAtBoundariesAndCycles() {
        assertEquals(emptyList<Int>(), upcomingQueueIndices(-1, 3) { it + 1 })
        assertEquals(emptyList<Int>(), upcomingQueueIndices(2, 3) { it + 1 })
        assertEquals(listOf(1, 2), upcomingQueueIndices(0, 3) { (it + 1) % 3 })
    }
}
