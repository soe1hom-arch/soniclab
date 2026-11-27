package com.soniclab.player

class HrtfSpatialEngine private constructor(
    nativeHandle: Long
) {
    @Volatile
    private var handle: Long = nativeHandle

    fun process(
        input: FloatArray,
        output: FloatArray,
        frames: Int,
        azimuthDegrees: Float,
        elevationDegrees: Float,
        reverbWet: Float
    ): Boolean {
        val currentHandle = handle
        if (currentHandle == 0L) return false
        return nativeProcess(
            currentHandle, input, output, frames,
            azimuthDegrees, elevationDegrees, reverbWet
        )
    }

    @Synchronized
    fun release() {
        val currentHandle = handle
        if (currentHandle != 0L) {
            handle = 0L
            nativeDestroy(currentHandle)
        }
    }

    companion object {
        @Volatile
        private var libraryLoaded = false

        @Synchronized
        private fun ensureLibraryLoaded(): Boolean {
            if (libraryLoaded) return true
            return try {
                System.loadLibrary("soniclab_hrtf")
                libraryLoaded = true
                android.util.Log.i("SonicLabHRTF", "NATIVE_LIBRARY_LOADED: soniclab_hrtf")
                true
            } catch (error: UnsatisfiedLinkError) {
                android.util.Log.e("SonicLabHRTF", "Native HRTF library unavailable", error)
                false
            } catch (error: SecurityException) {
                android.util.Log.e("SonicLabHRTF", "Native HRTF library access denied", error)
                false
            }
        }

        fun create(sampleRate: Int, sofaData: ByteArray): HrtfSpatialEngine? {
            if (sofaData.isEmpty() || !ensureLibraryLoaded()) return null
            return try {
                val handle = nativeCreate(sampleRate, sofaData)
                if (handle != 0L) HrtfSpatialEngine(handle) else null
            } catch (error: UnsatisfiedLinkError) {
                android.util.Log.e("SonicLabHRTF", "Native HRTF creation failed", error)
                null
            } catch (error: Exception) {
                android.util.Log.e("SonicLabHRTF", "HRTF creation failed", error)
                null
            }
        }

        private external fun nativeCreate(sampleRate: Int, sofaData: ByteArray): Long
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
