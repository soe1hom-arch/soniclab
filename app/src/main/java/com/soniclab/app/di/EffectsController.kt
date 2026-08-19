/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.di

import com.soniclab.core.model.Preset
import com.soniclab.player.AudioEqualizerBridge
import com.soniclab.player.AudioGainBridge
import com.soniclab.player.AudioReverbBridge
import com.soniclab.player.AudioSpatialBridge
import com.soniclab.player.AudioToneBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Software-only effects controller — the replacement for the old Android
 * AudioEffect (Equalizer/BassBoost/Virtualizer) engine. Everything now drives
 * the DSP processors inside the playback chain, so the 10-band EQ, bass
 * shelf and virtualizer:
 *
 * - behave identically on every device (no per-device band counts or
 *   failed session attachments),
 * - work even before playback starts,
 * - never double up with the software chain (no more BassBoost + bass shelf).
 *
 * Mapping from the legacy preset fields:
 * - [Preset.bandGainsMb] -> 10-band peaking EQ (mB -> dB),
 * - [Preset.bassStrength] 0..1000 -> bass low-shelf 0..+8 dB,
 * - [Preset.virtualizerStrength] 0..1000 -> 3D widening width (0..0.8),
 * - [Preset.reverbEnabled] -> room reverb wet mix,
 * - [Preset.loudnessBoostDb] -> per-track gain stage.
 */
class EffectsController {

    private val _activePreset = MutableStateFlow<Preset?>(null)
    val activePreset: StateFlow<Preset?> = _activePreset.asStateFlow()

    private val _bandGains = MutableStateFlow<List<Int>>(List(EqualizerBandCount) { 0 })
    val bandGains: StateFlow<List<Int>> = _bandGains.asStateFlow()

    private val _bassStrength = MutableStateFlow(0)
    val bassStrength: StateFlow<Int> = _bassStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    val bandCount: Int get() = AudioEqualizerBridge.bandCount

    fun centerFreqHz(band: Int): Int = AudioEqualizerBridge.centerFreqHz(band)

    fun bandLevelRange(): IntRange = AudioEqualizerBridge.bandLevelRange()

    fun applyPreset(preset: Preset) {
        _activePreset.value = preset
        // Full EQ definition from the preset (mB -> dB).
        AudioEqualizerBridge.resetAll()
        preset.bandGainsMb.forEach { (band, gainMb) ->
            AudioEqualizerBridge.setBandGain(band, gainMb)
        }
        // Bass: 0..1000 -> 0..+8 dB low-shelf (keeps the preset's character
        // without the old dual BassBoost path).
        AudioToneBridge.bassDb = (preset.bassStrength.coerceIn(0, 1000) / 1000f) * BASS_MAX_DB
        // A preset is a complete recipe for the sections it drives: virtualizer
        // turns 3D widening on when requested and off otherwise, so switching
        // presets (or back to Flat) never leaves a stale effect running.
        val width = (preset.virtualizerStrength.coerceIn(0, 1000) / 1000f) * VIRT_MAX_WIDTH
        if (preset.virtualizerStrength > 0) {
            AudioSpatialBridge.spatial3d = true
            AudioSpatialBridge.widthStrength = width
        } else {
            AudioSpatialBridge.spatial3d = false
        }
        // Preset room reverb (Cinema/Jazz) and legacy loudness boost. Reverb is
        // preset-driven too: 0 when the preset doesn't ask for it. The boost
        // lives in baseGainDb so system toggles (auto-normalize, Direct mode)
        // never wipe it.
        AudioReverbBridge.wetMix = if (preset.reverbEnabled) REVERB_DEFAULT_WET else 0f
        AudioGainBridge.baseGainDb = preset.loudnessBoostDb
        refreshState()
    }

    fun setBandGain(band: Int, gainMb: Int) {
        AudioEqualizerBridge.setBandGain(band, gainMb)
        _activePreset.value = null
        refreshState()
    }

    fun setBassStrength(strength: Short) {
        AudioToneBridge.bassDb = (strength.coerceIn(0, 1000) / 1000f) * BASS_MAX_DB
        _activePreset.value = null
        refreshState()
    }

    fun setVirtualizerStrength(strength: Short) {
        val v = strength.coerceIn(0, 1000)
        if (v > 0) {
            AudioSpatialBridge.spatial3d = true
            AudioSpatialBridge.widthStrength = (v / 1000f) * VIRT_MAX_WIDTH
        }
        _activePreset.value = null
        refreshState()
    }

    /** Restores saved custom EQ/bass/virtualizer values at app startup. */
    fun restoreCustom(gains: Map<Int, Int>, bass: Short, virtualizer: Short) {
        _activePreset.value = null
        AudioEqualizerBridge.resetAll()
        gains.forEach { (band, gainMb) -> AudioEqualizerBridge.setBandGain(band, gainMb) }
        setBassStrength(bass)
        setVirtualizerStrength(virtualizer)
        refreshState()
    }

    /**
     * Returns the preset-driven sound to a clean baseline: zeros EQ bands,
     * bass, preset reverb and the virtualizer's 3D widening. Balance, treble,
     * 8D/Surround and the rest are owned by their own sections.
     */
    fun reset() {
        AudioEqualizerBridge.resetAll()
        AudioToneBridge.bassDb = 0f
        AudioReverbBridge.wetMix = 0f
        AudioSpatialBridge.spatial3d = false
        _activePreset.value = null
        refreshState()
    }

    private fun refreshState() {
        _bandGains.value = AudioEqualizerBridge.gainsMb()
        _bassStrength.value = (AudioToneBridge.bassDb / BASS_MAX_DB * 1000f).toInt().coerceIn(0, 1000)
        _virtualizerStrength.value =
            (AudioSpatialBridge.widthStrength / VIRT_MAX_WIDTH * 1000f).toInt().coerceIn(0, 1000)
    }

    companion object {
        private const val EqualizerBandCount = 10
        private const val BASS_MAX_DB = 8f
        private const val VIRT_MAX_WIDTH = 0.8f
        private const val REVERB_DEFAULT_WET = 0.25f
    }
}
