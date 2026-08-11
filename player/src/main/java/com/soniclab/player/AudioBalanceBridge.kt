package com.soniclab.player

/**
 * Single-process bridge for the stereo balance control. The service reads
 * [processor]; the UI sets [balance] (-1..1).
 */
object AudioBalanceBridge {
    val processor = BalanceAudioProcessor()

    var balance: Float
        get() = processor.balance
        set(value) {
            processor.balance = value.coerceIn(-1f, 1f)
        }
}
