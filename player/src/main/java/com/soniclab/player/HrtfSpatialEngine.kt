package com.soniclab.player

class HrtfSpatialEngine private constructor(
    private val nativeHandle: Long
) {

    fun process(
        input: FloatArray,
        output: FloatArray,
        frames: Int,
        azimuthDegrees: Float,
        elevationDegrees: Float,
        reverbWet: Float
    ): Boolean {
        if (nativeHandle == 0L) {
            return false
        }

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
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
        }
    }

    companion object {
        init {
            System.loadLibrary("soniclab_hrtf")
        }

        fun create(
            sampleRate: Int,
            hrtfAssetPath: String
        ): HrtfSpatialEngine? {
            val handle = nativeCreate(sampleRate, hrtfAssetPath)

            return if (handle != 0L) {
                HrtfSpatialEngine(handle)
            } else {
                null
            }
        }

        private external fun nativeCreate(
            sampleRate: Int,
            hrtfAssetPath: String
        ): Long

        private external fun nativeProcess(
            handle: Long,
            input: FloatArray,
            output: FloatArray,
            frames: Int,
            azimuthDegrees: Float,
            elevationDegrees: Float,
            reverbWet: Float
        ): Boolean

        private external fun nativeDestroy(handle: Long)
    }
}