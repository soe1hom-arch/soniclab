package com.soniclab.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soniclab.app.di.AppContainer
import com.soniclab.core.model.Preset
import com.soniclab.player.AudioBalanceBridge
import com.soniclab.player.AudioReverbBridge
import com.soniclab.player.AudioSpatialBridge
import com.soniclab.player.AudioToneBridge
import com.soniclab.player.SpatialAudioProcessor
import kotlinx.coroutines.launch

class EqualizerViewModel(private val container: AppContainer) : ViewModel() {

    val bandGains = container.audioEffects.bandGains
    val activePreset = container.audioEffects.activePreset
    val bassStrength = container.audioEffects.bassStrength
    val virtualizerStrength = container.audioEffects.virtualizerStrength
    val presets: List<Preset> = Preset.presets

    var balance by mutableFloatStateOf(AudioBalanceBridge.balance)
        private set

    var spatialMode by mutableIntStateOf(AudioSpatialBridge.mode)
        private set
    var spatialWidth by mutableFloatStateOf(AudioSpatialBridge.widthStrength)
        private set
    var rotationSeconds by mutableFloatStateOf(AudioSpatialBridge.rotationSeconds)
        private set

    fun applyPreset(preset: Preset) {
        container.audioEffects.applyPreset(preset)
        viewModelScope.launch { container.settingsRepository.setActivePresetId(preset.id) }
    }

    fun setBandGain(band: Int, gainMb: Int) = container.audioEffects.setBandGain(band, gainMb)

    fun setBass(value: Int) = container.audioEffects.setBassStrength(value.coerceIn(0, 1000).toShort())

    fun setVirtualizer(value: Int) = container.audioEffects.setVirtualizerStrength(value.coerceIn(0, 1000).toShort())

    fun updateBalance(value: Float) {
        balance = value.coerceIn(-1f, 1f)
        AudioBalanceBridge.balance = balance
    }

    fun selectSpatialMode(mode: Int) {
        spatialMode = mode
        AudioSpatialBridge.mode = mode
    }

    fun updateSpatialWidth(value: Float) {
        spatialWidth = value.coerceIn(0f, 1f)
        AudioSpatialBridge.widthStrength = spatialWidth
    }

    fun updateRotationSeconds(value: Float) {
        rotationSeconds = value.coerceIn(4f, 60f)
        AudioSpatialBridge.rotationSeconds = rotationSeconds
    }

    var trebleDb by mutableFloatStateOf(AudioToneBridge.trebleDb)
        private set

    var reverbWet by mutableFloatStateOf(AudioReverbBridge.wetMix)
        private set

    var reverbRoom by mutableFloatStateOf(AudioReverbBridge.roomSize)
        private set

    val playbackPitch: Float
        get() = container.playerController.uiState.value.playbackPitch

    fun updateTreble(value: Float) {
        trebleDb = value.coerceIn(-12f, 12f)
        AudioToneBridge.trebleDb = trebleDb
    }

    fun updateReverbWet(value: Float) {
        reverbWet = value.coerceIn(0f, 1f)
        AudioReverbBridge.wetMix = reverbWet
    }

    fun updateReverbRoom(value: Float) {
        reverbRoom = value.coerceIn(0f, 1f)
        AudioReverbBridge.roomSize = reverbRoom
    }

    fun updatePitch(value: Float) {
        container.playerController.setPitchSemitones(value)
    }

    fun resetPitch() {
        container.playerController.resetPitch()
    }

    fun reset() {
        container.audioEffects.reset()
        balance = 0f
        AudioBalanceBridge.balance = 0f
        selectSpatialMode(SpatialAudioProcessor.MODE_OFF)
        updateSpatialWidth(SpatialAudioProcessor.DEFAULT_WIDTH_STRENGTH)
        updateRotationSeconds(SpatialAudioProcessor.DEFAULT_ROTATION_SECONDS)
        updateTreble(0f)
        updateReverbWet(0f)
        updateReverbRoom(0.5f)
        resetPitch()
        viewModelScope.launch { container.settingsRepository.setActivePresetId("none") }
    }
}
