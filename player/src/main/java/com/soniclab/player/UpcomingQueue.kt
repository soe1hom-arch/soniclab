/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

internal fun upcomingQueueIndices(
    currentIndex: Int,
    itemCount: Int,
    nextIndex: (Int) -> Int
): List<Int> {
    if (currentIndex !in 0 until itemCount) return emptyList()
    val visited = mutableSetOf(currentIndex)
    val result = mutableListOf<Int>()
    var index = currentIndex
    while (true) {
        index = nextIndex(index)
        if (index !in 0 until itemCount || !visited.add(index)) break
        result.add(index)
    }
    return result
}
