/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSpatialBridgeTest {
    @Test
    fun publishedStateTracksPresetsAndCustomSwitches() {
        val previous = AudioSpatialBridge.state.value
        try {
            AudioSpatialBridge.mode = SpatialAudioProcessor.MODE_8D
            assertEquals(SpatialAudioProcessor.MODE_8D, AudioSpatialBridge.state.value.mode)
            assertTrue(AudioSpatialBridge.state.value.spatial8d)
            AudioSpatialBridge.spatial8d = false
            assertEquals(SpatialAudioProcessor.MODE_CUSTOM, AudioSpatialBridge.state.value.mode)
            assertFalse(AudioSpatialBridge.state.value.enabled)
            AudioSpatialBridge.surround = true
            assertTrue(AudioSpatialBridge.state.value.enabled)
        } finally {
            AudioSpatialBridge.mode = previous.mode
            if (previous.mode == SpatialAudioProcessor.MODE_CUSTOM) {
                AudioSpatialBridge.spatial3d = previous.spatial3d
                AudioSpatialBridge.spatial8d = previous.spatial8d
                AudioSpatialBridge.surround = previous.surround
            }
        }
    }
}
