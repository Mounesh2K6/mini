package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val isFavorite: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: Int
)

@Entity(tableName = "player_settings")
data class PlayerSettings(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "DARK", // LIGHT, DARK, AUTO
    val accentColorIndex: Int = 0,   // index of selected color
    val fontSize: String = "S",     // S, M, L, XL
    val playerStyle: String = "EXPANDED", // COMPACT, EXPANDED, MINIMALIST, DETAILED
    val playbackSpeed: Float = 1.0f,
    val audioQuality: String = "HIGH", // LOW, MEDIUM, HIGH, LOSSLESS
    val hapticFeedbackEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val language: String = "English"
)

data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(PlaylistSongCrossRef::class, parentColumn = "playlistId", entityColumn = "songId")
    )
    val songs: List<Song>
)

@Dao
interface MusicDao {
    // Songs
    @Query("SELECT * FROM songs ORDER BY importedAt DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Int): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Delete
    suspend fun deleteSong(song: Song)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Int): Flow<PlaylistWithSongs?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :name, description = :desc WHERE id = :id")
    suspend fun updatePlaylist(id: Int, name: String, desc: String)

    // PlaylistSongs
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Delete
    suspend fun deletePlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Int)

    // Settings
    @Query("SELECT * FROM player_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<PlayerSettings?>

    @Query("SELECT * FROM player_settings WHERE id = 1")
    suspend fun getSettings(): PlayerSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: PlayerSettings)
}

@Database(
    entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class, PlayerSettings::class],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
}
