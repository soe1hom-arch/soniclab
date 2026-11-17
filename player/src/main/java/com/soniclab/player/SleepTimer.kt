/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SleepTimer(
    private val scope: CoroutineScope,
    private val onFinished: () -> Unit,
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    private var job: Job? = null
    private val _minutes = MutableStateFlow(0)
    val minutes = _minutes.asStateFlow()

    fun start(minutes: Int) {
        job?.cancel()
        job = null
        _minutes.value = minutes.coerceAtLeast(0)
        if (minutes <= 0) return
        job = scope.launch {
            wait(minutes * 60_000L)
            _minutes.value = 0
            job = null
            onFinished()
        }
    }
}
