/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSkipTest {
    @Test
    fun skipAfterQueueEndedStartsPlayback() {
        assertTrue(shouldResumeAfterSkip(Player.STATE_ENDED, playWhenReady = false))
        assertTrue(shouldResumeAfterSkip(Player.STATE_ENDED, playWhenReady = true))
    }

    @Test
    fun skipAfterPlaybackErrorResumesOnlyWhenPlayWasRequested() {
        assertTrue(shouldResumeAfterSkip(Player.STATE_IDLE, playWhenReady = true))
        assertFalse(shouldResumeAfterSkip(Player.STATE_IDLE, playWhenReady = false))
    }

    @Test
    fun skipKeepsDeliberatePause() {
        assertFalse(shouldResumeAfterSkip(Player.STATE_READY, playWhenReady = false))
        assertFalse(shouldResumeAfterSkip(Player.STATE_BUFFERING, playWhenReady = false))
    }
}
