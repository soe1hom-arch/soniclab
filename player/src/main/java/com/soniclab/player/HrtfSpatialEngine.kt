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
        @Volatile
        private var libraryLoaded = false

        @Synchronized
        private fun ensureLibraryLoaded(): Boolean {
            if (libraryLoaded) {
                return true
            }

            return try {
                System.loadLibrary("soniclab_hrtf")
                libraryLoaded = true
                true
            } catch (error: UnsatisfiedLinkError) {
                false
            } catch (error: SecurityException) {
                false
            }
        }

        fun create(
            sampleRate: Int,
            hrtfAssetPath: String
        ): HrtfSpatialEngine? {
            if (!ensureLibraryLoaded()) {
                return null
            }

            return try {
                val handle = nativeCreate(sampleRate, hrtfAssetPath)

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