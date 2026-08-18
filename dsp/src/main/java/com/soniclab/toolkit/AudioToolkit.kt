/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.toolkit

import android.net.Uri

/**
 * Result of a toolkit operation.
 */
sealed class ToolkitResult {
    data class Success(val outputPath: String) : ToolkitResult()
    data class Info(val info: AudioFileInfo) : ToolkitResult()
    data class Failure(val message: String, val cause: Throwable? = null) : ToolkitResult()
}

/**
 * Detailed metadata about an audio file.
 */
data class AudioFileInfo(
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channelCount: Int,
    val codec: String,
    val mimeType: String?,
    val title: String?,
    val artist: String?,
    val album: String?
)

/**
 * Contract for the audio toolkit. All operations run offline on-device:
 * MediaCodec decodes any codec to PCM, then pure-Kotlin DSP
 * ([PcmProcessor]) handles reverse, normalize, tempo/pitch and vocal
 * reduction. FFmpeg remains an optional future acceleration layer.
 */
interface AudioToolkit {
    suspend fun getInfo(uri: Uri): ToolkitResult
    suspend fun convertToWav(uri: Uri, outputPath: String, onProgress: (Float) -> Unit = {}): ToolkitResult
    suspend fun cut(uri: Uri, startMs: Long, endMs: Long, outputPath: String, onProgress: (Float) -> Unit = {}): ToolkitResult
    suspend fun join(tracks: List<Uri>, outputPath: String, onProgress: (Float) -> Unit = {}): ToolkitResult
    suspend fun normalize(uri: Uri, outputPath: String, targetLufs: Float = -14f, onProgress: (Float) -> Unit = {}): ToolkitResult
    suspend fun reverse(uri: Uri, outputPath: String, onProgress: (Float) -> Unit = {}): ToolkitResult
    suspend fun changePitch(
        uri: Uri,
        semitones: Float,
        outputPath: String,
        quality: StretchQuality = StretchQuality.BALANCED,
        onProgress: (Float) -> Unit = {}
    ): ToolkitResult
    suspend fun changeTempo(
        uri: Uri,
        factor: Float,
        outputPath: String,
        quality: StretchQuality = StretchQuality.BALANCED,
        onProgress: (Float) -> Unit = {}
    ): ToolkitResult
    suspend fun vocalReduction(uri: Uri, outputPath: String, onProgress: (Float) -> Unit = {}): ToolkitResult
}
