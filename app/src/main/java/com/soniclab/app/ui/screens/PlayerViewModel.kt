package com.soniclab.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.soniclab.app.di.AppContainer
import com.soniclab.player.AudioSpatialBridge
import com.soniclab.player.SpatialAudioProcessor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    val uiState = container.playerController.uiState
    val spectrum = container.visualizerEngine.spectrum
    val activePreset = container.audioEffects.activePreset
    val bandGains = container.audioEffects.bandGains

    var spatialMode by mutableIntStateOf(AudioSpatialBridge.mode)
        private set

    private var attachedSession = 0

    init {
        viewModelScope.launch {
            container.playerController.uiState.collectLatest { state ->
                if (state.hasTrack) {
                    val sessionId = container.playerController.audioSessionId
                    if (sessionId != 0 && sessionId != attachedSession) {
                        container.audioEffects.attachTo(sessionId)
                        container.visualizerEngine.attachTo(sessionId)
                        attachedSession = sessionId
                    }
                }
            }
        }
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
    fun setSpeed(speed: Float) = container.playerController.setSpeed(speed)

    fun toggleSpatialMode(mode: Int) {
        val next = if (spatialMode == mode) SpatialAudioProcessor.MODE_OFF else mode
        spatialMode = next
        AudioSpatialBridge.mode = next
    }

}
