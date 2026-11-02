/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.core.model

/** Complete user-created snapshot of the real-time effects controls. */
data class UserPreset(
    val id: String,
    val name: String,
    val bandGainsMb: Map<Int, Int>,
    val bassStrength: Int,
    val balance: Float,
    val trebleDb: Float,
    val spatialMode: Int,
    val spatial3d: Boolean,
    val spatial8d: Boolean,
    val surround: Boolean,
    val spatialWidth: Float,
    val rotationSeconds: Float,
    val panDepth: Float,
    val reverbWet: Float,
    val reverbRoom: Float,
    val pitchSemitones: Float,
    val headroomDb: Float
)
