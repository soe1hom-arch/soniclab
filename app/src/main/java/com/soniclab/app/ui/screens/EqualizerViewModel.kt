package com.soniclab.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    var spatial3d by mutableStateOf(AudioSpatialBridge.spatial3d)
        private set
    var spatial8d by mutableStateOf(AudioSpatialBridge.spatial8d)
        private set
    var surround by mutableStateOf(AudioSpatialBridge.surround)
        private set
    var spatialWidth by mutableFloatStateOf(AudioSpatialBridge.widthStrength)
        private set
    var rotationSeconds by mutableFloatStateOf(AudioSpatialBridge.rotationSeconds)
        private set
    var panDepth by mutableFloatStateOf(AudioSpatialBridge.panDepth)
        private set

    fun applyPreset(preset: Preset) {
        container.audioEffects.applyPreset(preset)
        viewModelScope.launch { container.settingsRepository.setActivePresetId(preset.id) }
    }

    fun setBandGain(band: Int, gainMb: Int) {
        container.audioEffects.setBandGain(band, gainMb)
        viewModelScope.launch {
            container.settingsRepository.setBandGains(
                container.audioEffects.bandGains.value.mapIndexedNotNull { i, g -> i to g }.toMap()
            )
            container.settingsRepository.setActivePresetId("none")
        }
    }

    fun setBass(value: Int) {
        container.audioEffects.setBassStrength(value.coerceIn(0, 1000).toShort())
        viewModelScope.launch {
            container.settingsRepository.setBassStrength(value.coerceIn(0, 1000))
            container.settingsRepository.setActivePresetId("none")
        }
    }

    fun setVirtualizer(value: Int) {
        container.audioEffects.setVirtualizerStrength(value.coerceIn(0, 1000).toShort())
        viewModelScope.launch {
            container.settingsRepository.setVirtualizerStrength(value.coerceIn(0, 1000))
            container.settingsRepository.setActivePresetId("none")
        }
    }

    fun updateBalance(value: Float) {
        balance = value.coerceIn(-1f, 1f)
        AudioBalanceBridge.balance = balance
        viewModelScope.launch { container.settingsRepository.setBalance(balance) }
    }

    fun selectSpatialMode(mode: Int) {
        spatialMode = mode
        AudioSpatialBridge.mode = mode
        spatial3d = AudioSpatialBridge.spatial3d
        spatial8d = AudioSpatialBridge.spatial8d
        surround = AudioSpatialBridge.surround
        persistSpatial()
    }

    fun set3dEnabled(enabled: Boolean) {
        AudioSpatialBridge.spatial3d = enabled
        syncSpatialState()
        persistSpatial()
    }

    fun set8dEnabled(enabled: Boolean) {
        AudioSpatialBridge.spatial8d = enabled
        syncSpatialState()
        persistSpatial()
    }

    fun setSurroundEnabled(enabled: Boolean) {
        AudioSpatialBridge.surround = enabled
        syncSpatialState()
        persistSpatial()
    }

    fun updateSpatialWidth(value: Float) {
        spatialWidth = value.coerceIn(0f, 1f)
        AudioSpatialBridge.widthStrength = spatialWidth
        viewModelScope.launch { container.settingsRepository.setSpatialWidth(spatialWidth) }
    }

    fun updateRotationSeconds(value: Float) {
        rotationSeconds = value.coerceIn(4f, 60f)
        AudioSpatialBridge.rotationSeconds = rotationSeconds
        viewModelScope.launch { container.settingsRepository.setRotationSeconds(rotationSeconds) }
    }

    fun updatePanDepth(value: Float) {
        panDepth = value.coerceIn(0f, 1f)
        AudioSpatialBridge.panDepth = panDepth
        viewModelScope.launch { container.settingsRepository.setPanDepth(panDepth) }
    }

    private fun syncSpatialState() {
        spatialMode = AudioSpatialBridge.mode
        spatial3d = AudioSpatialBridge.spatial3d
        spatial8d = AudioSpatialBridge.spatial8d
        surround = AudioSpatialBridge.surround
    }

    private fun persistSpatial() {
        viewModelScope.launch {
            container.settingsRepository.setSpatial(
                AudioSpatialBridge.mode,
                AudioSpatialBridge.spatial3d,
                AudioSpatialBridge.spatial8d,
                AudioSpatialBridge.surround
            )
        }
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
        viewModelScope.launch { container.settingsRepository.setTrebleDb(trebleDb) }
    }

    fun updateReverbWet(value: Float) {
        reverbWet = value.coerceIn(0f, 1f)
        AudioReverbBridge.wetMix = reverbWet
        persistReverb()
    }

    fun updateReverbRoom(value: Float) {
        reverbRoom = value.coerceIn(0f, 1f)
        AudioReverbBridge.roomSize = reverbRoom
        persistReverb()
    }

    private fun persistReverb() {
        viewModelScope.launch { container.settingsRepository.setReverb(reverbWet, reverbRoom) }
    }

    fun updatePitch(value: Float) {
        container.playerController.setPitchSemitones(value)
        viewModelScope.launch { container.settingsRepository.setPitchSemitones(value.coerceIn(-12f, 12f)) }
    }

    fun resetPitch() {
        container.playerController.resetPitch()
        viewModelScope.launch { container.settingsRepository.setPitchSemitones(0f) }
    }

    fun reset() {
        container.audioEffects.reset()
        balance = 0f
        AudioBalanceBridge.balance = 0f
        selectSpatialMode(SpatialAudioProcessor.MODE_OFF)
        updateSpatialWidth(SpatialAudioProcessor.DEFAULT_WIDTH_STRENGTH)
        updateRotationSeconds(SpatialAudioProcessor.DEFAULT_ROTATION_SECONDS)
        updatePanDepth(SpatialAudioProcessor.DEFAULT_PAN_DEPTH)
        updateTreble(0f)
        updateReverbWet(0f)
        updateReverbRoom(0.5f)
        resetPitch()
        viewModelScope.launch {
            container.settingsRepository.setActivePresetId("none")
            container.settingsRepository.setBandGains(emptyMap())
            container.settingsRepository.setBassStrength(0)
            container.settingsRepository.setVirtualizerStrength(0)
        }
    }
}
