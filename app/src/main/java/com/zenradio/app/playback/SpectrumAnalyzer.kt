package com.zenradio.app.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Niveles (0..1) por banda de frecuencia del audio que está sonando. */
object AudioLevels {
    const val BANDS = 5
    private val _levels = MutableStateFlow(FloatArray(BANDS))
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    internal fun publish(values: FloatArray) {
        _levels.value = values
    }

    fun reset() {
        _levels.value = FloatArray(BANDS)
    }
}

/**
 * Recibe una copia del PCM que ExoPlayer envía al altavoz (vía TeeAudioProcessor),
 * calcula una FFT de 512 muestras y publica la energía de 5 bandas
 * (graves → agudos) unas 30 veces por segundo. No necesita permiso de micrófono.
 */
@OptIn(UnstableApi::class)
class SpectrumAnalyzer : TeeAudioProcessor.AudioBufferSink {

    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1 - cos(2 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }
    private val bandEdgesHz = floatArrayOf(40f, 160f, 500f, 1_500f, 4_000f, 12_000f)

    private var sampleRate = 44_100
    private var channelCount = 2
    private var encoding = C.ENCODING_INVALID
    private var filled = 0
    private var lastPublish = 0L

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        filled = 0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (encoding != C.ENCODING_PCM_16BIT || channelCount <= 0) return
        val samples = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val frames = samples.remaining() / channelCount
        for (frame in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channelCount) sum += samples.get(frame * channelCount + ch)
            re[filled++] = sum / channelCount / Short.MAX_VALUE
            if (filled == FFT_SIZE) {
                filled = 0
                maybeAnalyze()
            }
        }
    }

    private fun maybeAnalyze() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPublish < PUBLISH_INTERVAL_MS) return
        lastPublish = now

        for (i in 0 until FFT_SIZE) {
            re[i] *= window[i]
            im[i] = 0f
        }
        fft(re, im)

        val binHz = sampleRate.toFloat() / FFT_SIZE
        val levels = FloatArray(AudioLevels.BANDS)
        for (band in 0 until AudioLevels.BANDS) {
            val from = (bandEdgesHz[band] / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val to = (bandEdgesHz[band + 1] / binHz).toInt().coerceIn(from + 1, FFT_SIZE / 2)
            var energy = 0f
            for (bin in from until to) {
                energy += sqrt(re[bin] * re[bin] + im[bin] * im[bin])
            }
            val magnitude = energy / (to - from) / (FFT_SIZE / 4f)
            val db = 20 * log10(magnitude + 1e-6f)
            levels[band] = ((db + 62f) / 52f).coerceIn(0f, 1f)
        }
        AudioLevels.publish(levels)
    }

    /** FFT radix-2 iterativa, in situ. */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var start = 0
            while (start < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val a = start + k
                    val b = a + len / 2
                    val tRe = real[b] * curRe - imag[b] * curIm
                    val tIm = real[b] * curIm + imag[b] * curRe
                    real[b] = real[a] - tRe
                    imag[b] = imag[a] - tIm
                    real[a] += tRe
                    imag[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += len
            }
            len = len shl 1
        }
    }

    private companion object {
        const val FFT_SIZE = 512
        const val PUBLISH_INTERVAL_MS = 33L
    }
}
