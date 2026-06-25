package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ArtworkHelper {
    private const val TAG = "ArtworkHelper"
    private const val DIR_NAME = "artworks_cache"

    /**
     * Resolves the album art file for a given audio file path.
     * Extracts and caches the artwork as a JPEG file if it exists.
     * Runs synchronously, so it should be called on a background thread (e.g. inside Coil or Coroutine).
     */
    fun getArtworkFile(context: Context, filePath: String?): File? {
        if (filePath.isNullOrEmpty()) return null
        
        try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) return null

            val cacheDir = File(context.cacheDir, DIR_NAME).apply {
                if (!exists()) mkdirs()
            }

            // Create a unique cache filename based on the file path
            val hashName = "art_${filePath.hashCode()}.jpg"
            val cacheFile = File(cacheDir, hashName)

            // If already extracted, return it
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return cacheFile
            }

            // Extract using MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    FileOutputStream(cacheFile).use { fos ->
                        fos.write(artBytes)
                    }
                    return cacheFile
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting embedded artwork from $filePath", e)
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getArtworkFile for $filePath", e)
        }
        
        return null
    }

    /**
     * Generates a unique, beautiful gradient for songs without custom artwork.
     * Returns a list of colors (gradients) based on the song's title/artist.
     */
    fun getPlaceholderGradient(title: String, artist: String): List<Long> {
        val hash = (title + artist).hashCode()
        val palettes = listOf(
            listOf(0xFF6200EE, 0xFF03DAC6), // Violet to Teal
            listOf(0xFFFF4081, 0xFF9C27B0), // Pink to Purple
            listOf(0xFF00C853, 0xFFB2FF59), // Green to Lime
            listOf(0xFFFFAB00, 0xFFFF3D00), // Amber to Red
            listOf(0xFF00B0FF, 0xFF2979FF), // Light Blue to Blue
            listOf(0xFF795548, 0xFFFF5722), // Brown to Deep Orange
            listOf(0xFF00E5FF, 0xFF1DE9B6), // Cyan to Teal
            listOf(0xFF607D8B, 0xFFCFD8DC)  // Blue Gray to Light Gray
        )
        val index = Math.abs(hash) % palettes.size
        return palettes[index]
    }
}
