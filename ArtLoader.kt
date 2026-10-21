package com.cadence.player

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads embedded cover art straight from the audio file. This is more reliable on
 * modern Android than the deprecated MediaStore "albumart" URIs.
 */
object ArtLoader {
    private val cache = object : LruCache<Long, ImageBitmap>(48) {}
    private val missing = HashSet<Long>()

    suspend fun load(ctx: Context, song: Song): ImageBitmap? {
        cache.get(song.albumId)?.let { return it }
        if (synchronized(missing) { song.albumId in missing }) return null
        val bmp = withContext(Dispatchers.IO) {
            try {
                var b: android.graphics.Bitmap? = null
                // loadThumbnail only works for MediaStore items; files opened from other apps use the fallback.
                if (Build.VERSION.SDK_INT >= 29 && song.id > 0) {
                    b = try { ctx.contentResolver.loadThumbnail(song.uri, Size(600, 600), null) } catch (_: Exception) { null }
                }
                if (b == null) {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(ctx, song.uri)
                        b = r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } catch (_: Exception) {
                    } finally {
                        r.release()
                    }
                }
                b
            } catch (_: Exception) {
                null
            }
        }
        return if (bmp != null) {
            val img = bmp.asImageBitmap()
            cache.put(song.albumId, img)
            img
        } else {
            synchronized(missing) { missing.add(song.albumId) }
            null
        }
    }
}
