/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder → float → DSP chain → float, extracted from [DspAudioSink] so the
 * core can be unit-tested on the JVM.
 *
 * - Accepts 16/24/32-bit and float PCM from the renderer,
 * - normalizes everything to 32-bit float before the chain,
 * - runs the chain processors (all configured in the float domain),
 * - returns float PCM bytes for the output sink (may be empty while a
 *   chunk-based processor such as the enhancer accumulates frames).
 */
class DspFramePipeline(private val processors: List<AudioProcessor>) {

    var sampleRateHz: Int = 0
        private set

    var inputChannels: Int = 0
        private set

    var outputChannels: Int = 0
        private set

    private var configured = false

    /** Configures every processor for float-domain processing. */
    fun configure(input: AudioProcessor.AudioFormat) {
        sampleRateHz = input.sampleRate
        inputChannels = input.channelCount
        var af = AudioProcessor.AudioFormat(input.sampleRate, input.channelCount, C.ENCODING_PCM_FLOAT)
        for (p in processors) af = p.configure(af)
        outputChannels = af.channelCount
        configured = true
    }

    fun isConfigured(): Boolean = configured

    /**
     * Consumes [input] fully and returns the chain output as little-endian
     * float PCM bytes. Empty when nothing could be emitted yet (the enhancer
     * buffers 512-frame chunks).
     */
    fun process(input: ByteBuffer, encoding: Int): ByteBuffer {
        check(configured) { "DspFramePipeline is not configured" }
        val floatInput = decodeToFloat(input, encoding)
        return runChain(floatInput)
    }

    /**
     * Ends the stream: queues end-of-stream on every processor and pumps any
     * remaining tail (e.g. the enhancer's final partial chunk) through the
     * rest of the chain. Returns float PCM bytes.
     */
    fun endOfStream(): ByteBuffer {
        var tail = EMPTY
        for (i in processors.indices) {
            processors[i].queueEndOfStream()
            val out = processors[i].getOutput()
            if (!out.hasRemaining()) continue
            var buf = out
            for (j in i + 1 until processors.size) {
                processors[j].queueInput(buf)
                buf = processors[j].getOutput()
            }
            if (tail.hasRemaining()) {
                val merged = ByteBuffer.allocate(tail.remaining() + buf.remaining()).order(ByteOrder.LITTLE_ENDIAN)
                merged.put(tail)
                merged.put(buf)
                merged.flip()
                tail = merged
            } else {
                tail = buf
            }
        }
        return tail
    }

    fun flush() {
        for (p in processors) p.flush()
    }

    fun reset() {
        for (p in processors) p.reset()
        configured = false
    }

    private fun runChain(input: ByteBuffer): ByteBuffer {
        var buf = input
        for (p in processors) {
            p.queueInput(buf)
            buf = p.getOutput()
        }
        return buf
    }

    private fun decodeToFloat(input: ByteBuffer, encoding: Int): ByteBuffer {
        input.order(ByteOrder.LITTLE_ENDIAN)
        if (encoding == C.ENCODING_PCM_FLOAT) {
            // Already float: share the bytes; consumed below via get().
            val out = ByteBuffer.allocate(input.remaining()).order(ByteOrder.LITTLE_ENDIAN)
            out.put(input)
            out.flip()
            return out
        }
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            else -> throw IllegalStateException("Unsupported PCM encoding $encoding")
        }
        val frames = input.remaining() / bytesPerSample / inputChannels
        val out = ByteBuffer.allocate(frames * inputChannels * 4).order(ByteOrder.LITTLE_ENDIAN)
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (input.hasRemaining()) out.putFloat(input.short.toFloat() / 32768f)
            }
            C.ENCODING_PCM_24BIT -> {
                while (input.hasRemaining()) {
                    val b0 = input.get().toInt() and 0xFF
                    val b1 = input.get().toInt() and 0xFF
                    val b2 = input.get().toInt() and 0xFF
                    var s = (b2 shl 16) or (b1 shl 8) or b0
                    if (s and 0x800000 != 0) s = s or -0x1000000
                    out.putFloat(s / 8388608f)
                }
            }
            C.ENCODING_PCM_32BIT -> {
                while (input.hasRemaining()) out.putFloat(input.int / 2147483648f)
            }
        }
        out.flip()
        return out
    }

    private companion object {
        val EMPTY: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    }
}
