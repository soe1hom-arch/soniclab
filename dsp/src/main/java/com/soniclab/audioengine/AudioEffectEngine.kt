package com.soniclab.audioengine

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.soniclab.core.model.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps the Android AudioEffect APIs (Equalizer, BassBoost, Virtualizer).
 * Effects are attached to the player's audio session id and released on [release].
 */
class AudioEffectEngine {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var _activePreset = MutableStateFlow<Preset?>(null)
    val activePreset: StateFlow<Preset?> = _activePreset.asStateFlow()

    private val _bandGains = MutableStateFlow<List<Int>>(emptyList())
    val bandGains: StateFlow<List<Int>> = _bandGains.asStateFlow()

    private val _bassStrength = MutableStateFlow(0)
    val bassStrength: StateFlow<Int> = _bassStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    val bandCount: Int
        get() = equalizer?.numberOfBands?.toInt() ?: 0

    fun attachTo(sessionId: Int) {
        if (sessionId == 0) return
        release()
        try {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            eq.enabled = true
            _bandGains.value = List(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }

            val bass = BassBoost(0, sessionId)
            bassBoost = bass
            bass.enabled = true
            _bassStrength.value = bass.roundedStrength.toInt()

            val virt = Virtualizer(0, sessionId)
            virtualizer = virt
            virt.enabled = true
            _virtualizerStrength.value = virt.roundedStrength.toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach audio effects", e)
            release()
        }
    }

    fun applyPreset(preset: Preset) {
        val eq = equalizer ?: return
        preset.bandGainsMb.forEach { (band, gainMb) ->
            if (band in 0 until eq.numberOfBands.toInt()) {
                eq.setBandLevel(band.toShort(), gainMb.toShort())
            }
        }
        bassBoost?.setStrength(preset.bassStrength)
        virtualizer?.setStrength(preset.virtualizerStrength)
        _bassStrength.value = preset.bassStrength.toInt()
        _virtualizerStrength.value = preset.virtualizerStrength.toInt()
        _activePreset.value = preset
        _bandGains.value = List(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }
    }

    fun setBandGain(band: Int, gainMb: Int) {
        val eq = equalizer ?: return
        if (band !in 0 until eq.numberOfBands.toInt()) return
        eq.setBandLevel(band.toShort(), gainMb.toShort())
        _activePreset.value = null // manual adjustment clears preset
        _bandGains.value = List(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }
    }

    fun setBassStrength(strength: Short) {
        bassBoost?.setStrength(strength)
        _bassStrength.value = strength.toInt().coerceIn(0, 1000)
        _activePreset.value = null
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.setStrength(strength)
        _virtualizerStrength.value = strength.toInt().coerceIn(0, 1000)
        _activePreset.value = null
    }

    fun reset() {
        val eq = equalizer ?: return
        for (band in 0 until eq.numberOfBands.toInt()) {
            eq.setBandLevel(band.toShort(), 0)
        }
        bassBoost?.setStrength(0)
        virtualizer?.setStrength(0)
        _bassStrength.value = 0
        _virtualizerStrength.value = 0
        _activePreset.value = null
        _bandGains.value = List(eq.numberOfBands.toInt()) { 0 }
    }

    fun centerFreqHz(band: Int): Int {
        val eq = equalizer ?: return 0
        val range = eq.bandLevelRange
        return if (band in 0 until eq.numberOfBands.toInt()) eq.getCenterFreq(band.toShort()).toInt() else 0
    }

    fun bandLevelRange(): IntRange {
        val eq = equalizer ?: return 0..0
        val range = eq.bandLevelRange
        return range[0].toInt()..range[1].toInt()
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    companion object {
        private const val TAG = "AudioEffectEngine"
    }
}
