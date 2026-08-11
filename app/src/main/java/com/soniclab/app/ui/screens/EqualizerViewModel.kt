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
import com.soniclab.player.AudioSpatialBridge
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

    fun reset() {
        container.audioEffects.reset()
        balance = 0f
        AudioBalanceBridge.balance = 0f
        selectSpatialMode(SpatialAudioProcessor.MODE_OFF)
        updateSpatialWidth(SpatialAudioProcessor.DEFAULT_WIDTH_STRENGTH)
        updateRotationSeconds(SpatialAudioProcessor.DEFAULT_ROTATION_SECONDS)
        viewModelScope.launch { container.settingsRepository.setActivePresetId("none") }
    }
}
