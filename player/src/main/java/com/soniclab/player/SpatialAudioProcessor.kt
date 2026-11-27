/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import androidx.media3.common.AudioProcessor
import com.soniclab.player.HrtfSpatialEngine
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time 3D / 8D spatial audio on the playback path.
 *
 * - 3D: mid/side stereo widening — pushes the image "out of the head".
 * - 8D: HRTF/binaural rotation when available; otherwise LFO pan + echo.
 * - 8D+Tengah: 8D rotation with a center anchor.
 * - Surround: 3D widening with a gentle room echo, no rotation.
 */
class SpatialAudioProcessor : PcmAudioProcessor() {

    var mode: Int = MODE_OFF
        set(value) {
            field = value
            when (value) {
                MODE_OFF -> {
                    spatial3d = false
                    spatial8d = false
                    surround = false
                }
                MODE_3D -> {
                    spatial3d = true
                    spatial8d = false
                    surround = false
                }
                MODE_8D -> {
                    spatial3d = false
                    spatial8d = true
                    surround = false
                }
                MODE_3D_8D -> {
                    spatial3d = true
                    spatial8d = true
                    surround = false
                }
                MODE_SURROUND -> {
                    spatial3d = true
                    spatial8d = false
                    surround = true
                }
            }
        }

    @Volatile
    var spatial3d: Boolean = false

    @Volatile
    var spatial8d: Boolean = false

    @Volatile
    var surround: Boolean = false

    @Volatile
    var widthStrength: Float = DEFAULT_WIDTH_STRENGTH

    @Volatile
    var rotationSeconds: Float = DEFAULT_ROTATION_SECONDS

    @Volatile
    var panDepth: Float = DEFAULT_PAN_DEPTH

    // 8D state, touched only on the audio thread.
    private var phase = 0.0
    private var delayBufferL = FloatArray(0)
    private var delayBufferR = FloatArray(0)
    private var delayWritePos = 0
    private var delayReadOffset = 0

    private var hrtfEngine: HrtfSpatialEngine? = null
    private var hrtfReady = false

    override fun outputFormat(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (input.channelCount == 1) {
            AudioProcessor.AudioFormat(input.sampleRate, 2, input.encoding)
        } else {
            input
        }
    }

    override fun isEffectActive(): Boolean =
        spatial3d || spatial8d || surround || inputChannels == 1

    override fun processSamples(input: FloatArray, frames: Int): FloatArray {
        val out = if (inputChannels == 1 && outputChannels == 2) {
            FloatArray(frames * 2)
        } else {
            input
        }

        when {
            spatial3d && spatial8d -> process3D8D(input, out, frames)
            spatial8d -> process8D(input, out, frames)
            spatial3d && surround -> processSurround(input, out, frames, widen = true)
            surround -> processSurround(input, out, frames, widen = false)
            spatial3d -> process3D(input, out, frames)
            else -> if (out !== input) duplicateMono(input, out, frames)
        }

        if (spatial3d || spatial8d || surround) {
            for (i in out.indices) {
                out[i] = softClip(out[i] * SPATIAL_OUTPUT_GAIN)
            }
        }

        return out
    }

    private fun softClip(v: Float): Float {
        val a = abs(v)
        if (a <= SOFT_CLIP_THRESHOLD) return v

        val overflow = (a - SOFT_CLIP_THRESHOLD) / (1f - SOFT_CLIP_THRESHOLD)
        val shaped = SOFT_CLIP_THRESHOLD +
                (1f - SOFT_CLIP_THRESHOLD) * (overflow / (1f + overflow))

        return if (v >= 0f) shaped else -shaped
    }

    override fun onFormatChanged() {
        resetDsp()
        initHrtf()
    }

    override fun onFlush() {
        resetDsp()
    }

    private fun initHrtf() {
    if (hrtfReady) return

    hrtfEngine = try {
        HrtfSpatialEngine.create(
            sampleRate = sampleRateHz.toInt(),
            hrtfAssetPath = HRTF_ASSET_PATH
        )
    } catch (e: Exception) {
        null
    }

    hrtfReady = hrtfEngine != null
}

    private fun process3D(input: FloatArray, out: FloatArray, frames: Int) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX

        if (inputChannels == 1) {
            duplicateMono(input, out, frames)
            return
        }

        var i = 0
        var o = 0

        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f

            val midGain = 1f + (width - 1f) * PRESENCE_GUARD

            out[o++] = mid * midGain + side * width
            out[o++] = mid * midGain - side * width
        }
    }

    private fun process8D(input: FloatArray, out: FloatArray, frames: Int) {
        val phaseStep = phaseStep()

        val engine = hrtfEngine
        if (engine != null) {
            val azimuth = ((phase / (2.0 * PI)) * 360.0).toFloat()

            engine.process(
                input = input,
                output = out,
                frames = frames,
                azimuthDegrees = azimuth,
                elevationDegrees = 0f,
                reverbWet = 0.15f
            )

            phase = (phase + phaseStep) % (2.0 * PI)
            return
        }

        if (inputChannels == 1) {
            var i = 0
            var o = 0

            repeat(frames) {
                val v = input[i++]
                val (gainL, gainR) = panGains()
                phase += phaseStep

                val readPos =
                    (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size

                val echoL = delayBufferL[readPos]
                val echoR = delayBufferR[readPos]

                val outL = v * gainL + echoL * ECHO_FEEDBACK
                val outR = v * gainR + echoR * ECHO_FEEDBACK

                delayBufferL[delayWritePos] = outL
                delayBufferR[delayWritePos] = outR
                delayWritePos = (delayWritePos + 1) % delayBufferL.size

                out[o++] = outL
                out[o++] = outR
            }

            return
        }

        var i = 0
        var o = 0

        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val (gainL, gainR) = panGains()
            phase += phaseStep

            val readPos =
                (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size

            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]

            val outL = l * gainL + echoL * ECHO_FEEDBACK
            val outR = r * gainR + echoR * ECHO_FEEDBACK

            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size

            out[o++] = outL
            out[o++] = outR
        }
    }

    private fun process3D8D(input: FloatArray, out: FloatArray, frames: Int) {
        val phaseStep = phaseStep()

        if (inputChannels == 1) {
            var i = 0
            var o = 0

            repeat(frames) {
                val v = input[i++]
                val (gainL, gainR) = panGains()
                phase += phaseStep

                val anchor = v * CENTER_ANCHOR
                val readPos =
                    (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size

                val echoL = delayBufferL[readPos]
                val echoR = delayBufferR[readPos]

                val outL = v * gainL + anchor + echoL * ECHO_FEEDBACK
                val outR = v * gainR + anchor + echoR * ECHO_FEEDBACK

                delayBufferL[delayWritePos] = outL
                delayBufferR[delayWritePos] = outR
                delayWritePos = (delayWritePos + 1) % delayBufferL.size

                out[o++] = outL
                out[o++] = outR
            }

            return
        }

        var i = 0
        var o = 0

        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val mid = (l + r) * 0.5f
            val (gainL, gainR) = panGains()
            phase += phaseStep

            val anchor = mid * CENTER_ANCHOR
            val readPos =
                (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size

            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]

            val outL = l * gainL + anchor + echoL * ECHO_FEEDBACK
            val outR = r * gainR + anchor + echoR * ECHO_FEEDBACK

            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size

            out[o++] = outL
            out[o++] = outR
        }
    }

    private fun processSurround(
        input: FloatArray,
        out: FloatArray,
        frames: Int,
        widen: Boolean
    ) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX
        val midGain = 1f + (width - 1f) * PRESENCE_GUARD

        if (inputChannels == 1) {
            var i = 0
            var o = 0

            repeat(frames) {
                val v = input[i++]
                val readPos =
                    (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size

                val echoL = delayBufferL[readPos]
                val echoR = delayBufferR[readPos]

                val outL = v + echoL * ECHO_FEEDBACK
                val outR = v + echoR * ECHO_FEEDBACK

                delayBufferL[delayWritePos] = outL
                delayBufferR[delayWritePos] = outR
                delayWritePos = (delayWritePos + 1) % delayBufferL.size

                out[o++] = outL
                out[o++] = outR
            }

            return
        }

        var i = 0
        var o = 0

        repeat(frames) {
            val l = input[i++]
            val r = input[i++]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f

            val widL = if (widen) mid * midGain + side * width else l
            val widR = if (widen) mid * midGain - side * width else r

            val readPos =
                (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size

            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]

            val outL = widL + echoL * ECHO_FEEDBACK
            val outR = widR + echoR * ECHO_FEEDBACK

            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size

            out[o++] = outL
            out[o++] = outR
        }
    }

    private fun panGains(): Pair<Float, Float> {
        val depth = panDepth.coerceIn(0.1f, 1f)
        val pan = sin(phase).toFloat()
        val theta = (pan * depth + 1f) * (PI * 0.25f).toFloat()

        return cos(theta) to sin(theta)
    }

    private fun phaseStep(): Double {
        val sampleRate = sampleRateHz.coerceAtLeast(1f)
        return (2.0 * PI) / (rotationSeconds.coerceIn(4f, 60f) * sampleRate)
    }

    private fun duplicateMono(
        input: FloatArray,
        out: FloatArray,
        frames: Int
    ) {
        var o = 0

        for (frame in 0 until frames) {
            val v = input[frame]
            out[o++] = v
            out[o++] = v
        }
    }

    private fun resetDsp() {
        phase = 0.0
        delayWritePos = 0

        val delaySamples =
            (sampleRateHz * ECHO_DELAY_SECONDS).toInt().coerceAtLeast(1)

        if (delayBufferL.size != delaySamples) {
            delayBufferL = FloatArray(delaySamples)
            delayBufferR = FloatArray(delaySamples)
        } else {
            delayBufferL.fill(0f)
            delayBufferR.fill(0f)
        }

        delayReadOffset = delaySamples
    }

    companion object {
        const val MODE_OFF = 0
        const val MODE_3D = 1
        const val MODE_8D = 2
        const val MODE_3D_8D = 3
        const val MODE_SURROUND = 4
        const val MODE_CUSTOM = 5

        const val DEFAULT_WIDTH_STRENGTH = 0.35f
        const val DEFAULT_ROTATION_SECONDS = 8f
        const val DEFAULT_PAN_DEPTH = 0.75f

        private const val CENTER_ANCHOR = 0.18f
        private const val WIDTH_MAX = 1.8f
        private const val PRESENCE_GUARD = 0.35f
        private const val ECHO_DELAY_SECONDS = 0.12f
        private const val ECHO_FEEDBACK = 0.06f
        private const val SPATIAL_OUTPUT_GAIN = 0.82f
        private const val SOFT_CLIP_THRESHOLD = 0.98f

        private const val HRTF_ASSET_PATH = "hrtf/mit_kemar_44100.sofa"
    }
}