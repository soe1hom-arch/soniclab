/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soniclab.app.di.AppContainer
import com.soniclab.player.AudioSpatialBridge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerViewModel(private val container: AppContainer) : ViewModel() {
    val uiState = container.playerController.uiState
    val spectrum = container.visualizerEngine.spectrum
    val activePreset = container.audioEffects.activePreset
    val bandGains = container.audioEffects.bandGains
    val favorites = container.favoritesRepository.favorites
    val sleepTimerMinutes = container.playerController.sleepTimerMinutes
    val spatialState = AudioSpatialBridge.state

    private var lastAttachedSession = 0

    init {
        viewModelScope.launch {
            container.playerController.uiState.collectLatest { state ->
                if (state.hasTrack) {
                    val sessionId = container.playerController.audioSessionId
                    if (sessionId != 0 && sessionId != lastAttachedSession) {
                        lastAttachedSession = sessionId
                        container.visualizerEngine.attachTo(sessionId)
                    }
                }
            }
        }
    }

    fun toggleFavorite(id: Long) = container.favoritesRepository.toggle(id)

    fun setSleepTimer(minutes: Int) {
        container.playerController.startSleepTimer(minutes)
    }

    fun togglePlayPause() = container.playerController.togglePlayPause()
    fun playQueueAt(index: Int) = container.playerController.playTrackFromIndex(index)
    fun moveQueueItem(from: Int, to: Int) = container.playerController.moveQueueItem(from, to)
    fun next() = container.playerController.next()
    fun previous() = container.playerController.previous()
    fun seekTo(positionMs: Long) = container.playerController.seekTo(positionMs)
    fun setShuffle(enabled: Boolean) = container.playerController.setShuffle(enabled)

    fun cycleRepeat() {
        val current = container.playerController.uiState.value.repeatMode
        container.playerController.setRepeatMode((current + 1) % 3)
    }

    /** Applies a slider change immediately without writing DataStore on every drag frame. */
    fun previewSpeed(speed: Float) = container.playerController.setSpeed(speed)

    fun setSpeed(speed: Float) {
        previewSpeed(speed)
        persistSpeed(speed)
    }

    fun persistSpeed(speed: Float) {
        viewModelScope.launch { container.settingsRepository.setPlaybackSpeed(speed) }
    }

    fun selectSpatialMode(mode: Int) {
        AudioSpatialBridge.mode = mode
        viewModelScope.launch {
            container.settingsRepository.setSpatial(
                AudioSpatialBridge.mode,
                AudioSpatialBridge.spatial3d,
                AudioSpatialBridge.spatial8d,
                AudioSpatialBridge.surround
            )
        }
    }
}
