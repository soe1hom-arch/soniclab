package com.soniclab.ai

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Manages AI models bundled inside the APK assets (offline-first design).
 *
 * Place a `.tflite` flatbuffer under `ai/src/main/assets/models/` and it is
 * automatically discovered, copied to filesDir and handed to
 * [TfLiteEnhancer]. When no model is bundled the app keeps working through
 * the classic DSP fallback, so AI Enhance never depends on the network.
 */
class AiModelManager(private val context: Context) {

    data class BundledModel(
        val name: String,
        val sizeBytes: Long,
        val file: File
    )

    /** Names of `.tflite` files under assets/models/, sorted for determinism. */
    fun bundledModelNames(): List<String> =
        runCatching {
            context.assets.list(MODELS_DIR)
                ?.filter { it.endsWith(".tflite") }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())

    /**
     * Copies the first bundled model into a stable filesDir path and returns
     * it, or null when no model is bundled. Existing copies are reused.
     */
    fun bundledModelFile(name: String? = null): File? {
        val names = bundledModelNames()
        if (names.isEmpty()) {
            Log.i(TAG, "No bundled TFLite models under assets/$MODELS_DIR; classic DSP fallback active")
            return null
        }
        val assetName = name?.takeIf { it in names } ?: names.first()
        val target = File(context.filesDir, "$MODELS_DIR/$assetName")
        if (target.exists() && target.length() > 0) return target

        val copied = runCatching {
            target.parentFile?.mkdirs()
            context.assets.open("$MODELS_DIR/$assetName").use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }.isSuccess
        return target.takeIf { copied && it.exists() }
    }

    /** Metadata for every bundled model already copied to filesDir. */
    fun bundledModels(): List<BundledModel> = bundledModelNames().mapNotNull { name ->
        val file = File(context.filesDir, "$MODELS_DIR/$name")
        if (file.exists()) BundledModel(name, file.length(), file) else null
    }

    companion object {
        private const val TAG = "AiModelManager"
        private const val MODELS_DIR = "models"
    }
}
