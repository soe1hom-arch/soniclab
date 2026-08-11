package com.soniclab.player

/**
 * Single-process bridge for the 3D/8D spatial effect. The service reads
 * [processor]; the UI sets [mode], [widthStrength] and [rotationSeconds].
 */
object AudioSpatialBridge {
    val processor = SpatialAudioProcessor()

    var mode: Int
        get() = processor.mode
        set(value) {
            processor.mode = value
        }

    var widthStrength: Float
        get() = processor.widthStrength
        set(value) {
            processor.widthStrength = value.coerceIn(0f, 1f)
        }

    var rotationSeconds: Float
        get() = processor.rotationSeconds
        set(value) {
            processor.rotationSeconds = value.coerceIn(4f, 60f)
        }
}
