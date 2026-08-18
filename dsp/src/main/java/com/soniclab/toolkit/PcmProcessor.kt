/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.toolkit

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interleaved PCM16 (little-endian) audio data decoded from any codec.
 */
data class PcmData(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int
)

/**
 * Pure-Kotlin PCM processing utilities powering the offline audio toolkit.
 *
 * All operations run entirely on-device: reverse, loudness normalization,
 * stereo-aware SOLA time-stretch (tempo change without pitch shift), pitch
 * shifting (stretch + windowed-sinc resample), vocal reduction (mid/side),
 * resampling and channel up/down-mixing. No FFmpeg binary is required.
 */
/**
 * Quality/speed trade-off for time-stretching (tempo/pitch) operations.
 */
enum class StretchQuality {
    FAST,
    BALANCED,
    HIGH
}

object PcmProcessor {

    /** Converts little-endian PCM16 bytes into normalized floats in [-1, 1]. */
    fun toPcmData(bytes: ByteArray, sampleRate: Int, channels: Int): PcmData {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = bytes.size / 2
        val samples = FloatArray(count)
        for (i in 0 until count) {
            samples[i] = buffer.short.toFloat() / 32768f
        }
        return PcmData(samples, sampleRate, channels)
    }

    /** Reverses the audio while keeping each channel aligned. */
    fun reverse(data: PcmData): PcmData {
        val frames = data.samples.size / data.channels
        val out = FloatArray(data.samples.size)
        for (frame in 0 until frames) {
            val src = (frames - 1 - frame) * data.channels
            for (c in 0 until data.channels) {
                out[frame * data.channels + c] = data.samples[src + c]
            }
        }
        return data.copy(samples = out)
    }

    /**
     * Loudness normalization toward [targetLufs] using a real EBU R128
     * measurement (K-weighting + 400 ms blocks + absolute/relative gating,
     * see [com.soniclab.analyzer.R128Meter]). Gain is capped by [maxGainDb]
     * so quiet recordings are not pumped into distortion.
     */
    fun normalize(data: PcmData, targetLufs: Float, maxGainDb: Float = 12f): PcmData {
        val meter = com.soniclab.analyzer.R128Meter(data.sampleRate, data.channels)
        meter.push(data.samples)
        val integrated = meter.integratedLufs()
        if (integrated <= com.soniclab.analyzer.R128Meter.QUIET_LUFS) {
            // Silent file: nothing to normalize.
            return data
        }
        val gainDb = (targetLufs - integrated).coerceAtMost(maxGainDb)
        val gain = 10.0.pow(gainDb / 20.0).toFloat()
        val out = FloatArray(data.samples.size) { i ->
            (data.samples[i] * gain).coerceIn(-1f, 1f)
        }
        return data.copy(samples = out)
    }

    /**
     * Tempo change without pitch shift using stereo-aware SOLA
     * (Synchronized Overlap-Add). [factor] > 1 slows down (longer
     * duration), < 1 speeds up. Channels are stretched with a shared
     * placement offset so the stereo image stays intact.
     */
    fun timeStretch(
        data: PcmData,
        factor: Float,
        quality: StretchQuality = StretchQuality.BALANCED
    ): PcmData {
        val stretched = solaStretch(data.samples, data.channels, factor, quality)
        return PcmData(stretched, data.sampleRate, data.channels)
    }

    /**
     * Pitch shift (in semitones, positive = higher) without changing
     * duration: time-stretch by the ratio, then resample back.
     */
    fun pitchShift(
        data: PcmData,
        semitones: Float,
        quality: StretchQuality = StretchQuality.BALANCED
    ): PcmData {
        val ratio = 2.0.pow(semitones / 12.0).toFloat()
        val stretched = timeStretch(data, ratio, quality)
        return resample(stretched, 1f / ratio)
    }

    /** Band-limited resampling; [ratio] = newRate / oldRate. */
    fun resample(data: PcmData, ratio: Float): PcmData {
        if (ratio == 1f) return data
        val channels = splitChannels(data).map { sincResample(it, ratio) }
        val newRate = (data.sampleRate * ratio).toInt().coerceAtLeast(1)
        return PcmData(interleave(channels), newRate, data.channels)
    }

    /** Up/down-mixes to the requested channel count (mono/stereo). */
    fun conformChannels(data: PcmData, channels: Int): PcmData {
        if (data.channels == channels) return data
        val split = splitChannels(data)
        val mixed = when {
            split.size == 1 && channels == 2 -> listOf(split[0], split[0])
            split.size == 2 && channels == 1 -> listOf(
                FloatArray(split[0].size) { (split[0][it] + split[1][it]) * 0.5f }
            )
            else -> split
        }
        return PcmData(interleave(mixed), data.sampleRate, channels)
    }

    /** Resamples to the requested sample rate. */
    fun conformSampleRate(data: PcmData, sampleRate: Int): PcmData {
        if (data.sampleRate == sampleRate) return data
        return resample(data, sampleRate.toFloat() / data.sampleRate)
    }

    /**
     * Karaoke-style vocal reduction by keeping the side channel (L - R).
     * Returns null when the input is mono.
     */
    fun vocalReduction(data: PcmData): PcmData? {
        if (data.channels < 2) return null
        val split = splitChannels(data)
        val side = FloatArray(split[0].size) { i ->
            ((split[0][i] - split[1][i]) * 0.5f).coerceIn(-1f, 1f)
        }
        return PcmData(interleave(listOf(side, side)), data.sampleRate, 2)
    }

    private fun splitChannels(data: PcmData): List<FloatArray> {
        val frames = data.samples.size / data.channels
        return List(data.channels) { c ->
            FloatArray(frames) { data.samples[it * data.channels + c] }
        }
    }

    private fun interleave(channels: List<FloatArray>): FloatArray {
        val frames = channels.minOf { it.size }
        val out = FloatArray(frames * channels.size)
        for (f in 0 until frames) {
            for (c in channels.indices) {
                out[f * channels.size + c] = channels[c][f]
            }
        }
        return out
    }

    /**
     * Windowed-sinc resampler with a Blackman window and output-Nyquist
     * cutoff. Better quality than linear interpolation; used for pitch
     * shifts and sample-rate conformance.
     */
    private fun sincResample(input: FloatArray, ratio: Float): FloatArray {
        val outLength = (input.size * ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLength)
        val halfTaps = 32
        val cutoff = min(1.0, 1.0 / ratio)
        for (i in out.indices) {
            val position = i / ratio
            val center = position.toInt()
            var acc = 0.0
            var weightSum = 0.0
            for (j in (center - halfTaps)..(center + halfTaps)) {
                if (j < 0 || j >= input.size) continue
                val t = position - j
                val s = PI * t * cutoff
                val sinc = if (abs(t) < 1e-9) cutoff else cutoff * sin(s) / s
                val window = blackman(j - center + halfTaps, 2 * halfTaps)
                val weighted = sinc * window
                acc += input[j] * weighted
                weightSum += weighted
            }
            out[i] = if (weightSum > 1e-12) (acc / weightSum).toFloat() else 0f
        }
        return out
    }

    private fun blackman(index: Int, size: Int): Double {
        if (size <= 1) return 1.0
        val x = 2.0 * PI * index / (size - 1)
        return 0.42 - 0.5 * cos(x) + 0.08 * cos(2.0 * x)
    }

    /**
     * Stereo-aware SOLA time-stretch for interleaved PCM. All channels share
     * one best placement offset (found with normalized cross-correlation
     * summed across channels), preserving the stereo image. Hann crossfades
     * join overlapping frames; boundary fades remove click artifacts.
     */
    private fun solaStretch(
        input: FloatArray,
        channels: Int,
        factor: Float,
        quality: StretchQuality
    ): FloatArray {
        val safeFactor = factor.coerceIn(0.25f, 2f)
        val (frame, analysisHop, searchHalf, corrWindowBase) = when (quality) {
            StretchQuality.FAST -> intArrayOf(1024, 256, 32, 128)
            StretchQuality.BALANCED -> intArrayOf(2048, 512, 64, 256)
            StretchQuality.HIGH -> intArrayOf(4096, 1024, 128, 512)
        }
        val synthesisHop = (analysisHop * safeFactor).toInt().coerceIn(analysisHop / 4, frame / 2)
        val overlap = frame - synthesisHop
        val corrWindow = minOf(overlap, corrWindowBase)
        val totalFrames = input.size / channels
        val outFrames = ((totalFrames.toLong() * synthesisHop) / analysisHop).toInt() + frame
        val out = FloatArray(outFrames * channels)

        // Perceptual touch: detect percussive onsets (sharp energy jumps) so
        // their timing is preserved instead of being smeared by re-placement.
        val loopFrames = (totalFrames - frame) / analysisHop
        val onset = detectOnsets(input, channels, analysisHop, loopFrames)

        var read = 0
        var write = 0
        var frameIndex = 0
        while (read + frame <= totalFrames) {
            var bestK = 0
            if (!onset.getOrElse(frameIndex) { false }) {
            var bestScore = Double.NEGATIVE_INFINITY
            for (k in -searchHalf..searchHalf) {
                val outPos = write + k
                if (outPos < 0 || outPos + corrWindow > outFrames) continue
                var corr = 0.0
                var inEnergy = 0.0
                var outEnergy = 0.0
                for (i in 0 until corrWindow) {
                    for (c in 0 until channels) {
                        val a = input[(read + i) * channels + c]
                        val b = out[(outPos + i) * channels + c]
                        corr += a.toDouble() * b
                        inEnergy += a.toDouble() * a
                        outEnergy += b.toDouble() * b
                    }
                }
                val score = corr / sqrt(inEnergy * outEnergy).coerceAtLeast(1e-9)
                if (score > bestScore) {
                    bestScore = score
                    bestK = k
                }
            }
            }

            val place = write + bestK
            for (i in 0 until overlap) {
                val weight = hann(i, overlap)
                for (c in 0 until channels) {
                    val src = (read + i) * channels + c
                    val dst = (place + i) * channels + c
                    out[dst] = (input[src] * weight + out[dst] * (1.0 - weight)).toFloat()
                }
            }
            for (i in overlap until frame) {
                for (c in 0 until channels) {
                    out[(place + i) * channels + c] = input[(read + i) * channels + c]
                }
            }
            read += analysisHop
            write += synthesisHop
            frameIndex++
        }

        val usedFrames = write.coerceAtMost(outFrames)
        val fadeLen = minOf(512, usedFrames / 2)
        for (c in 0 until channels) {
            for (i in 0 until fadeLen) {
                out[i * channels + c] = (out[i * channels + c] * hann(i, fadeLen)).toFloat()
            }
            for (i in 0 until fadeLen) {
                val idx = (usedFrames - 1 - i) * channels + c
                out[idx] = (out[idx] * (1.0 - hann(i, fadeLen))).toFloat()
            }
        }

        // Gentle headroom so overlap-add accumulation never clips.
        for (i in out.indices) {
            out[i] = (out[i] * HEADROOM).coerceIn(-1f, 1f)
        }
        return out.copyOf(usedFrames * channels)
    }

    private fun detectOnsets(input: FloatArray, channels: Int, analysisHop: Int, count: Int): BooleanArray {
        val onset = BooleanArray(count)
        if (count <= 1) return onset
        var runningMean = 1e-6f
        for (f in 0 until count) {
            val start = f * analysisHop * channels
            var energy = 0.0
            for (i in 0 until analysisHop) {
                val s = input[start + i * channels]
                energy += s.toDouble() * s
            }
            energy /= analysisHop
            onset[f] = energy > runningMean * ONSET_RATIO + 1e-5f && energy > 1e-4f
            runningMean = runningMean * 0.9f + energy.toFloat() * 0.1f
        }
        return onset
    }

    private fun hann(index: Int, size: Int): Double {
        if (size <= 1) return 1.0
        return 0.5 - 0.5 * cos(2.0 * PI * index / (size - 1))
    }

    private const val HEADROOM = 0.98f
    private const val ONSET_RATIO = 4f
}
