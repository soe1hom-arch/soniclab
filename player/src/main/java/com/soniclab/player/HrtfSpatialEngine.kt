package com.soniclab.player

class HrtfSpatialEngine private constructor(
    private val nativeHandle: Long
) {
    companion object {
        @Volatile
        private var libraryLoaded = false

        fun create(
            sampleRate: Int,
            hrtfAssetPath: String
        ): HrtfSpatialEngine? {
            if (!loadNativeLibrary()) {
                return null
            }

            return try {
                val handle = nativeInit(sampleRate, hrtfAssetPath)

                if (handle != 0L) {
                    HrtfSpatialEngine(handle)
                } else {
                    null
                }
            } catch (error: UnsatisfiedLinkError) {
                null
            } catch (error: Exception) {
                null
            }
        }

        private fun loadNativeLibrary(): Boolean {
            if (libraryLoaded) {
                return true
            }

            return try {
                System.loadLibrary("soniclab_hrtf")
                libraryLoaded = true
                true
            } catch (error: UnsatisfiedLinkError) {
                false
            } catch (error: Exception) {
                false
            }
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