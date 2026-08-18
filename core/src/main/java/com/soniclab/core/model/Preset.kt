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
            // Kurva halus: boost ekstrem dikurangi agar tidak menggembung/kasar, plus limiter di ujung rantai menjaga headroom.
            Preset("music_hd", "Music HD", mapOf(0 to 250, 1 to 200, 2 to 150, 3 to 100, 4 to 50, 5 to 50, 6 to 100, 7 to 150, 8 to 200, 9 to 250), bassStrength = 150),
            Preset("hi_fi", "Hi-Fi", mapOf(0 to 0, 1 to 0, 2 to 100, 3 to 150, 4 to 200, 5 to 200, 6 to 150, 7 to 100, 8 to 0, 9 to 0)),
            Preset("studio", "Studio Monitor", mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0, 7 to 0, 8 to 0, 9 to 0)),
            Preset("bass_clean", "Bass Clean", mapOf(0 to 600, 1 to 500, 2 to 350, 3 to 200, 4 to 100, 5 to 0, 6 to 0, 7 to 0, 8 to 0, 9 to 0), bassStrength = 350),
            Preset("vocal_boost", "Vocal Boost", mapOf(0 to -100, 1 to -50, 2 to 0, 3 to 150, 4 to 350, 5 to 450, 6 to 300, 7 to 100, 8 to -50, 9 to -100)),
            Preset("cinema", "Cinema", mapOf(0 to 250, 1 to 200, 2 to 100, 3 to 0, 4 to 100, 5 to 200, 6 to 300, 7 to 300, 8 to 200, 9 to 100), reverbEnabled = true),
            // Gaming: V-shape imersif dengan mid sedikit diturunkan + virtualizer ringan.
            Preset("gaming", "Gaming", mapOf(0 to 500, 1 to 400, 2 to 300, 3 to 100, 4 to -100, 5 to -100, 6 to 100, 7 to 300, 8 to 400, 9 to 500), virtualizerStrength = 300),
            Preset("podcast", "Podcast", mapOf(0 to -200, 1 to -100, 2 to 0, 3 to 200, 4 to 300, 5 to 250, 6 to 100, 7 to 0, 8 to -100, 9 to -200)),
            // Car Audio: bass kuat + mid naik (mengalahkan noise jalan), treble dijaga agar tidak tajam.
            Preset("car_audio", "Car Audio", mapOf(0 to 400, 1 to 500, 2 to 300, 3 to 100, 4 to 100, 5 to 200, 6 to 300, 7 to 250, 8 to 100, 9 to 0), bassStrength = 400),
            // BT Speaker: fokus mid-bass 100-250 Hz (speaker kecil tidak bisa reproduce sub-bass).
            Preset("bt_speaker", "Bluetooth Speaker", mapOf(0 to 100, 1 to 500, 2 to 400, 3 to 200, 4 to 0, 5 to -100, 6 to 0, 7 to 200, 8 to 300, 9 to 300), bassStrength = 300),
            Preset("headphones", "Headphones", mapOf(0 to 350, 1 to 300, 2 to 200, 3 to 100, 4 to 0, 5 to 0, 6 to 100, 7 to 200, 8 to 300, 9 to 350), bassStrength = 150),
            Preset("night_mode", "Night Mode", mapOf(0 to -300, 1 to -200, 2 to -100, 3 to 0, 4 to 0, 5 to 0, 6 to -100, 7 to -200, 8 to -300, 9 to -400)),
            // Preset baru
            Preset("bass_vocal", "Bass + Vokal", mapOf(0 to 400, 1 to 350, 2 to 200, 3 to 100, 4 to 0, 5 to 100, 6 to 250, 7 to 200, 8 to 50, 9 to 0), bassStrength = 300),
            Preset("acoustic", "Acoustic", mapOf(0 to 150, 1 to 100, 2 to 50, 3 to 50, 4 to 0, 5 to 50, 6 to 100, 7 to 150, 8 to 100, 9 to 50)),
            Preset("jazz", "Jazz", mapOf(0 to 250, 1 to 200, 2 to 100, 3 to 0, 4 to -50, 5 to 0, 6 to 100, 7 to 150, 8 to 200, 9 to 100), reverbEnabled = true)
        )
    }
}
