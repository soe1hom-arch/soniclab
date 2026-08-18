/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Single-process bridge between the app and the playback chain's 10-band
 * software EQ. The service reads [processor]; the UI sets band gains in
 * millibels (mB). The gain array is swapped atomically (never mutated in
 * place), so updates from the UI thread are safe against the audio thread.
 */
object AudioEqualizerBridge {
    val processor = EqualizerAudioProcessor()

    val bandCount: Int get() = EqualizerAudioProcessor.BAND_COUNT

    fun centerFreqHz(band: Int): Int {
        val hz = EqualizerAudioProcessor.CENTER_FREQS.getOrNull(band) ?: return 0
        return hz.toInt()
    }

    fun bandLevelRange(): IntRange =
        EqualizerAudioProcessor.MIN_GAIN_MB..EqualizerAudioProcessor.MAX_GAIN_MB

    fun setBandGain(band: Int, gainMb: Int) {
        val b = band.coerceIn(0, EqualizerAudioProcessor.BAND_COUNT - 1)
        val clampedMb = gainMb.coerceIn(EqualizerAudioProcessor.MIN_GAIN_MB, EqualizerAudioProcessor.MAX_GAIN_MB)
        val next = processor.bandGainsDb.copyOf()
        next[b] = clampedMb / 100f
        processor.bandGainsDb = next
    }

    fun resetAll() {
        processor.bandGainsDb = FloatArray(EqualizerAudioProcessor.BAND_COUNT)
    }

    fun gainsMb(): List<Int> = processor.bandGainsDb.map { (it * 100).toInt() }
}
