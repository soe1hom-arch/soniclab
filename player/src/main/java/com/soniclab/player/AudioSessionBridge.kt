/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

/**
 * Shares the ExoPlayer audio session id from the service to the app process.
 * (Media3 1.5 Player interface does not expose getAudioSessionId on MediaController.)
 */
object AudioSessionBridge {
    var sessionId: Int = 0
}
