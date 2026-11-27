package com.soniclab.player.audio

class HrtfSpatialEngine private constructor(
    private val nativeHandle: Long
) {
    companion object {
        init {
            System.loadLibrary("soniclab_hrtf")
        }

        fun create(sampleRate: Int, hrtfAssetPath: String): HrtfSpatialEngine {
            val handle = nativeInit(sampleRate, hrtfAssetPath)
            require(handle != 0L) { "Gagal memuat HRTF engine" }
            return HrtfSpatialEngine(handle)
        }

        private external fun nativeInit(
            sampleRate: Int,
            hrtfAssetPath: String
        ): Long
    }

    fun process(
        input: FloatArray,
        output: FloatArray,
        frames: Int,
        azimuthDegrees: Float,
        elevationDegrees: Float,
        reverbWet: Float
    ): Int {
        return nativeProcess(
            nativeHandle,
            input,
            output,
            frames,
            azimuthDegrees,
            elevationDegrees,
            reverbWet
        )
    }

    fun release() {
        nativeRelease(nativeHandle)
    }

    private external fun nativeProcess(
        handle: Long,
        input: FloatArray,
        output: FloatArray,
        frames: Int,
        azimuthDegrees: Float,
        elevationDegrees: Float,
        reverbWet: Float
    ): Int

    private external fun nativeRelease(handle: Long)
}