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
 *
 * Preset and manual (custom) values are remembered as "pending" even before an
 * audio session exists, so the app can restore them from disk on the next start.
 */
class AudioEffectEngine {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var pendingPreset: Preset? = null
    private var customBandGains: Map<Int, Int>? = null
    private var customBass: Short? = null
    private var customVirtualizer: Short? = null

    private val _activePreset = MutableStateFlow<Preset?>(null)
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

            val bass = BassBoost(0, sessionId)
            bassBoost = bass
            bass.enabled = true

            val virt = Virtualizer(0, sessionId)
            virtualizer = virt
            virt.enabled = true

            applyPending()
            refreshState()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach audio effects", e)
            release()
        }
    }

    fun applyPreset(preset: Preset) {
        pendingPreset = preset
        customBandGains = null
        customBass = null
        customVirtualizer = null
        val eq = equalizer ?: return
        preset.bandGainsMb.forEach { (band, gainMb) ->
            if (band in 0 until eq.numberOfBands.toInt()) {
                eq.setBandLevel(band.toShort(), gainMb.toShort())
            }
        }
        bassBoost?.setStrength(preset.bassStrength)
        virtualizer?.setStrength(preset.virtualizerStrength)
        _activePreset.value = preset
        refreshState()
    }

    fun setBandGain(band: Int, gainMb: Int) {
        val eq = equalizer ?: run {
            customBandGains = (customBandGains ?: emptyMap()) + (band to gainMb)
            return
        }
        if (band !in 0 until eq.numberOfBands.toInt()) return
        eq.setBandLevel(band.toShort(), gainMb.toShort())
        pendingPreset = null
        customBandGains = (customBandGains ?: currentGainsMap()) + (band to gainMb)
        _activePreset.value = null
        refreshState()
    }

    fun setBassStrength(strength: Short) {
        bassBoost?.run {
            setStrength(strength)
            pendingPreset = null
            customBass = strength
            _activePreset.value = null
        } ?: run { customBass = strength }
        refreshState()
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.run {
            setStrength(strength)
            pendingPreset = null
            customVirtualizer = strength
            _activePreset.value = null
        } ?: run { customVirtualizer = strength }
        refreshState()
    }

    /** Restores saved custom EQ values before a session is attached. */
    fun restoreCustom(gains: Map<Int, Int>, bass: Short, virtualizer: Short) {
        pendingPreset = null
        customBandGains = gains.ifEmpty { null }
        customBass = bass
        customVirtualizer = virtualizer
        applyPending()
        refreshState()
    }

    fun reset() {
        val eq = equalizer ?: return
        for (band in 0 until eq.numberOfBands.toInt()) {
            eq.setBandLevel(band.toShort(), 0)
        }
        bassBoost?.setStrength(0)
        virtualizer?.setStrength(0)
        pendingPreset = null
        customBandGains = null
        customBass = null
        customVirtualizer = null
        _activePreset.value = null
        _bandGains.value = List(eq.numberOfBands.toInt()) { 0 }
        _bassStrength.value = 0
        _virtualizerStrength.value = 0
    }

    fun centerFreqHz(band: Int): Int {
        val eq = equalizer ?: return 0
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

    private fun applyPending() {
        val eq = equalizer ?: return
        val preset = pendingPreset
        if (preset != null) {
            preset.bandGainsMb.forEach { (band, gainMb) ->
                if (band in 0 until eq.numberOfBands.toInt()) {
                    eq.setBandLevel(band.toShort(), gainMb.toShort())
                }
            }
            bassBoost?.setStrength(preset.bassStrength)
            virtualizer?.setStrength(preset.virtualizerStrength)
            _activePreset.value = preset
            return
        }
        customBandGains?.forEach { (band, gainMb) ->
            if (band in 0 until eq.numberOfBands.toInt()) {
                eq.setBandLevel(band.toShort(), gainMb.toShort())
            }
        }
        customBass?.let { bassBoost?.setStrength(it) }
        customVirtualizer?.let { virtualizer?.setStrength(it) }
        _activePreset.value = null
    }

    private fun currentGainsMap(): Map<Int, Int> =
        _bandGains.value.mapIndexedNotNull { index, gain -> if (gain != 0) index to gain else null }.toMap()

    private fun refreshState() {
        val eq = equalizer ?: return
        _bandGains.value = List(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }
        _bassStrength.value = bassBoost?.roundedStrength?.toInt() ?: 0
        _virtualizerStrength.value = virtualizer?.roundedStrength?.toInt() ?: 0
    }

    companion object {
        private const val TAG = "AudioEffectEngine"
    }
}
