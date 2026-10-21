package com.cadence.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val mime: String?,
    val uri: Uri
) {
    val artUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

data class Album(val id: Long, val name: String, val artist: String, val songs: List<Song>) {
    val artUri: Uri get() = songs.first().artUri
}

data class Artist(val name: String, val songs: List<Song>)

object Library {
    suspend fun load(ctx: Context): List<Song> = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        ctx.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            while (c.moveToNext()) {
                val sid = c.getLong(id)
                out.add(
                    Song(
                        id = sid,
                        title = c.getString(title) ?: "Unknown",
                        artist = c.getString(artist)?.takeIf { it != "<unknown>" } ?: "Unknown Artist",
                        album = c.getString(album) ?: "Unknown Album",
                        albumId = c.getLong(albumId),
                        durationMs = c.getLong(dur),
                        mime = c.getString(mime),
                        uri = ContentUris.withAppendedId(collection, sid)
                    )
                )
            }
        }
        out
    }

    /** Builds a Song for a file opened from outside the library (e.g. "Open with" in a file manager). */
    suspend fun fromUri(ctx: Context, uri: Uri): Song = withContext(Dispatchers.IO) {
        var fileName: String? = null
        try {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) fileName = it.getString(0)
            }
        } catch (_: Exception) { }
        if (fileName == null) fileName = uri.lastPathSegment?.substringAfterLast('/')

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration = 0L
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            r.release()
        }

        // Negative id so it can never collide with a MediaStore id.
        val id = -((uri.toString().hashCode().toLong() and 0x7FFFFFFFL) + 1L)
        Song(
            id = id,
            title = title?.takeIf { it.isNotBlank() } ?: fileName?.substringBeforeLast('.') ?: "Unknown",
            artist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            album = album?.takeIf { it.isNotBlank() } ?: "Unknown Album",
            albumId = id,
            durationMs = duration,
            mime = ctx.contentResolver.getType(uri),
            uri = uri
        )
    }

    fun albums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (id, list) -> Album(id, list.first().album, list.first().artist, list.sortedBy { it.title }) }
            .sortedBy { it.name.lowercase() }

    fun artists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }
            .map { (name, list) -> Artist(name, list) }
            .sortedBy { it.name.lowercase() }
}
