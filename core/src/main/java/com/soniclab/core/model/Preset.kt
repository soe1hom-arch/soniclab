/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.core.model

/**
 * Smart audio preset. Band gains are in millibels (like the Android Equalizer API),
 * keyed by the equalizer band index. Extra settings drive BassBoost/Virtualizer/etc.
 */
data class Preset(
    val id: String,
    val name: String,
    val bandGainsMb: Map<Int, Int> = emptyMap(),
    val bassStrength: Short = 0,
    val virtualizerStrength: Short = 0,
    val loudnessBoostDb: Float = 0f,
    val stereoWidth: Float = 1f,
    val reverbEnabled: Boolean = false,
    val echoEnabled: Boolean = false,
    val isCustom: Boolean = false
) {
    companion object {
        val NONE = Preset(id = "none", name = "Flat")

        val presets: List<Preset> = listOf(
            NONE,
            // Conservative curves: small tonal corrections preserve dynamics
            // and avoid the stacked EQ+bass boosts common in generic players.
            Preset("music_hd", "Music HD", mapOf(0 to 100, 1 to 80, 2 to 50, 3 to 30, 4 to 20, 5 to 20, 6 to 30, 7 to 50, 8 to 80, 9 to 100), bassStrength = 80),
            Preset("hi_fi", "Hi-Fi", mapOf(0 to 0, 1 to 0, 2 to 30, 3 to 50, 4 to 60, 5 to 60, 6 to 50, 7 to 30, 8 to 0, 9 to 0)),
            Preset("studio", "Studio Monitor", mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0, 7 to 0, 8 to 0, 9 to 0)),
            Preset("bass_clean", "Bass Clean", mapOf(0 to 250, 1 to 250, 2 to 180, 3 to 100, 4 to 40, 5 to 0, 6 to 0, 7 to 0, 8 to 0, 9 to 0), bassStrength = 180),
            Preset("vocal_boost", "Vocal Boost", mapOf(0 to -50, 1 to -30, 2 to 0, 3 to 80, 4 to 150, 5 to 180, 6 to 120, 7 to 50, 8 to -30, 9 to -50)),
            Preset("cinema", "Cinema", mapOf(0 to 150, 1 to 120, 2 to 60, 3 to 0, 4 to 60, 5 to 100, 6 to 150, 7 to 150, 8 to 100, 9 to 50), reverbEnabled = true),
            // Gaming: V-shape imersif dengan mid sedikit diturunkan + virtualizer ringan.
            Preset("gaming", "Gaming", mapOf(0 to 250, 1 to 220, 2 to 150, 3 to 60, 4 to -60, 5 to -60, 6 to 60, 7 to 150, 8 to 220, 9 to 250), virtualizerStrength = 220),
            Preset("podcast", "Podcast", mapOf(0 to -150, 1 to -80, 2 to 0, 3 to 100, 4 to 150, 5 to 130, 6 to 60, 7 to 0, 8 to -80, 9 to -150)),
            // Car Audio: bass kuat + mid naik (mengalahkan noise jalan), treble dijaga agar tidak tajam.
            Preset("car_audio", "Car Audio", mapOf(0 to 250, 1 to 250, 2 to 200, 3 to 80, 4 to 60, 5 to 100, 6 to 160, 7 to 130, 8 to 60, 9 to 0), bassStrength = 220),
            // BT Speaker: fokus mid-bass 100-250 Hz (speaker kecil tidak bisa reproduce sub-bass).
            Preset("bt_speaker", "Bluetooth Speaker", mapOf(0 to 60, 1 to 300, 2 to 250, 3 to 120, 4 to 0, 5 to -60, 6 to 0, 7 to 100, 8 to 160, 9 to 160), bassStrength = 180),
            Preset("headphones", "Headphones", mapOf(0 to 180, 1 to 150, 2 to 100, 3 to 50, 4 to 0, 5 to 0, 6 to 50, 7 to 100, 8 to 150, 9 to 180), bassStrength = 80),
            Preset("night_mode", "Night Mode", mapOf(0 to -200, 1 to -140, 2 to -70, 3 to 0, 4 to 0, 5 to 0, 6 to -70, 7 to -140, 8 to -200, 9 to -250)),
            // Preset baru
            Preset("bass_vocal", "Bass + Vokal", mapOf(0 to 220, 1 to 200, 2 to 130, 3 to 60, 4 to 0, 5 to 60, 6 to 150, 7 to 120, 8 to 30, 9 to 0), bassStrength = 180),
            Preset("acoustic", "Acoustic", mapOf(0 to 80, 1 to 60, 2 to 30, 3 to 30, 4 to 0, 5 to 30, 6 to 60, 7 to 80, 8 to 60, 9 to 30)),
            Preset("jazz", "Jazz", mapOf(0 to 150, 1 to 120, 2 to 60, 3 to 0, 4 to -30, 5 to 0, 6 to 60, 7 to 80, 8 to 120, 9 to 60), reverbEnabled = true)
        )
    }
}
