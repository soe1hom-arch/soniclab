/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

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
import com.soniclab.core.model.UserPreset
import com.soniclab.player.AudioBalanceBridge
import com.soniclab.player.AudioBypassBridge
import com.soniclab.player.AudioGainBridge
import com.soniclab.player.AudioHeadroomBridge
import com.soniclab.player.AudioLimiterBridge
import com.soniclab.player.AudioReverbBridge
import com.soniclab.player.AudioSpatialBridge
import com.soniclab.player.AudioToneBridge
import com.soniclab.player.SpatialAudioProcessor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class EqualizerViewModel(private val container: AppContainer) : ViewModel() {

    val bandGains = container.audioEffects.bandGains
    val activePreset = container.audioEffects.activePreset
    val bassStrength = container.audioEffects.bassStrength
    val presets: List<Preset> = Preset.presets
    val headroomDb = container.settingsRepository.headroomDb
    val effectsEnabled = container.settingsRepository.effectsEnabled
    val loudnessMatchEnabled = container.settingsRepository.loudnessMatchEnabled
    val userPresets = container.settingsRepository.userPresets

    var effectsBypassed by mutableStateOf(AudioBypassBridge.enabled)
        private set
    var outputPeakDb by mutableFloatStateOf(AudioLimiterBridge.processor.outputPeakDb)
        private set
    var gainReductionDb by mutableFloatStateOf(AudioLimiterBridge.processor.gainReductionDb)
        private set

    init {
        viewModelScope.launch {
            while (isActive) {
                outputPeakDb = AudioLimiterBridge.processor.outputPeakDb
                gainReductionDb = AudioLimiterBridge.processor.gainReductionDb
                delay(100)
            }
        }
    }

    fun setEffectsBypassed(enabled: Boolean) {
        effectsBypassed = enabled
        AudioBypassBridge.abBypass = enabled
    }

    fun setEffectsEnabled(enabled: Boolean) {
        AudioBypassBridge.masterEffectsEnabled = enabled
        viewModelScope.launch { container.settingsRepository.setEffectsEnabled(enabled) }
    }

    fun setLoudnessMatchEnabled(enabled: Boolean) {
        AudioGainBridge.loudnessMatchEnabled = enabled
        AudioHeadroomBridge.headroomDb = AudioHeadroomBridge.headroomDb
        viewModelScope.launch { container.settingsRepository.setLoudnessMatchEnabled(enabled) }
    }

    fun setHeadroom(value: Float) {
        val db = value.coerceIn(-3f, 0f)
        AudioHeadroomBridge.headroomDb = db
        viewModelScope.launch { container.settingsRepository.setHeadroomDb(db) }
    }

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

    fun saveCurrentPreset(name: String) {
        val cleanName = name.trim().replace("|", " ").replace("\n", " ")
        if (cleanName.isBlank()) return
        val preset = UserPreset(
            id = "user_${System.currentTimeMillis()}",
            name = cleanName,
            bandGainsMb = container.audioEffects.bandGains.value.mapIndexed { index, gain -> index to gain }.toMap(),
            bassStrength = bassStrength.value,
            balance = balance,
            trebleDb = trebleDb,
            spatialMode = spatialMode,
            spatial3d = spatial3d,
            spatial8d = spatial8d,
            surround = surround,
            spatialWidth = spatialWidth,
            rotationSeconds = rotationSeconds,
            panDepth = panDepth,
            reverbWet = reverbWet,
            reverbRoom = reverbRoom,
            pitchSemitones = playbackPitch,
            headroomDb = AudioHeadroomBridge.headroomDb
        )
        viewModelScope.launch { container.settingsRepository.saveUserPreset(preset) }
    }

    fun applyUserPreset(preset: UserPreset) {
        container.audioEffects.restoreCustom(preset.bandGainsMb, preset.bassStrength.toShort(), 0)
        AudioGainBridge.baseGainDb = 0f
        balance = preset.balance.coerceIn(-1f, 1f)
        AudioBalanceBridge.balance = balance
        trebleDb = preset.trebleDb.coerceIn(-6f, 6f)
        AudioToneBridge.trebleDb = trebleDb
        AudioSpatialBridge.mode = preset.spatialMode
        if (preset.spatialMode == SpatialAudioProcessor.MODE_CUSTOM) {
            AudioSpatialBridge.spatial3d = preset.spatial3d
            AudioSpatialBridge.spatial8d = preset.spatial8d
            AudioSpatialBridge.surround = preset.surround
        }
        spatialMode = AudioSpatialBridge.mode
        spatial3d = AudioSpatialBridge.spatial3d
        spatial8d = AudioSpatialBridge.spatial8d
        surround = AudioSpatialBridge.surround
        spatialWidth = preset.spatialWidth.coerceIn(0f, 1f)
        rotationSeconds = preset.rotationSeconds.coerceIn(4f, 60f)
        panDepth = preset.panDepth.coerceIn(0f, 1f)
        AudioSpatialBridge.widthStrength = spatialWidth
        AudioSpatialBridge.rotationSeconds = rotationSeconds
        AudioSpatialBridge.panDepth = panDepth
        reverbWet = preset.reverbWet.coerceIn(0f, 1f)
        reverbRoom = preset.reverbRoom.coerceIn(0f, 1f)
        AudioReverbBridge.wetMix = reverbWet
        AudioReverbBridge.roomSize = reverbRoom
        AudioHeadroomBridge.headroomDb = preset.headroomDb
        container.playerController.setPitchSemitones(preset.pitchSemitones)
        viewModelScope.launch {
            container.settingsRepository.setActivePresetId("none")
            container.settingsRepository.setBandGains(preset.bandGainsMb)
            container.settingsRepository.setBassStrength(preset.bassStrength)
            container.settingsRepository.setBalance(balance)
            container.settingsRepository.setTrebleDb(trebleDb)
            container.settingsRepository.setSpatial(
                AudioSpatialBridge.mode, spatial3d, spatial8d, surround
            )
            container.settingsRepository.setSpatialWidth(spatialWidth)
            container.settingsRepository.setRotationSeconds(rotationSeconds)
            container.settingsRepository.setPanDepth(panDepth)
            container.settingsRepository.setReverb(reverbWet, reverbRoom)
            container.settingsRepository.setPitchSemitones(preset.pitchSemitones)
            container.settingsRepository.setHeadroomDb(preset.headroomDb)
        }
    }

    fun deleteUserPreset(id: String) {
        viewModelScope.launch { container.settingsRepository.deleteUserPreset(id) }
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

    fun setLimiter(enabled: Boolean) {
        // Kept for callers from older UI versions; the final safety stage is
        // intentionally always active.
        AudioLimiterBridge.enabled = true
        viewModelScope.launch { container.settingsRepository.setLimiterEnabled(true) }
    }

    fun updateTreble(value: Float) {
        trebleDb = value.coerceIn(-6f, 6f)
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

    /** Atur ulang hanya bagian Prasetel Equalizer (band EQ, bass, virtualizer, balance). */
    fun resetPresetSection() {
        container.audioEffects.reset()
        balance = 0f
        AudioBalanceBridge.balance = 0f
        viewModelScope.launch {
            container.settingsRepository.setActivePresetId("none")
            container.settingsRepository.setBandGains(emptyMap())
            container.settingsRepository.setBassStrength(0)
            container.settingsRepository.setVirtualizerStrength(0)
            container.settingsRepository.setBalance(0f)
        }
    }

    /** Atur ulang hanya bagian Mode 3D/8D (mode, lebar, kecepatan, kedalaman pan). */
    fun resetSpatialSection() {
        selectSpatialMode(SpatialAudioProcessor.MODE_OFF)
        updateSpatialWidth(SpatialAudioProcessor.DEFAULT_WIDTH_STRENGTH)
        updateRotationSeconds(SpatialAudioProcessor.DEFAULT_ROTATION_SECONDS)
        updatePanDepth(SpatialAudioProcessor.DEFAULT_PAN_DEPTH)
    }

    /** Atur ulang hanya bagian Tone & Efek (treble, reverb, room, pitch, limiter). */
    fun resetToneSection() {
        updateTreble(0f)
        updateReverbWet(0f)
        updateReverbRoom(0.5f)
        resetPitch()
        // Reset returns to the transparent clean baseline. Enable the limiter
        // manually when using aggressive EQ/preset boosts.
        setLimiter(true)
    }

    fun reset() {
        resetPresetSection()
        resetSpatialSection()
        resetToneSection()
    }
}
