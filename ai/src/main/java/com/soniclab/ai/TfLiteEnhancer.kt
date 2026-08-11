package com.soniclab.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite enhancer. Loads a model bundled in assets via
 * [AiModelManager]; if none is present it delegates to [ClassicEnhancer] so
 * one-tap AI Enhance always works offline. Model contract:
 * input/output `float32[1][N]` with N fixed at export time; input frames are
 * chunked to match the model length.
 */
class TfLiteEnhancer(context: Context, modelFile: File? = null) : AiEnhancer {

    private val fallback = ClassicEnhancer()
    private var interpreter: Interpreter? = null
    private var inputSize: Int = 0
    private val modelName: String = modelFile?.name ?: "none"

    override val isAiModelLoaded: Boolean get() = interpreter != null
    override val displayName: String
        get() = if (interpreter != null) "TFLite AI Enhance ($modelName)" else "Classic DSP Enhance"

    init {
        try {
            val file = modelFile ?: AiModelManager(context).bundledModelFile()
            if (file != null && file.exists()) {
                interpreter = Interpreter(loadModelFile(file))
                inputSize = interpreter?.getInputTensor(0)?.shape()?.getOrNull(1) ?: 0
                Log.i(TAG, "Loaded TFLite model ${file.name} (input size $inputSize)")
            } else {
                Log.i(TAG, "No TFLite model bundled; using classic DSP fallback")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TFLite init failed; falling back to classic DSP", e)
            interpreter = null
            inputSize = 0
        }
    }

    override fun enhance(samples: FloatArray): FloatArray {
        val tf = interpreter ?: return fallback.enhance(samples)
        if (inputSize <= 0) return fallback.enhance(samples)

        val output = FloatArray(samples.size)
        var offset = 0
        try {
            while (offset < samples.size) {
                val length = minOf(inputSize, samples.size - offset)
                val chunk = FloatArray(inputSize)
                samples.copyInto(chunk, 0, offset, offset + length)
                val result = Array(1) { FloatArray(inputSize) }
                tf.run(Array(1) { chunk }, result)
                result[0].copyInto(output, offset, 0, length)
                offset += inputSize
            }
            return output
        } catch (e: Exception) {
            return fallback.enhance(samples)
        }
    }

    private fun loadModelFile(file: File): MappedByteBuffer {
        file.inputStream().use { input ->
            val channel = input.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "TfLiteEnhancer"

        /** Creates the enhancer wired to whatever model is bundled in assets. */
        fun load(context: Context): TfLiteEnhancer =
            TfLiteEnhancer(context, AiModelManager(context).bundledModelFile())
    }
}
