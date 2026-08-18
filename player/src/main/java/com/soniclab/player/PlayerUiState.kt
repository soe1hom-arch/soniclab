/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import androidx.media3.common.Player
import com.soniclab.core.model.Track

/**
 * Immutable snapshot of the player state consumed by the UI.
 */
/** Decoded format info of the currently playing audio (from Media3 tracks). */
data class TrackAudioInfo(
    val codec: String,
    val sampleRateHz: Int,
    val bitDepth: String,
    val channels: Int
)

data class PlayerUiState(
    val currentTrack: Track? = null,
    val audioInfo: TrackAudioInfo? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 0f,
    val queue: List<Track> = emptyList(),
    val userQueue: List<Track> = emptyList(),
    val isBuffering: Boolean = false
) {
    val hasTrack: Boolean get() = currentTrack != null
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
