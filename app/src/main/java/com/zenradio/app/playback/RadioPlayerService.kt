package com.zenradio.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zenradio.app.MainActivity
import com.zenradio.app.R
import com.zenradio.app.data.Station
import com.zenradio.app.data.StationRepository
import com.zenradio.app.data.toMediaItem
import com.zenradio.app.widget.ZenRadioWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio de reproducción en segundo plano.
 *
 * Al ser un MediaLibraryService:
 *  - Media3 lo promueve a Foreground Service mientras suena y publica la
 *    notificación de medios (también en la pantalla de bloqueo).
 *  - Android Auto puede navegar por las emisoras y reproducirlas.
 *  - Los botones multimedia y el widget pueden reanudar la radio.
 *
 * Además gestiona el temporizador de apagado, la reconexión automática,
 * el analizador de espectro y el estado del widget.
 */
@OptIn(UnstableApi::class)
class RadioPlayerService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var repository: StationRepository
    private lateinit var reconnector: Reconnector

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    // --- Temporizador de apagado -------------------------------------------------
    private var sleepTimerEndsAt = 0L
    private var fadeStep = 0
    private val sleepTimerRunnable = Runnable { fadeOutStep() }

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository.get(this)

        player = ExoPlayer.Builder(this, ZenRenderersFactory(this))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        loadPlaylist()
        player.addListener(serviceListener)
        reconnector = Reconnector(this, player) { publishSessionExtras() }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openAppIntent)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )

        // Mantiene la lista de reproducción alineada con las emisoras del usuario.
        scope.launch { repository.stations.collect { syncPlaylist(it) } }

        publishSessionExtras()
        pushWidgetState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        reconnector.release()
        AudioLevels.reset()
        ZenRadioWidget.update(this, ZenRadioWidget.State(currentStationName(), null, isPlaying = false))
        session?.release()
        session = null
        player.removeListener(serviceListener)
        player.release()
        super.onDestroy()
    }

    // --- Lista de reproducción ---------------------------------------------------

    /** Carga todas las emisoras y se sitúa en la última escuchada (sin conectar aún). */
    private fun loadPlaylist() {
        val stations = repository.stations.value
        if (stations.isEmpty()) return
        val start = stations.indexOfFirst { it.id == repository.lastStationId }.coerceAtLeast(0)
        player.setMediaItems(stations.map { it.toMediaItem() }, start, C.TIME_UNSET)
    }

    /** Aplica altas, bajas y cambios de orden sin cortar la emisora que suena. */
    private fun syncPlaylist(stations: List<Station>) {
        val ids = stations.map { it.id }
        for (i in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(i).mediaId !in ids) player.removeMediaItem(i)
        }
        stations.forEachIndexed { target, station ->
            val current = (0 until player.mediaItemCount)
                .firstOrNull { player.getMediaItemAt(it).mediaId == station.id }
            when {
                current == null -> player.addMediaItem(target, station.toMediaItem())
                current != target -> player.moveMediaItem(current, target)
            }
        }
    }

    private val serviceListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.let { repository.lastStationId = it }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) AudioLevels.reset()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                pushWidgetState()
            }
        }
    }

    // --- Temporizador de apagado -------------------------------------------------

    private fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepTimerRunnable)
        player.volume = 1f
        fadeStep = 0
        sleepTimerEndsAt = if (minutes > 0) {
            val delayMs = minutes * 60_000L - FADE_DURATION_MS
            handler.postDelayed(sleepTimerRunnable, delayMs.coerceAtLeast(0))
            System.currentTimeMillis() + minutes * 60_000L
        } else {
            0L
        }
        publishSessionExtras()
    }

    /** Baja el volumen poco a poco durante los últimos segundos y pausa. */
    private fun fadeOutStep() {
        if (fadeStep >= FADE_STEPS) {
            player.pause()
            player.volume = 1f
            fadeStep = 0
            sleepTimerEndsAt = 0L
            publishSessionExtras()
            return
        }
        fadeStep++
        player.volume = 1f - fadeStep.toFloat() / FADE_STEPS
        handler.postDelayed(sleepTimerRunnable, FADE_DURATION_MS / FADE_STEPS)
    }

    // --- Estado compartido con la UI y el widget ---------------------------------

    private fun publishSessionExtras() {
        session?.setSessionExtras(
            Bundle().apply {
                putLong(RadioCommands.KEY_SLEEP_TIMER_END, sleepTimerEndsAt)
                putBoolean(RadioCommands.KEY_RECONNECTING, reconnector.isReconnecting)
            }
        )
    }

    private fun currentStationName(): String =
        player.currentMediaItem?.mediaMetadata?.title?.toString() ?: getString(R.string.app_name)

    private fun pushWidgetState() {
        ZenRadioWidget.update(
            this,
            ZenRadioWidget.State(
                stationName = currentStationName(),
                nowPlaying = player.liveTrackTitle(),
                isPlaying = player.isPlaying,
            ),
        )
    }

    // --- Sesión: comandos propios, Android Auto y reanudación --------------------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val commands = base.availableSessionCommands.buildUpon()
                .add(SessionCommand(RadioCommands.SET_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(commands, base.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == RadioCommands.SET_SLEEP_TIMER) {
                setSleepTimer(args.getInt(RadioCommands.EXTRA_MINUTES, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = if (parentId == ROOT_ID) {
                // En Android Auto mostramos primero las favoritas.
                repository.stations.value
                    .sortedByDescending { it.isFavorite }
                    .map { it.toMediaItem() }
            } else {
                emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val station = repository.findById(mediaId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(station.toMediaItem(), null))
        }

        /** Los controladores externos solo envían el mediaId: lo convertimos en la URL real. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { repository.findById(it.mediaId)?.toMediaItem() }
            return Futures.immediateFuture(resolved.toMutableList())
        }

        /** Si Android Auto pide una emisora, cargamos toda la lista para que funcione "siguiente". */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val requestedId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId
            return Futures.immediateFuture(fullPlaylistStartingAt(requestedId))
        }

        /** Reanudar desde auriculares, widget o el panel de medios del sistema. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFuture(fullPlaylistStartingAt(repository.lastStationId))
    }

    private fun fullPlaylistStartingAt(stationId: String?): MediaSession.MediaItemsWithStartPosition {
        val stations = repository.stations.value
        val index = stations.indexOfFirst { it.id == stationId }.coerceAtLeast(0)
        return MediaSession.MediaItemsWithStartPosition(
            stations.map { it.toMediaItem() },
            index,
            C.TIME_UNSET,
        )
    }

    private fun rootItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()

    private companion object {
        const val ROOT_ID = "zenradio_root"
        const val LOAD_RETRIES = 6
        const val FADE_STEPS = 20
        const val FADE_DURATION_MS = 10_000L
    }
}
