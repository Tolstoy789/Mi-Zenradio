package com.zenradio.app

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.zenradio.app.data.Station
import com.zenradio.app.data.StationCatalog
import com.zenradio.app.data.toMediaItem
import com.zenradio.app.playback.ZenRenderersFactory
import com.zenradio.app.playback.liveTrackTitle
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.atomic.AtomicReference

/**
 * Reproduce cada emisora del catálogo (y las candidatas que pasaron la sonda
 * de red) con el mismo reproductor y la misma cadena de audio que la app:
 * tiene que empezar a sonar en [START_TIMEOUT_MS] y seguir sonando
 * [LISTEN_MS] sin cortarse. El resultado de cada una queda en logcat con la
 * etiqueta [TAG] para el informe.
 */
@OptIn(UnstableApi::class)
@RunWith(Parameterized::class)
class StationPlaybackTest(
    private val group: String,
    @Suppress("unused") private val id: String,
    private val station: Station,
) {

    @Test
    fun suena() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val error = AtomicReference<PlaybackException?>(null)
        val nowPlaying = AtomicReference("")
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            // Igual que RadioPlayerService: misma cadena de audio y mismos reintentos.
            player = ExoPlayer.Builder(context, ZenRenderersFactory(context))
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))
                )
                .build()
            player.volume = 0f
            player.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) = error.set(e)

                override fun onMetadata(metadata: Metadata) {
                    for (i in 0 until metadata.length()) {
                        val title = (metadata.get(i) as? IcyInfo)?.title
                        if (!title.isNullOrBlank()) nowPlaying.set(title)
                    }
                }
            })
            player.setMediaItem(station.toMediaItem())
            player.prepare()
            player.play()
        }

        val started = SystemClock.elapsedRealtime()
        var playingAt = -1L
        var startPosition = 0L
        var endPosition = 0L
        var bufferedAhead = 0L
        var state = Player.STATE_IDLE
        try {
            while (error.get() == null) {
                Thread.sleep(250)
                val now = SystemClock.elapsedRealtime()
                var isPlaying = false
                var position = 0L
                instrumentation.runOnMainSync {
                    isPlaying = player.isPlaying
                    position = player.currentPosition
                    state = player.playbackState
                }
                if (playingAt < 0) {
                    if (isPlaying) {
                        playingAt = now
                        startPosition = position
                    } else if (now - started > START_TIMEOUT_MS) {
                        break
                    }
                } else if (now - playingAt >= LISTEN_MS) {
                    endPosition = position
                    instrumentation.runOnMainSync { bufferedAhead = player.totalBufferedDuration }
                    break
                }
            }

            var format = ""
            var appTitle: String? = null
            instrumentation.runOnMainSync {
                appTitle = player.liveTrackTitle()
                player.audioFormat?.let {
                    val kbps = maxOf(it.bitrate, it.averageBitrate, it.peakBitrate) / 1000
                    format = listOfNotNull(
                        it.sampleMimeType,
                        if (kbps > 0) "$kbps kbps" else null,
                        "${it.sampleRate} Hz",
                        "${it.channelCount} canales",
                    ).joinToString(" ")
                }
            }

            val failure = when {
                error.get() != null -> error.get()!!.let { "error ${it.errorCodeName}: ${it.cause?.message ?: it.message}" }
                playingAt < 0 -> "no empezó a sonar en ${START_TIMEOUT_MS / 1000} s (estado ${stateName(state)})"
                endPosition - startPosition < LISTEN_MS * 7 / 10 ->
                    "se cortó: en ${LISTEN_MS / 1000} s solo sonaron ${(endPosition - startPosition) / 1000} s " +
                        "(${bufferedAhead / 1000} s cargados sin reproducir)"
                else -> null
            }
            val startMs = if (playingAt >= 0) playingAt - started else -1
            // Canción según los metadatos ICY, y si la app la llega a mostrar (liveTrackTitle).
            val song = nowPlaying.get().trim()
            val detail = failure ?: when {
                song.isEmpty() -> "sin título de canción"
                appTitle == song -> "sonando: $song"
                else -> "sonando: $song (la app no lo muestra)"
            }
            report(if (failure == null) "OK" else "FALLA", startMs, format, detail)
            if (failure != null) fail("${station.name}: $failure")
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }

    private fun report(result: String, startMs: Long, format: String, detail: String) {
        val fields = listOf(group, station.id, station.name, result, startMs.toString(), format, detail, station.streamUrl)
        Log.i(TAG, "RESULT|" + fields.joinToString("|") { it.replace('|', '/').replace('\n', ' ') })
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "sin conectar"
        Player.STATE_BUFFERING -> "cargando"
        Player.STATE_READY -> "listo"
        else -> "terminado"
    }

    companion object {
        private const val TAG = "ZenStreamTest"
        private const val START_TIMEOUT_MS = 20_000L
        private const val LISTEN_MS = 8_000L
        private const val LOAD_RETRIES = 6 // RadioPlayerService.LOAD_RETRIES

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun emisoras(): List<Array<Any>> {
            val catalog = StationCatalog.defaults.map { arrayOf<Any>("catalogo", it.id, it) }
            val candidates = candidatas().map { arrayOf<Any>("candidata", it.id, it) }
            return catalog + candidates
        }

        /** Candidatas que la sonda de red dejó en los assets de la prueba (id|Nombre|URL). */
        private fun candidatas(): List<Station> {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            val text = runCatching { assets.open("candidates.txt").bufferedReader().use { it.readText() } }
                .getOrDefault("")
            return text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val (id, name, url) = line.split("|", limit = 3).map { it.trim() }
                    Station(id = id, name = name, genre = "", streamUrl = url)
                }
                .toList()
        }
    }
}
