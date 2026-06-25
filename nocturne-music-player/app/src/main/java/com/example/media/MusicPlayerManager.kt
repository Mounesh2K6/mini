package com.example.media

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import com.example.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

object MusicPlayerManager {
    private const val TAG = "MusicPlayerManager"

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var appContext: Context? = null

    // Player States
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow("NONE") // NONE, ONE, ALL
    val repeatMode = _repeatMode.asStateFlow()

    var currentQueue: List<Song> = emptyList()
    var currentQueueIndex: Int = -1

    var playbackSpeed: Float = 1.0f
        set(value) {
            field = value
            applyPlaybackSpeed()
        }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun startService() {
        appContext?.let { ctx ->
            try {
                val intent = Intent(ctx, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MusicPlaybackService", e)
            }
        }
    }

    private fun updateServiceNotification() {
        appContext?.let { ctx ->
            val intent = Intent(ctx, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
            }
            ctx.startService(intent)
        }
    }

    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        try {
            if (queue.isNotEmpty()) {
                currentQueue = queue
                currentQueueIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            } else if (!currentQueue.any { it.id == song.id }) {
                currentQueue = listOf(song)
                currentQueueIndex = 0
            } else {
                currentQueueIndex = currentQueue.indexOfFirst { it.id == song.id }
            }

            _currentSong.value = song
            _duration.value = song.durationMs
            _currentPosition.value = 0L

            releasePlayer()

            val file = File(song.filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found offline: ${song.filePath}")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(song.filePath)
                prepare()
                applyPlaybackSpeed(this)
                start()
                setOnCompletionListener {
                    handleSongCompletion()
                }
            }

            _isPlaying.value = true
            startProgressPolling()
            startService()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing song: ${song.title}", e)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            stopProgressPolling()
            updateServiceNotification()
        } else {
            player.start()
            applyPlaybackSpeed(player)
            _isPlaying.value = true
            startProgressPolling()
            startService() // Ensures service is in foreground when playing
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs.toInt())
            _currentPosition.value = positionMs
        }
    }

    fun nextTrack() {
        if (currentQueue.isEmpty()) return

        if (repeatMode.value == "ONE") {
            currentSong.value?.let { playSong(it) }
            return
        }

        val nextIndex = when {
            shuffleEnabled.value -> (currentQueue.indices).random()
            currentQueueIndex < currentQueue.size - 1 -> currentQueueIndex + 1
            repeatMode.value == "ALL" -> 0
            else -> -1
        }

        if (nextIndex in currentQueue.indices) {
            playSong(currentQueue[nextIndex], currentQueue)
        } else {
            releasePlayer()
            _isPlaying.value = false
            _currentPosition.value = 0L
            updateServiceNotification()
        }
    }

    fun previousTrack() {
        if (currentQueue.isEmpty()) return

        if (_currentPosition.value > 3000) {
            seekTo(0)
            return
        }

        val prevIndex = when {
            shuffleEnabled.value -> (currentQueue.indices).random()
            currentQueueIndex > 0 -> currentQueueIndex - 1
            repeatMode.value == "ALL" -> currentQueue.size - 1
            else -> -1
        }

        if (prevIndex in currentQueue.indices) {
            playSong(currentQueue[prevIndex], currentQueue)
        } else {
            currentSong.value?.let { playSong(it) }
        }
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            "NONE" -> "ALL"
            "ALL" -> "ONE"
            else -> "NONE"
        }
    }

    private fun handleSongCompletion() {
        nextTrack()
    }

    private fun applyPlaybackSpeed(player: MediaPlayer? = mediaPlayer) {
        val p = player ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                p.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set playback speed: $playbackSpeed", e)
            }
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _currentPosition.value = player.currentPosition.toLong()
                        }
                    } catch (e: Exception) {
                        // Player might be in transition/released
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
    }

    fun releasePlayer() {
        stopProgressPolling()
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (e: Exception) {}
            player.release()
        }
        mediaPlayer = null
    }

    fun stopPlayback() {
        releasePlayer()
        _currentSong.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        updateServiceNotification()
    }
}
