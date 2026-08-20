/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.ai

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

/**
 * Neural AI Enhance backed by an on-device TFLite model placed at
 * `models/ai_enhancer_v1.tflite` in the module assets.
 *
 * When no model is bundled (the current default — no model has survived the
 * quality bar for real music yet), this transparently falls back to
 * [ClassicEnhancer], so the playback path always stays functional and honest.
 *
 * Model contract (same as the README in `src/main/assets/models`):
 * - Input:  `float32[1][N]` mono PCM samples in [-1, 1]
 * - Output: `float32[1][N]` enhanced PCM samples in [-1, 1]
 *
 * Inference runs on the audio thread ([EnhanceAudioProcessor] chunks audio to
 * ~10 ms), so the model must be small and fast enough for realtime use.
 */
class NeuralEnhancer : AiEnhancer {

    private val fallback = ClassicEnhancer()
    private val interpreter: Interpreter?

    constructor(context: Context) : this(loadInterpreter(context))

    internal constructor(interpreter: Interpreter?) {
        this.interpreter = interpreter
    }

    override val isAiModelLoaded: Boolean
        get() = interpreter != null

    override val displayName: String
        get() = if (interpreter != null) MODEL_LABEL else fallback.displayName

    override fun enhance(samples: FloatArray): FloatArray {
        val model = interpreter ?: return fallback.enhance(samples)
        if (samples.isEmpty()) return samples
        val input = arrayOf(samples)
        val output = arrayOf(FloatArray(samples.size))
        try {
            model.run(input, output)
        } catch (_: RuntimeException) {
            // A broken/unsupported model must never crash the playback thread.
            return fallback.enhance(samples)
        }
        return output[0]
    }

    /** Releases the native TFLite interpreter; safe to call at any time. */
    fun close() {
        interpreter?.close()
    }

    companion object {
        private const val MODEL_LABEL = "Neural Enhance (TFLite)"
        private const val MODEL_ASSET = "models/ai_enhancer_v1.tflite"

        private fun loadInterpreter(context: Context): Interpreter? {
            return try {
                context.assets.openFd(MODEL_ASSET).use { fd ->
                    FileInputStream(fd.fileDescriptor).use { stream ->
                        val model = stream.channel.map(
                            FileChannel.MapMode.READ_ONLY, 0, stream.channel.size()
                        )
                        val options = Interpreter.Options().apply { setNumThreads(1) }
                        Interpreter(model, options)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
