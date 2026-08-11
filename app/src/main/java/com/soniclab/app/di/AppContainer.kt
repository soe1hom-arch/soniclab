package com.soniclab.app.di

import android.content.Context
import com.soniclab.ai.AiModelManager
import com.soniclab.ai.AiVocalRemover
import com.soniclab.ai.TfLiteEnhancer
import com.soniclab.analyzer.AudioInfoAnalyzer
import com.soniclab.analyzer.PcmReader
import com.soniclab.analyzer.WaveformAnalyzer
import com.soniclab.audioengine.AudioEffectEngine
import com.soniclab.library.LibraryRepository
import com.soniclab.player.AudioEnhanceBridge
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
    val audioEffects: AudioEffectEngine = AudioEffectEngine()
    val visualizerEngine: VisualizerEngine = VisualizerEngine()

    val toolkit: MediaCodecAudioToolkit = MediaCodecAudioToolkit(appContext)
    val aiModelManager: AiModelManager = AiModelManager(appContext)
    val enhancer: TfLiteEnhancer = TfLiteEnhancer.load(appContext)
    val vocalRemover: AiVocalRemover = AiVocalRemover()

    init {
        // Feed the on-device enhancer into the playback audio pipeline.
        AudioEnhanceBridge.enhancer = enhancer
    }

    val audioInfoAnalyzer: AudioInfoAnalyzer = AudioInfoAnalyzer(appContext)
    val analyzerDecoder: PcmReader = PcmReader(appContext)
    val waveformAnalyzer: WaveformAnalyzer = WaveformAnalyzer(appContext)
}
