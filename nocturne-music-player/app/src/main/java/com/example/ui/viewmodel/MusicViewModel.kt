package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

import com.example.media.MusicPlayerManager

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = androidx.room.Room.databaseBuilder(
        application,
        MusicDatabase::class.java,
        "music_player_db"
    ).build()

    private val repository = MusicRepository(application, database.musicDao())

    // UI Flows from Database
    val songs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsWithSongs: StateFlow<List<PlaylistWithSongs>> = repository.playlistsWithSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<PlayerSettings> = repository.settingsFlow
        .map { it ?: PlayerSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerSettings())

    // Active Player States delegated to MusicPlayerManager
    val currentSong: StateFlow<Song?> = MusicPlayerManager.currentSong
    val isPlaying: StateFlow<Boolean> = MusicPlayerManager.isPlaying
    val currentPosition: StateFlow<Long> = MusicPlayerManager.currentPosition
    val duration: StateFlow<Long> = MusicPlayerManager.duration
    val shuffleEnabled: StateFlow<Boolean> = MusicPlayerManager.shuffleEnabled
    val repeatMode: StateFlow<String> = MusicPlayerManager.repeatMode

    private val _importingState = MutableStateFlow<String?>(null) // null = idle, or loading message
    val importingState: StateFlow<String?> = _importingState.asStateFlow()

    init {
        // Initialize MusicPlayerManager with context
        MusicPlayerManager.init(application)
        
        // Sync playback speed with settings
        viewModelScope.launch {
            repository.getSettings()
            settings.collect { s ->
                MusicPlayerManager.playbackSpeed = s.playbackSpeed
            }
        }
    }

    // -----------------------------------------------------
    // Media Player Operations
    // -----------------------------------------------------

    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        MusicPlayerManager.playSong(song, queue)
    }

    fun togglePlayPause() {
        MusicPlayerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        MusicPlayerManager.seekTo(positionMs)
    }

    fun nextTrack() {
        MusicPlayerManager.nextTrack()
    }

    fun previousTrack() {
        MusicPlayerManager.previousTrack()
    }

    fun toggleShuffle() {
        MusicPlayerManager.toggleShuffle()
    }

    fun toggleRepeatMode() {
        MusicPlayerManager.toggleRepeatMode()
    }

    override fun onCleared() {
        super.onCleared()
        database.close()
    }

    // -----------------------------------------------------
    // Database and Repository Actions
    // -----------------------------------------------------

    fun importSongFromUri(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            _importingState.value = "Importing file..."
            val song = repository.importAudioFile(uri, fileName)
            if (song != null) {
                _importingState.value = "Imported: ${song.title}"
            } else {
                _importingState.value = "Failed to import song."
            }
            delay(1500)
            _importingState.value = null
        }
    }

    fun generateZenSynthTrack(title: String, type: String) {
        viewModelScope.launch {
            _importingState.value = "Generating Ambient track..."
            val song = repository.generateZenAmbientTrack(title, type)
            if (song != null) {
                _importingState.value = "Generated: ${song.title}"
            } else {
                _importingState.value = "Synthesizer failed."
            }
            delay(1500)
            _importingState.value = null
        }
    }

    fun downloadPremiumDemoTrack(title: String, artist: String, album: String, url: String) {
        viewModelScope.launch {
            _importingState.value = "Downloading high quality offline track..."
            val song = repository.downloadDemoSong(title, artist, album, url)
            if (song != null) {
                _importingState.value = "Downloaded: ${song.title}"
            } else {
                _importingState.value = "Download failed."
            }
            delay(1500)
            _importingState.value = null
        }
    }

    fun deleteSongFromLibrary(song: Song) {
        viewModelScope.launch {
            if (currentSong.value?.id == song.id) {
                MusicPlayerManager.stopPlayback()
            }
            repository.deleteSong(song)
        }
    }

    // Playlists
    fun createNewPlaylist(name: String, desc: String) {
        viewModelScope.launch {
            repository.createPlaylist(name, desc)
        }
    }

    fun deleteUserPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun updatePlaylistInfo(id: Int, name: String, desc: String) {
        viewModelScope.launch {
            repository.updatePlaylist(id, name, desc)
        }
    }

    fun addSongToUserPlaylist(playlistId: Int, songId: Int) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromUserPlaylist(playlistId: Int, songId: Int) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    // Settings
    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(themeMode = mode))
        }
    }

    fun updateAccentColorIndex(index: Int) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(accentColorIndex = index))
        }
    }

    fun updateFontSizeValue(size: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(fontSize = size))
        }
    }

    fun updatePlayerStyleValue(style: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(playerStyle = style))
        }
    }

    fun updatePlaybackSpeedValue(speed: Float) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(playbackSpeed = speed))
            MusicPlayerManager.playbackSpeed = speed
        }
    }

    fun updateAudioQualityValue(quality: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(audioQuality = quality))
        }
    }

    fun updateHapticFeedbackValue(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(hapticFeedbackEnabled = enabled))
        }
    }

    fun updateAnimationsValue(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(animationsEnabled = enabled))
        }
    }

    fun updateLanguageValue(language: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(language = language))
        }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            repository.saveSettings(PlayerSettings())
            MusicPlayerManager.playbackSpeed = 1.0f
        }
    }
}
