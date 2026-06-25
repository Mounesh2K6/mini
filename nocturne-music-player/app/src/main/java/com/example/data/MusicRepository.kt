package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.exp
import kotlin.math.sin

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {
    val allSongs: Flow<List<Song>> = musicDao.getAllSongs()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val playlistsWithSongs: Flow<List<PlaylistWithSongs>> = musicDao.getAllPlaylistsWithSongs()
    val settingsFlow: Flow<PlayerSettings?> = musicDao.getSettingsFlow()

    suspend fun getSettings(): PlayerSettings {
        return musicDao.getSettings() ?: PlayerSettings().also {
            musicDao.insertSettings(it)
        }
    }

    suspend fun saveSettings(settings: PlayerSettings) {
        musicDao.insertSettings(settings)
    }

    suspend fun createPlaylist(name: String, description: String): Long {
        val playlist = Playlist(name = name, description = description)
        return musicDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        musicDao.deletePlaylist(playlist)
    }

    suspend fun updatePlaylist(id: Int, name: String, desc: String) {
        musicDao.updatePlaylist(id, name, desc)
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: Int) {
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        musicDao.deletePlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
    }

    fun getPlaylistWithSongs(playlistId: Int): Flow<PlaylistWithSongs?> {
        return musicDao.getPlaylistWithSongs(playlistId)
    }

    suspend fun deleteSong(song: Song) {
        // Delete physical file first
        try {
            val file = File(song.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to delete file: ${song.filePath}", e)
        }
        musicDao.deleteSong(song)
    }

    /**
     * Imports an audio file from a Content Uri (SAF) into the app's internal files directory.
     */
    suspend fun importAudioFile(uri: Uri, originalName: String?): Song? = withContext(Dispatchers.IO) {
        try {
            val musicDir = File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
            val uniqueName = "${UUID.randomUUID()}_${originalName ?: "imported_track.mp3"}"
            val targetFile = File(musicDir, uniqueName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Extract metadata
            val retriever = MediaMetadataRetriever()
            var title = originalName?.substringBeforeLast(".") ?: "Unknown Track"
            var artist = "Unknown Artist"
            var album = "Unknown Album"
            var durationMs = 0L

            try {
                retriever.setDataSource(targetFile.absolutePath)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: artist
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: album
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    durationMs = durStr.toLong()
                }
            } catch (ex: Exception) {
                Log.e("MusicRepository", "Error extracting metadata, falling back to defaults", ex)
            } finally {
                retriever.release()
            }

            if (durationMs <= 0L) {
                // Approximate duration (fallback)
                durationMs = 180000L // 3 minutes
            }

            val song = Song(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                filePath = targetFile.absolutePath
            )

            val songId = musicDao.insertSong(song)
            return@withContext song.copy(id = songId.toInt())
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to import file", e)
            return@withContext null
        }
    }

    /**
     * Generates a procedurally synthesized Zen meditation track as a physical WAV file
     * and saves it in the local music directory.
     */
    suspend fun generateZenAmbientTrack(title: String, type: String): Song? = withContext(Dispatchers.IO) {
        try {
            val musicDir = File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
            val uniqueName = "zen_${type}_${System.currentTimeMillis()}.wav"
            val targetFile = File(musicDir, uniqueName)

            val sampleRate = 22050
            val durationSeconds = 30
            val totalSamples = sampleRate * durationSeconds
            val bitsPerSample = 16
            val numChannels = 1
            val dataSize = totalSamples * (bitsPerSample / 8) * numChannels
            val totalFilesize = dataSize + 36

            // Prepare WAV Header
            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put("RIFF".toByteArray())
                putInt(totalFilesize)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16) // Subchunk1Size for PCM
                putShort(1) // AudioFormat: 1 = PCM
                putShort(numChannels.toShort())
                putInt(sampleRate)
                putInt(sampleRate * numChannels * (bitsPerSample / 8)) // ByteRate
                putShort((numChannels * (bitsPerSample / 8)).toShort()) // BlockAlign
                putShort(bitsPerSample.toShort()) // BitsPerSample
                put("data".toByteArray())
                putInt(dataSize)
            }

            FileOutputStream(targetFile).use { fos ->
                fos.write(header.array())

                // Chords based on Zen Ambient theme
                // Let's create beautiful ambient chords
                // Chord 1: Cmaj9 (C3=130.81, E3=164.81, G3=196.00, B3=246.94, D4=293.66)
                // Chord 2: Am9 (A2=110.00, C3=130.81, E3=164.81, G3=196.00, B3=246.94)
                // Chord 3: Fmaj7#11 (F2=87.31, A3=220.00, C4=261.63, E4=329.63, G4=392.00)
                // Chord 4: G6/9 (G2=98.00, B3=246.94, D4=293.66, E4=329.63, A4=440.00)

                val chords = when (type) {
                    "lofi" -> listOf(
                        doubleArrayOf(130.81, 164.81, 196.00, 246.94, 293.66), // Cmaj9
                        doubleArrayOf(110.00, 130.81, 164.81, 196.00, 246.94), // Am9
                        doubleArrayOf(87.31, 220.00, 261.63, 329.63, 392.00),  // Fmaj7#11
                        doubleArrayOf(98.00, 246.94, 293.66, 329.63, 440.00)   // G6/9
                    )
                    "sleep" -> listOf(
                        doubleArrayOf(110.00, 164.81, 220.00, 261.63), // Am
                        doubleArrayOf(146.83, 196.00, 293.66, 349.23), // Dm7
                        doubleArrayOf(130.81, 196.00, 261.63, 329.63), // Cmaj7
                        doubleArrayOf(123.47, 164.81, 246.94, 329.63)  // Em7
                    )
                    else -> listOf(
                        doubleArrayOf(130.81, 196.00, 261.63, 329.63), // Cmaj7
                        doubleArrayOf(174.61, 220.00, 261.63, 349.23), // Fmaj7
                        doubleArrayOf(130.81, 196.00, 261.63, 329.63), // Cmaj7
                        doubleArrayOf(196.00, 246.94, 293.66, 392.00)  // G
                    )
                }

                val chordDuration = 7.5 // 4 chords * 7.5s = 30 seconds
                val bufferSize = 4096
                val byteBuffer = ByteBuffer.allocate(bufferSize * 2).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                }

                var sampleIndex = 0
                while (sampleIndex < totalSamples) {
                    byteBuffer.clear()
                    val count = minOf(bufferSize, totalSamples - sampleIndex)
                    for (k in 0 until count) {
                        val currentSample = sampleIndex + k
                        val t = currentSample.toDouble() / sampleRate.toDouble()
                        val chordIdx = (t / chordDuration).toInt().coerceIn(0, chords.size - 1)
                        val tc = t % chordDuration
                        val activeChord = chords[chordIdx]

                        // Envelope: 1s Fade-in, 1s Fade-out per chord
                        val env = if (tc < 1.2) {
                            tc / 1.2
                        } else if (tc > (chordDuration - 1.2)) {
                            (chordDuration - tc) / 1.2
                        } else {
                            1.0
                        }

                        // Combine chord notes
                        var sum = 0.0
                        for (freq in activeChord) {
                            // Sine wave
                            sum += sin(2.0 * Math.PI * freq * t)
                        }
                        sum /= activeChord.size

                        // Add subtle sub-bass (fundamental frequency divided by 2 or octaves down)
                        val subBassFreq = activeChord[0] / 2.0
                        if (subBassFreq > 20.0) {
                            sum += 0.4 * sin(2.0 * Math.PI * subBassFreq * t)
                        }

                        // Add a beautiful high lofi pentatonic bell-pluck every 2.5 seconds
                        val pluckPeriod = 2.5
                        val pluckTime = t % pluckPeriod
                        val pluckAge = pluckTime
                        val pluckIdx = (t / pluckPeriod).toInt()
                        // Bell frequencies in Pentatonic Scale (A minor pentatonic)
                        val bellFreqs = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.00, 1046.50)
                        val bellFreq = bellFreqs[pluckIdx % bellFreqs.size]
                        val pluckEnv = exp(-4.5 * pluckAge) // rapid decay
                        val pluckSignal = sin(2.0 * Math.PI * bellFreq * t) * pluckEnv * 0.25

                        // Warm Lofi noise (rain texture)
                        val noise = (Math.random() * 2.0 - 1.0) * 0.05

                        // Apply envelope & mix
                        val mixed = (sum * 0.55 + pluckSignal) * env + noise
                        val clamped = mixed.coerceIn(-1.0, 1.0)
                        val shortVal = (clamped * 32767.0).toInt().toShort()
                        byteBuffer.putShort(shortVal)
                    }
                    fos.write(byteBuffer.array(), 0, count * 2)
                    sampleIndex += count
                }
            }

            // Save details in database
            val artistName = when (type) {
                "lofi" -> "Chilled Beats"
                "sleep" -> "Drifting Mind"
                else -> "Aura Synth"
            }
            val albumName = "Synthesized Ambience"

            val song = Song(
                title = title,
                artist = artistName,
                album = albumName,
                durationMs = 30000L,
                filePath = targetFile.absolutePath,
                isFavorite = true
            )

            val songId = musicDao.insertSong(song)
            return@withContext song.copy(id = songId.toInt())
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to generate ambient track", e)
            return@withContext null
        }
    }

    /**
     * Downloads a sample audio file from a remote URL to make a real song out of it.
     */
    suspend fun downloadDemoSong(title: String, artist: String, album: String, url: String): Song? = withContext(Dispatchers.IO) {
        try {
            val musicDir = File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
            val uniqueName = "demo_${System.currentTimeMillis()}_${title.replace(" ", "_")}.mp3"
            val targetFile = File(musicDir, uniqueName)

            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            // Read duration using metadata retriever
            val retriever = MediaMetadataRetriever()
            var durationMs = 150000L // default 2.5 minutes
            try {
                retriever.setDataSource(targetFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    durationMs = durStr.toLong()
                }
            } catch (ex: Exception) {
                Log.e("MusicRepository", "Failed to extract downloaded track length", ex)
            } finally {
                retriever.release()
            }

            val song = Song(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                filePath = targetFile.absolutePath
            )

            val songId = musicDao.insertSong(song)
            return@withContext song.copy(id = songId.toInt())
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to download demo song", e)
            return@withContext null
        }
    }
}
