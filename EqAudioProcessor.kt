package com.cadence.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parametric EQ running in 64-bit floating point on 32-bit float output.
 * Accepts 16-bit PCM or float PCM (ExoPlayer converts 24/32-bit sources to float
 * when float output is enabled) and always emits float PCM, so nothing is
 * requantised to 16 bits on the way to the AudioTrack.
 */
class EqAudioProcessor : BaseAudioProcessor() {

    private class Biquad {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    }

    private var appliedVersion = -1
    private var biquads: List<Biquad> = emptyList()
    private var preampLin = 1.0
    private var active = true
    // state[channel][filter*2 + {0,1}]
    private var state: Array<DoubleArray> = emptyArray()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        appliedVersion = -1
        return AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
    }

    override fun onFlush() {
        appliedVersion = -1
        state = emptyArray()
    }

    override fun onReset() {
        biquads = emptyList()
        state = emptyArray()
        appliedVersion = -1
    }

    private fun refresh() {
        val v = EqSettings.version
        if (v == appliedVersion && state.isNotEmpty()) return
        appliedVersion = v
        val fs = inputAudioFormat.sampleRate.toDouble()
        val filters = if (EqSettings.enabled) EqSettings.activeFilters() else emptyList()
        val list = ArrayList<Biquad>()
        for (f in filters) {
            if (f.freq <= 0f || f.freq >= fs * 0.49) continue
            if (f.gainDb == 0f && f.type == FilterType.PEAK) continue
            list.add(design(f, fs))
        }
        val preampDb = if (EqSettings.enabled) EqSettings.effectivePreampDb() else 0f
        active = EqSettings.enabled && (list.isNotEmpty() || preampDb != 0f)
        preampLin = 10.0.pow(preampDb / 20.0)
        val channels = inputAudioFormat.channelCount
        // Keep existing filter state when the filter count is unchanged, otherwise reset.
        if (state.size != channels || (state.isNotEmpty() && state[0].size != list.size * 2)) {
            state = Array(channels) { DoubleArray(list.size * 2) }
        }
        biquads = list
    }

    private fun design(f: EqFilter, fs: Double): Biquad {
        val a = 10.0.pow(f.gainDb / 40.0)
        val w0 = 2.0 * PI * f.freq / fs
        val cw = cos(w0)
        val sw = sin(w0)
        val alpha = sw / (2.0 * f.q.coerceAtLeast(0.05f))
        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (f.type) {
            FilterType.PEAK -> {
                b0 = 1 + alpha * a; b1 = -2 * cw; b2 = 1 - alpha * a
                a0 = 1 + alpha / a; a1 = -2 * cw; a2 = 1 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val t = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cw + t)
                b1 = 2 * a * ((a - 1) - (a + 1) * cw)
                b2 = a * ((a + 1) - (a - 1) * cw - t)
                a0 = (a + 1) + (a - 1) * cw + t
                a1 = -2 * ((a - 1) + (a + 1) * cw)
                a2 = (a + 1) + (a - 1) * cw - t
            }
            FilterType.HIGH_SHELF -> {
                val t = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cw + t)
                b1 = -2 * a * ((a - 1) + (a + 1) * cw)
                b2 = a * ((a + 1) + (a - 1) * cw - t)
                a0 = (a + 1) - (a - 1) * cw + t
                a1 = 2 * ((a - 1) - (a + 1) * cw)
                a2 = (a + 1) - (a - 1) * cw - t
            }
        }
        return Biquad().apply {
            this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0
            this.a1 = a1 / a0; this.a2 = a2 / a0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        refresh()
        val fmt = inputAudioFormat
        val channels = fmt.channelCount
        val is16 = fmt.encoding == C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16) 2 else 4
        val frames = inputBuffer.remaining() / (bytesPerSample * channels)
        if (frames == 0) { inputBuffer.position(inputBuffer.limit()); return }
        val out = replaceOutputBuffer(frames * channels * 4)

        val bq = biquads
        val n = bq.size
        for (i in 0 until frames) {
            for (c in 0 until channels) {
                var x = if (is16) inputBuffer.short / 32768.0 else inputBuffer.float.toDouble()
                if (active) {
                    x *= preampLin
                    val st = state[c]
                    for (k in 0 until n) {
                        val f = bq[k]
                        val z1 = st[k * 2]
                        val z2 = st[k * 2 + 1]
                        val y = f.b0 * x + z1
                        st[k * 2] = f.b1 * x - f.a1 * y + z2
                        st[k * 2 + 1] = f.b2 * x - f.a2 * y
                        x = y
                    }
                }
                // Safety clamp so overs never wrap.
                if (x > 1.0) x = 1.0 else if (x < -1.0) x = -1.0
                out.putFloat(x.toFloat())
            }
        }
        out.flip()
    }
}
