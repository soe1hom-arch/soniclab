package com.soniclab.player

/**
 * Fixed user headroom (-3..0 dB) applied before the effect chain so boosts
 * (EQ, enhancer, preset loudness) have less chance of clipping into the
 * final limiter. 0 dB = passthrough.
 */
object AudioHeadroomBridge {
    val processor = GainAudioProcessor()

    var headroomDb: Float
        get() = processor.gainDb
        set(value) {
            processor.gainDb = value.coerceIn(-3f, 0f)
        }
}
