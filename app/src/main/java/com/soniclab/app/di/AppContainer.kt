package com.soniclab.app.di

import android.content.Context
import com.soniclab.ai.AiModelManager
import com.soniclab.ai.TfLiteEnhancer
import com.soniclab.analyzer.AudioInfoAnalyzer
import com.soniclab.analyzer.PcmReader
import com.soniclab.analyzer.WaveformAnalyzer
import com.soniclab.library.LibraryRepository
import com.soniclab.player.AudioBalanceBridge
import com.soniclab.player.AudioEnhanceBridge
import com.soniclab.player.AudioLimiterBridge
import com.soniclab.player.AudioReverbBridge
import com.soniclab.player.AudioSpatialBridge
import com.soniclab.player.AudioToneBridge
import com.soniclab.player.PlayerController
import com.soniclab.playlist.FavoritesRepository
import com.soniclab.playlist.PlaylistRepository
import com.soniclab.settings.SettingsRepository
import com.soniclab.toolkit.MediaCodecAudioToolkit
import com.soniclab.visualizer.VisualizerEngine

/**
 * Manual dependency graph (no DI framework yet; swap for Hilt later if desired).
 * Owned by the Application; every screen resolves its dependencies from here.
 */
class AppContainer(context: Context) {

    val appContext = context.applicationContext

    val libraryRepository: LibraryRepository = LibraryRepository(appContext)
    val playlistRepository: PlaylistRepository = PlaylistRepository(appContext)
    val favoritesRepository: FavoritesRepository = FavoritesRepository(appContext)
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val playerController: PlayerController = PlayerController(appContext)
    val audioEffects: EffectsController = EffectsController()
    val visualizerEngine: VisualizerEngine = VisualizerEngine()

    val toolkit: MediaCodecAudioToolkit = MediaCodecAudioToolkit(appContext)
    val aiModelManager: AiModelManager = AiModelManager(appContext)
    val enhancer: TfLiteEnhancer = TfLiteEnhancer.load(appContext)

    init {
        // Feed the on-device enhancer into the playback audio pipeline.
        AudioEnhanceBridge.enhancer = enhancer
    }

    val audioInfoAnalyzer: AudioInfoAnalyzer = AudioInfoAnalyzer(appContext)
    val analyzerDecoder: PcmReader = PcmReader(appContext)
    val waveformAnalyzer: WaveformAnalyzer = WaveformAnalyzer(appContext)

    /**
     * Restores persisted effect/playback settings into the live bridges so
     * nothing resets when the app is closed and reopened.
     */
    fun restoreSettings() {
        val s = kotlinx.coroutines.runBlocking { settingsRepository.loadEffectSettings() }
        AudioBalanceBridge.balance = s.balance
        AudioToneBridge.trebleDb = s.trebleDb
        AudioReverbBridge.wetMix = s.reverbWet
        AudioReverbBridge.roomSize = s.reverbRoom

        AudioSpatialBridge.mode = s.spatialMode
        if (s.spatialMode == com.soniclab.player.SpatialAudioProcessor.MODE_CUSTOM) {
            AudioSpatialBridge.spatial3d = s.spatial3d
            AudioSpatialBridge.spatial8d = s.spatial8d
            AudioSpatialBridge.surround = s.surround
        }
        AudioSpatialBridge.widthStrength = s.spatialWidth
        AudioSpatialBridge.rotationSeconds = s.rotationSeconds
        AudioSpatialBridge.panDepth = s.panDepth

        val preset = com.soniclab.core.model.Preset.presets.firstOrNull { it.id == s.activePresetId }
        if (preset != null) {
            audioEffects.applyPreset(preset)
        } else {
            audioEffects.restoreCustom(
                s.bandGains,
                s.bassStrength.coerceIn(0, 1000).toShort(),
                s.virtualizerStrength.coerceIn(0, 1000).toShort()
            )
        }

        AudioLimiterBridge.enabled = s.limiterEnabled

        playerController.setDirectOutput(s.directOutputEnabled)

        playerController.setPitchSemitones(s.pitchSemitones)
        playerController.setSpeed(s.playbackSpeed)
    }
}
