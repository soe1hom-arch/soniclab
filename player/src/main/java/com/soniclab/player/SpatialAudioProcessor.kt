/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    private var phase = 0.0
    private var delayBufferL = FloatArray(0)
    private var delayBufferR = FloatArray(0)
    private var delayWritePos = 0
    private var delayReadOffset = 0

    private var hrtfEngine: HrtfSpatialEngine? = null
    private var hrtfReady = false

    override fun outputFormat(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (input.channelCount == 1) {
            AudioProcessor.AudioFormat(
                input.sampleRate,
                2,
                input.encoding
            )
        } else {
            input
        }
    }

    override fun isEffectActive(): Boolean {
        return spatial3d || spatial8d || surround || inputChannels == 1
    }

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
            else -> {
                if (out !== input) {
                    duplicateMono(input, out, frames)
                }
            }
        }

        if (spatial3d || spatial8d || surround) {
            for (i in out.indices) {
                out[i] = softClip(out[i] * SPATIAL_OUTPUT_GAIN)
            }
        }

        return out
    }

    private fun softClip(value: Float): Float {
        val absolute = abs(value)

        if (absolute <= SOFT_CLIP_THRESHOLD) {
            return value
        }

        val overflow =
            (absolute - SOFT_CLIP_THRESHOLD) / (1f - SOFT_CLIP_THRESHOLD)

        val shaped = SOFT_CLIP_THRESHOLD +
                (1f - SOFT_CLIP_THRESHOLD) * (overflow / (1f + overflow))

        return if (value >= 0f) shaped else -shaped
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
                sampleRate = sampleRateHz,
                hrtfAssetPath = HRTF_ASSET_PATH
            )
        } catch (error: Exception) {
            Log.e("SonicLabHRTF", "HRTF init failed", error)
            null
        }

        hrtfReady = hrtfEngine != null

        Log.i(
            "SonicLabHRTF",
            "HRTF create result=$hrtfReady sampleRate=$sampleRateHz"
        )
    }

    private fun process3D(
        input: FloatArray,
        out: FloatArray,
        frames: Int
    ) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX

        if (inputChannels == 1) {
            duplicateMono(input, out, frames)
            return
        }

        var inputIndex = 0
        var outputIndex = 0

        repeat(frames) {
            val left = input[inputIndex++]
            val right = input[inputIndex++]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f
            val midGain = 1f + (width - 1f) * PRESENCE_GUARD

            out[outputIndex++] = mid * midGain + side * width
            out[outputIndex++] = mid * midGain - side * width
        }
    }

    private fun process8D(
        input: FloatArray,
        out: FloatArray,
        frames: Int
    ) {
        if (!hrtfReady) {
            initHrtf()
        }

        val phaseStep = phaseStep()
        val engine = hrtfEngine

        Log.d(
            "SonicLabHRTF",
            "process8D frames=$frames hrtf=${engine != null}"
        )

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
            var inputIndex = 0
            var outputIndex = 0

            repeat(frames) {
                val value = input[inputIndex++]
                val gains = panGains()

                val echoLeft = readDelay(delayBufferL)
                val echoRight = readDelay(delayBufferR)

                val outLeft = value * gains.first + echoLeft * ECHO_FEEDBACK
                val outRight = value * gains.second + echoRight * ECHO_FEEDBACK

                delayBufferL[delayWritePos] = outLeft
                delayBufferR[delayWritePos] = outRight

                advanceDelayPosition()

                out[outputIndex++] = outLeft
                out[outputIndex++] = outRight

                phase = (phase + phaseStep) % (2.0 * PI)
            }

            return
        }

        var inputIndex = 0
        var outputIndex = 0

        repeat(frames) {
            val left = input[inputIndex++]
            val right = input[inputIndex++]
            val gains = panGains()

            val echoLeft = readDelay(delayBufferL)
            val echoRight = readDelay(delayBufferR)

            val outLeft = left * gains.first + echoLeft * ECHO_FEEDBACK
            val outRight = right * gains.second + echoRight * ECHO_FEEDBACK

            delayBufferL[delayWritePos] = outLeft
            delayBufferR[delayWritePos] = outRight

            advanceDelayPosition()

            out[outputIndex++] = outLeft
            out[outputIndex++] = outRight

            phase = (phase + phaseStep) % (2.0 * PI)
        }
    }

    private fun process3D8D(
        input: FloatArray,
        out: FloatArray,
        frames: Int
    ) {
        val phaseStep = phaseStep()

        if (inputChannels == 1) {
            var inputIndex = 0
            var outputIndex = 0

            repeat(frames) {
                val value = input[inputIndex++]
                val gains = panGains()
                val anchor = value * CENTER_ANCHOR

                val echoLeft = readDelay(delayBufferL)
                val echoRight = readDelay(delayBufferR)

                val outLeft =
                    value * gains.first + anchor + echoLeft * ECHO_FEEDBACK
                val outRight =
                    value * gains.second + anchor + echoRight * ECHO_FEEDBACK

                delayBufferL[delayWritePos] = outLeft
                delayBufferR[delayWritePos] = outRight

                advanceDelayPosition()

                out[outputIndex++] = outLeft
                out[outputIndex++] = outRight

                phase = (phase + phaseStep) % (2.0 * PI)
            }

            return
        }

        var inputIndex = 0
        var outputIndex = 0

        repeat(frames) {
            val left = input[inputIndex++]
            val right = input[inputIndex++]
            val mid = (left + right) * 0.5f
            val gains = panGains()
            val anchor = mid * CENTER_ANCHOR

            val echoLeft = readDelay(delayBufferL)
            val echoRight = readDelay(delayBufferR)

            val outLeft =
                left * gains.first + anchor + echoLeft * ECHO_FEEDBACK
            val outRight =
                right * gains.second + anchor + echoRight * ECHO_FEEDBACK

            delayBufferL[delayWritePos] = outLeft
            delayBufferR[delayWritePos] = outRight

            advanceDelayPosition()

            out[outputIndex++] = outLeft
            out[outputIndex++] = outRight

            phase = (phase + phaseStep) % (2.0 * PI)
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
            var inputIndex = 0
            var outputIndex = 0

            repeat(frames) {
                val value = input[inputIndex++]

                val echoLeft = readDelay(delayBufferL)
                val echoRight = readDelay(delayBufferR)

                val outLeft = value + echoLeft * ECHO_FEEDBACK
                val outRight = value + echoRight * ECHO_FEEDBACK

                delayBufferL[delayWritePos] = outLeft
                delayBufferR[delayWritePos] = outRight

                advanceDelayPosition()

                out[outputIndex++] = outLeft
                out[outputIndex++] = outRight
            }

            return
        }

        var inputIndex = 0
        var outputIndex = 0

        repeat(frames) {
            val left = input[inputIndex++]
            val right = input[inputIndex++]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            val widenedLeft =
                if (widen) mid * midGain + side * width else left
            val widenedRight =
                if (widen) mid * midGain - side * width else right

            val echoLeft = readDelay(delayBufferL)
            val echoRight = readDelay(delayBufferR)

            val outLeft = widenedLeft + echoLeft * ECHO_FEEDBACK
            val outRight = widenedRight + echoRight * ECHO_FEEDBACK

            delayBufferL[delayWritePos] = outLeft
            delayBufferR[delayWritePos] = outRight

            advanceDelayPosition()

            out[outputIndex++] = outLeft
            out[outputIndex++] = outRight
        }
    }

    private fun panGains(): Pair<Float, Float> {
        val depth = panDepth.coerceIn(0.1f, 1f)
        val pan = sin(phase).toFloat()
        val theta = (pan * depth + 1f) * (PI * 0.25f).toFloat()

        return cos(theta) to sin(theta)
    }

    private fun phaseStep(): Double {
        val sampleRate = sampleRateHz.coerceAtLeast(1)
        val rotation = rotationSeconds.coerceIn(4f, 60f)

        return (2.0 * PI) / (rotation * sampleRate)
    }

    private fun readDelay(buffer: FloatArray): Float {
        if (buffer.isEmpty()) {
            return 0f
        }

        val readPosition =
            (delayWritePos - delayReadOffset + buffer.size) % buffer.size

        return buffer[readPosition]
    }

    private fun advanceDelayPosition() {
        if (delayBufferL.isEmpty() || delayBufferR.isEmpty()) {
            return
        }

        delayWritePos = (delayWritePos + 1) % delayBufferL.size
    }

    private fun duplicateMono(
        input: FloatArray,
        out: FloatArray,
        frames: Int
    ) {
        var outputIndex = 0

        for (frame in 0 until frames) {
            val value = input[frame]

            out[outputIndex++] = value
            out[outputIndex++] = value
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
        const val DEFAULT_ROTATION_SECONDS = 14f
        const val DEFAULT_PAN_DEPTH = 0.4f

        private const val CENTER_ANCHOR = 0.45f
        private const val WIDTH_MAX = 1.8f
        private const val PRESENCE_GUARD = 0.35f
        private const val ECHO_DELAY_SECONDS = 0.38f
        private const val ECHO_FEEDBACK = 0.1f
        private const val SPATIAL_OUTPUT_GAIN = 0.82f
        private const val SOFT_CLIP_THRESHOLD = 0.98f

        private const val HRTF_ASSET_PATH = "hrtf/hrtf_48k.sofa"
    }
}