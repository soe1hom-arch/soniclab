package com.soniclab.audioengine

/**
 * Low-latency audio bridge built on Oboe (C++). This scaffold exposes the
 * interface; the native .so implementation lands in a later milestone.
 */
interface OboeEngine {
    companion object {
        const val SAMPLE_RATE = 48000
        const val FRAMES_PER_BUFFER = 256
    }

    /** Starts low-latency capture from the microphone. */
    fun startCapture(onFrames: (FloatArray) -> Unit): Boolean

    /** Starts low-latency playback of processed frames. */
    fun startPlayback(): Boolean

    fun stop()
}
