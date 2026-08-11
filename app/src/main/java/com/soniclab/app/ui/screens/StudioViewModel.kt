package com.soniclab.app.ui.screens

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soniclab.analyzer.AudioInfoAnalyzer
import com.soniclab.analyzer.LoudnessAnalyzer
import com.soniclab.app.di.AppContainer
import com.soniclab.toolkit.ToolkitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Single studio entry for the playback menu: audio toolkit (convert/cut/join/
 * reverse/pitch/tempo/normalize/vocal-remover) + analyzer (info/waveform/LUFS),
 * both bound to the currently playing track.
 */
class StudioViewModel(private val container: AppContainer) : ViewModel() {

    var pickedUri by mutableStateOf<Uri?>(null)
        private set
    var fileName by mutableStateOf<String?>(null)
        private set
    var pickedUri2 by mutableStateOf<Uri?>(null)
        private set
    var fileName2 by mutableStateOf<String?>(null)
        private set
    var pitchSemitones by mutableFloatStateOf(0f)
        private set
    var tempoFactor by mutableFloatStateOf(1f)
        private set
    var working by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var analyzing by mutableStateOf(false)
        private set
    var info by mutableStateOf<AudioInfoAnalyzer.AudioInfo?>(null)
        private set
    var waveform by mutableStateOf<List<Pair<Float, Float>>>(emptyList())
        private set
    var lufs by mutableStateOf(0f)
        private set
    var sourceLabel by mutableStateOf<String?>(null)
        private set

    val playerState = container.playerController.uiState

    fun useCurrentTrack() {
        val track = playerState.value.currentTrack ?: return
        pickedUri = track.uri
        fileName = track.title
        pickedUri2 = null
        fileName2 = null
        message = null
    }

    fun onPick(uri: Uri?, displayName: String?) {
        pickedUri = uri
        fileName = displayName
        message = null
    }

    fun onPickSecond(uri: Uri?, displayName: String?) {
        pickedUri2 = uri
        fileName2 = displayName
        message = null
    }

    fun analyzeCurrent() {
        val track = playerState.value.currentTrack ?: return
        analyze(track.uri, track.title)
    }

    fun analyzeFile() {
        val uri = pickedUri ?: return
        analyze(uri, fileName)
    }

    fun analyze(uri: Uri, label: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            analyzing = true
            sourceLabel = label
            info = container.audioInfoAnalyzer.analyze(uri)
            waveform = container.waveformAnalyzer.analyze(uri, 400) ?: emptyList()
            lufs = computeLufs(container, uri)
            analyzing = false
        }
    }

    fun getInfo() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = container.toolkit.getInfo(uri)
            message = when (result) {
                is ToolkitResult.Info -> {
                    val info = result.info
                    "Codec: ${info.codec} • ${info.bitrateKbps} kbps • ${info.sampleRateHz} Hz • ${info.channelCount} ch • ${info.durationMs / 1000}s"
                }
                is ToolkitResult.Failure -> result.message
                is ToolkitResult.Success -> "OK"
            }
        }
    }

    fun convertToWav() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "converted_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(runWithProgress { container.toolkit.convertToWav(uri, output, it) }, "Converted")
        }
    }

    fun cut() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "cut_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(runWithProgress { container.toolkit.cut(uri, 0L, 30_000L, output, it) }, "Cut (first 30s)")
        }
    }

    fun join() {
        val uri1 = pickedUri ?: return
        val uri2 = pickedUri2 ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "join_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(runWithProgress { container.toolkit.join(listOf(uri1, uri2), output, it) }, "Joined (File 1 + File 2)")
        }
    }

    fun normalize() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "normalized_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(runWithProgress { container.toolkit.normalize(uri, output, targetLufs = -14f, onProgress = it) }, "Normalized (−14 LUFS)")
        }
    }

    fun reverse() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "reverse_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(runWithProgress { container.toolkit.reverse(uri, output, it) }, "Reversed")
        }
    }

    fun setPitch(value: Float) {
        pitchSemitones = value.coerceIn(-12f, 12f)
    }

    fun setTempo(value: Float) {
        tempoFactor = value.coerceIn(0.5f, 2f)
    }

    fun changePitch() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "pitch_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(
                runWithProgress { container.toolkit.changePitch(uri, pitchSemitones, output, onProgress = it) },
                "Pitch ${String.format(Locale.US, "%+.0f st", pitchSemitones)}"
            )
        }
    }

    fun changeTempo() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "tempo_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(
                runWithProgress { container.toolkit.changeTempo(uri, tempoFactor, output, onProgress = it) },
                "Tempo ${String.format(Locale.US, "%.2f×", tempoFactor)}"
            )
        }
    }

    fun vocalReduction() {
        val uri = pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val output = File(container.appContext.filesDir, "vocal_removed_${System.currentTimeMillis()}.wav").absolutePath
            message = finishMessage(runWithProgress { container.toolkit.vocalReduction(uri, output, it) }, "Vocal removed (instrumental)")
        }
    }

    private suspend fun runWithProgress(block: suspend (onProgress: (Float) -> Unit) -> ToolkitResult): ToolkitResult {
        working = true
        progress = 0f
        return try {
            block { progress = it }
        } finally {
            working = false
        }
    }

    private fun finishMessage(result: ToolkitResult, successPrefix: String): String = when (result) {
        is ToolkitResult.Success -> "$successPrefix: ${result.outputPath}"
        is ToolkitResult.Failure -> "Failed: ${result.message}"
        is ToolkitResult.Info -> "OK"
    }

    private fun computeLufs(container: AppContainer, uri: Uri): Float {
        val decoded = container.analyzerDecoder.decode(uri, maxSeconds = 180) ?: return -70f
        val meter = LoudnessAnalyzer(decoded.sampleRate)
        var lufs = -70f
        val step = 8192
        var i = 0
        while (i < decoded.samples.size) {
            val end = (i + step).coerceAtMost(decoded.samples.size)
            lufs = meter.push(decoded.samples.copyOfRange(i, end))
            i = end
        }
        return lufs
    }
}
