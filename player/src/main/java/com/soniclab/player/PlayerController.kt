package com.soniclab.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.soniclab.analyzer.LoudnessAnalyzer
import com.soniclab.analyzer.PcmReader
import com.soniclab.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PlayerController"

/**
 * App-facing controller that binds to [PlaybackService] through a MediaController
 * and exposes a reactive [PlayerUiState] for Compose.
 */
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    /** Crossfade duration in ms; 0 disables crossfading. */
    private var crossfadeMs: Long = 0L
    private var fadeJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                fadeJob?.cancel()
                fadeJob = null
                controller?.volume = 1f
            }
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            updateTicker(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val playing = playbackState == Player.STATE_READY
            _uiState.value = _uiState.value.copy(
                isPlaying = playing && (controller?.playWhenReady == true),
                isBuffering = playbackState == Player.STATE_BUFFERING
            )
            updateTicker(_uiState.value.isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            fadeJob?.cancel()
            fadeJob = null
            if (crossfadeMs > 0) {
                scope.launch {
                    controller?.volume = 0f
                    fadeVolume(from = 0f, to = 1f)
                }
            }
            if (autoNormalize) {
                val uri = mediaItem?.localConfiguration?.uri
                scope.launch(Dispatchers.IO) {
                    val gain = computeReplayGainDb(uri)
                    AudioGainBridge.gainDb = gain ?: 0f
                }
            }
            syncFromController()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _uiState.value = _uiState.value.copy(playbackSpeed = playbackParameters.speed)
        }
    }

    fun bind() {
        if (controller != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                controller?.addListener(playerListener)
                syncFromController()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to PlaybackService", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }
        controller?.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
        controller?.prepare()
        controller?.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
    }

    fun playTrackFromIndex(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (fromIndex !in 0 until count || toIndex !in 0 until count || fromIndex == toIndex) return
        c.moveMediaItem(fromIndex, toIndex)
    }

    fun playTrackFromQueue(trackId: Long) {
        val c = controller ?: return
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == trackId.toString()) {
                c.seekTo(i, 0L)
                c.play()
                return
            }
        }
    }

    /** ReplayGain-style auto normalization: per-track gain toward -14 LUFS. */
    private var autoNormalize = false

    fun setAutoNormalize(enabled: Boolean) {
        autoNormalize = enabled
        if (!enabled) AudioGainBridge.gainDb = 0f
    }

    /**
     * Enables crossfading between consecutive tracks (fade-out on the
     * current track's tail, fade-in on the next). 0 disables it.
     * Gapless transitions are handled natively by ExoPlayer.
     */
    fun setCrossfadeMs(ms: Long) {
        crossfadeMs = ms.coerceAtLeast(0L)
        if (crossfadeMs == 0L) {
            fadeJob?.cancel()
            fadeJob = null
            controller?.volume = 1f
        }
    }

    val audioSessionId: Int
        get() = AudioSessionBridge.sessionId

    fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    private fun syncFromController() {
        val c = controller ?: return
        val mediaItem = c.currentMediaItem
        val track = mediaItem?.mediaMetadata?.let { meta ->
            val uri = mediaItem.localConfiguration?.uri ?: return@let null
            Track(
                id = mediaItem.mediaId.toLongOrNull() ?: -1L,
                title = meta.title?.toString() ?: "Unknown",
                artist = meta.artist?.toString() ?: "Unknown Artist",
                album = meta.albumTitle?.toString() ?: "Unknown Album",
                albumId = -1L,
                durationMs = c.duration.takeIf { it > 0 } ?: 0L,
                uri = uri,
                dataPath = "",
                sizeBytes = 0L,
                mimeType = null,
                dateAddedMs = 0L
            )
        }
        val queue = buildList {
            for (i in 0 until c.mediaItemCount) {
                val item = c.getMediaItemAt(i)
                val meta = item.mediaMetadata
                item.localConfiguration?.uri?.let { uri ->
                    add(
                        Track(
                            id = item.mediaId.toLongOrNull() ?: -1L,
                            title = meta.title?.toString() ?: "Unknown",
                            artist = meta.artist?.toString() ?: "Unknown Artist",
                            album = meta.albumTitle?.toString() ?: "Unknown Album",
                            albumId = -1L,
                            durationMs = 0L,
                            uri = uri,
                            dataPath = "",
                            sizeBytes = 0L,
                            mimeType = null,
                            dateAddedMs = 0L
                        )
                    )
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            currentTrack = track,
            queue = queue,
            durationMs = c.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0L,
            positionMs = c.currentPosition,
            repeatMode = c.repeatMode,
            shuffleEnabled = c.shuffleModeEnabled,
            playbackSpeed = c.playbackParameters.speed
        )
        updateTicker(c.isPlaying)
    }

    private fun updateTicker(active: Boolean) {
        tickerJob?.cancel()
        if (!active) return
        tickerJob = scope.launch {
            while (true) {
                val c = controller ?: break
                val duration = c.duration.takeIf { it > 0 } ?: _uiState.value.durationMs
                val position = c.currentPosition
                _uiState.value = _uiState.value.copy(positionMs = position, durationMs = duration)
                maybeStartCrossfade(c, duration, position)
                delay(250L)
            }
        }
    }

    /**
     * Starts a fade-out once the current track reaches its crossfade tail,
     * then advances to the next media item (fade-in handled on transition).
     */
    private fun maybeStartCrossfade(c: MediaController, duration: Long, position: Long) {
        if (crossfadeMs <= 0 || fadeJob != null || !c.isPlaying) return
        if (duration <= 0 || position < duration - crossfadeMs) return
        if (!c.hasNextMediaItem()) return
        fadeJob = scope.launch {
            fadeVolume(from = 1f, to = 0f)
            c.seekToNextMediaItem()
        }
    }

    private suspend fun fadeVolume(from: Float, to: Float) {
        val steps = 20
        val stepMs = (crossfadeMs / steps).coerceAtLeast(16L)
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            controller?.volume = from + (to - from) * fraction
            delay(stepMs)
        }
        controller?.volume = to
    }

    /** Sleep timer: pauses playback after [minutes]. Calling again replaces the timer; 0 cancels it. */
    fun startSleepTimer(minutes: Int, onFired: () -> Unit = {}): Job {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes <= 0) return Job().apply { cancel() }
        sleepTimerJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            onFired()
        }
        return sleepTimerJob ?: Job().apply { cancel() }
    }



    private fun computeReplayGainDb(uri: Uri?): Float? {
        if (uri == null) return null
        val decoded = PcmReader(context).decode(uri, maxSeconds = 45) ?: return null
        if (decoded.samples.isEmpty()) return null
        val meter = LoudnessAnalyzer(decoded.sampleRate)
        var lufs = -70f
        val step = 8192
        var i = 0
        while (i < decoded.samples.size) {
            val end = (i + step).coerceAtMost(decoded.samples.size)
            lufs = meter.push(decoded.samples.copyOfRange(i, end))
            i = end
        }
        if (lufs <= -60f) return null
        return (-14f - lufs).coerceIn(-12f, 12f)
    }
}
