package com.cadence.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

data class AudioQuality(
    val codec: String,
    val sampleRateHz: Int,
    val bitDepth: Int?,
    val bitrateKbps: Int,
    val channels: Int,
    val lossless: Boolean
) {
    val isHiRes: Boolean
        get() = lossless && ((bitDepth ?: 16) > 16 || sampleRateHz > 48000)

    /** Badge text, in the same spirit as Apple Music's Lossless / Hi-Res Lossless. */
    val badge: String
        get() = when {
            isHiRes -> "Hi-Res Lossless"
            lossless -> "Lossless"
            else -> codec
        }

    val sampleRateLabel: String
        get() {
            val khz = sampleRateHz / 1000.0
            return if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz"
            else String.format(java.util.Locale.US, "%.1f kHz", khz)
        }

    /** e.g. "24-bit/48 kHz" */
    val detail: String
        get() = if (bitDepth != null) "$bitDepth-bit/$sampleRateLabel" else sampleRateLabel

    val summary: String
        get() = buildString {
            append(codec)
            append(" · ")
            append(detail)
            if (bitrateKbps > 0) append(" · $bitrateKbps kbps")
        }
}

object AudioQualityProbe {

    suspend fun probe(ctx: Context, uri: Uri, mimeHint: String?): AudioQuality? =
        withContext(Dispatchers.IO) {
            try {
                probeBlocking(ctx, uri, mimeHint)
            } catch (_: Exception) {
                null
            }
        }

    private fun probeBlocking(ctx: Context, uri: Uri, mimeHint: String?): AudioQuality? {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use {
            val size = it.statSize
            val flac = readFlacStreamInfo(it)
            val durationMs = flac?.durationMs?.takeIf { d -> d > 0 } ?: retrieverDurationMs(ctx, uri)
            val computedKbps =
                if (durationMs > 0 && size > 0) ((size * 8L) / durationMs).toInt() else 0 // bits/ms == kbps

            if (flac != null) {
                return AudioQuality("FLAC", flac.sampleRate, flac.bitsPerSample, computedKbps, flac.channels, true)
            }

            // Everything else: ask the platform extractor.
            val ex = MediaExtractor()
            try {
                ex.setDataSource(ctx, uri, null)
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("audio/")) continue
                    val rate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                    val ch = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                    var depth: Int? = null
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        depth = when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                            AudioFormat.ENCODING_PCM_8BIT -> 8
                            AudioFormat.ENCODING_PCM_16BIT -> 16
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
                            else -> null
                        }
                    }
                    val lossless = mime == "audio/raw" || mime == "audio/alac" || mime == "audio/x-wav" ||
                        mime == "audio/flac" || mimeHint?.contains("wav", true) == true
                    val declared = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000 else 0
                    val kbps = if (lossless) computedKbps else (if (declared > 0) declared else computedKbps)
                    val name = when (mime) {
                        "audio/mpeg" -> "MP3"
                        "audio/mp4a-latm" -> "AAC"
                        "audio/vorbis" -> "Vorbis"
                        "audio/opus" -> "Opus"
                        "audio/alac" -> "ALAC"
                        "audio/raw", "audio/x-wav" -> "WAV"
                        "audio/flac" -> "FLAC"
                        else -> mime.removePrefix("audio/").uppercase()
                    }
                    return AudioQuality(name, rate, depth ?: if (lossless) 16 else null, kbps, ch, lossless)
                }
            } finally {
                ex.release()
            }
        }
        return null
    }

    private class FlacInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val totalSamples: Long) {
        val durationMs: Long get() = if (sampleRate > 0 && totalSamples > 0) totalSamples * 1000L / sampleRate else 0L
    }

    private fun readFlacStreamInfo(pfd: android.os.ParcelFileDescriptor): FlacInfo? {
        val fis = FileInputStream(pfd.fileDescriptor)
        val channel = fis.channel
        channel.position(0)
        val head = ByteArray(10)
        if (fis.read(head) < 10) return null
        var offset = 0L
        if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
            val sz = ((head[6].toInt() and 0x7F) shl 21) or ((head[7].toInt() and 0x7F) shl 14) or
                ((head[8].toInt() and 0x7F) shl 7) or (head[9].toInt() and 0x7F)
            offset = 10L + sz
        }
        channel.position(offset)
        val b = ByteArray(26)
        var read = 0
        while (read < b.size) {
            val n = fis.read(b, read, b.size - read)
            if (n < 0) return null
            read += n
        }
        if (b[0] != 'f'.code.toByte() || b[1] != 'L'.code.toByte() || b[2] != 'a'.code.toByte() || b[3] != 'C'.code.toByte()) return null
        // b[4] block header, b[5..7] length, STREAMINFO body starts at b[8]; sample-rate fields start 10 bytes in.
        fun u(i: Int) = b[i].toInt() and 0xFF
        val sampleRate = (u(18) shl 12) or (u(19) shl 4) or (u(20) shr 4)
        val channels = ((u(20) shr 1) and 0x7) + 1
        val bps = (((u(20) and 0x1) shl 4) or (u(21) shr 4)) + 1
        val total = ((u(21).toLong() and 0xF) shl 32) or (u(22).toLong() shl 24) or
            (u(23).toLong() shl 16) or (u(24).toLong() shl 8) or u(25).toLong()
        return FlacInfo(sampleRate, channels, bps, total)
    }

    private fun retrieverDurationMs(ctx: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            r.release()
        }
    }
}
