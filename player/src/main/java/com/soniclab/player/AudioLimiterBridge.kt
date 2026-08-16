package com.soniclab.player

/**
 * Single-process bridge for the final limiter stage of the playback chain.
 */
object AudioLimiterBridge {
    val processor = LimiterAudioProcessor()

    var enabled: Boolean
        get() = processor.enabled
        set(value) {
            processor.enabled = value
        }
}
