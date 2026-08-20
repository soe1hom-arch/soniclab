/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.audioengine

import java.util.concurrent.atomic.AtomicInteger

/**
 * Low-latency audio engine backed by the native `libsoniclab_oboe.so`
 * (Oboe C++ + JNI). Supports capture, playback, and full-duplex on the
 * device's preferred fast audio path (48 kHz / float / low latency).
 *
 * Capture delivers interleaved stereo float frames to [startCapture]'s
 * listener; playback asks the caller for the next chunk via [playbackSource]
 * and plays silence when nothing is supplied. Callbacks run on Oboe's
 * real-time audio thread, so handlers must be cheap and must not allocate.
 */
class OboeNativeEngine : OboeEngine {

    @Volatile
    private var captureListener: ((FloatArray) -> Unit)? = null

    @Volatile
    private var playbackSource: (() -> FloatArray?)? = null

    private val sessionId = AtomicInteger(0)

    @Volatile
    private var started = false

    override fun startCapture(onFrames: (FloatArray) -> Unit): Boolean {
        captureListener = onFrames
        started = nativeStart(this, MODE_CAPTURE, sessionId.incrementAndGet())
        return started
    }

    override fun startPlayback(): Boolean {
        started = nativeStart(this, MODE_PLAYBACK, sessionId.incrementAndGet())
        return started
    }

    /** Starts mic capture and speaker output in the same session. */
    fun startFullDuplex(
        onFrames: (FloatArray) -> Unit,
        playbackSource: (() -> FloatArray?)? = null
    ): Boolean {
        captureListener = onFrames
        this.playbackSource = playbackSource
        started = nativeStart(this, MODE_FULL_DUPLEX, sessionId.incrementAndGet())
        return started
    }

    override fun stop() {
        nativeStop()
    }

    /** Stops and tears the native streams down; safe to call at any time. */
    fun release() {
        nativeRelease()
        started = false
    }

    val isRunning: Boolean
        get() = started && nativeIsRunning()

    /** Called from native on the audio thread for captured frames. */
    @Suppress("unused")
    fun onCapture(sessionId: Int, frames: FloatArray) {
        captureListener?.invoke(frames)
    }

    /**
     * Called from native on the audio thread to request the next playback
     * chunk. Returns null (silence) when no renderer is attached.
     */
    @Suppress("unused")
    fun onPlayback(sessionId: Int, numFrames: Int): FloatArray? {
        return playbackSource?.invoke()
    }

    companion object {
        private const val MODE_CAPTURE = 0
        private const val MODE_PLAYBACK = 1
        private const val MODE_FULL_DUPLEX = 2

        init {
            System.loadLibrary("soniclab_oboe")
        }
    }

    private external fun nativeStart(
        callback: OboeNativeEngine,
        mode: Int,
        sessionId: Int
    ): Boolean

    private external fun nativeStop()
    private external fun nativeRelease()
    private external fun nativeIsRunning(): Boolean
}
