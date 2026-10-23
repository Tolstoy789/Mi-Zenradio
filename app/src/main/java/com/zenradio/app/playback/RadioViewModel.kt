package com.zenradio.app.playback

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zenradio.app.data.Station
import com.zenradio.app.data.StationRepository
import com.zenradio.app.ui.RadioActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado que la UI necesita para pintarse. */
data class RadioUiState(
    val stations: List<Station> = emptyList(),
    val currentStationId: String? = null,
    val nowPlaying: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isReconnecting: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val sleepTimerEndsAt: Long = 0L,
    val autoPlay: Boolean = false,
    val themeId: String? = null,
) {
    val currentStation: Station?
        get() = stations.firstOrNull { it.id == currentStationId } ?: stations.firstOrNull()
}

/**
 * Puente entre Compose y RadioPlayerService.
 * Se conecta al servicio mediante un MediaController, así la reproducción
 * vive en el servicio y sobrevive a que la Activity se destruya.
 */
class RadioViewModel(application: Application) : AndroidViewModel(application), RadioActions {

    private val repository = StationRepository.get(application)

    private val _state = MutableStateFlow(
        RadioUiState(
            stations = repository.stations.value,
            currentStationId = repository.lastStationId,
            autoPlay = repository.autoPlay.value,
            themeId = repository.themeId.value,
        )
    )
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    /** Niveles del ecualizador calculados a partir del audio real. */
    val audioLevels: StateFlow<FloatArray> = AudioLevels.levels

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var autoPlayHandled = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(errorMessage = "No se pudo conectar con la emisora. Revisa tu conexión.")
            }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            applySessionExtras(extras)
        }
    }

    init {
        viewModelScope.launch {
            repository.stations.collect { list -> _state.update { it.copy(stations = list) } }
        }
        viewModelScope.launch {
            repository.autoPlay.collect { enabled -> _state.update { it.copy(autoPlay = enabled) } }
        }
        viewModelScope.launch {
            repository.themeId.collect { id -> _state.update { it.copy(themeId = id) } }
        }
        connectToService()
    }

    private fun connectToService() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, RadioPlayerService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val mediaController = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = mediaController
                mediaController.addListener(playerListener)
                _state.update { it.copy(isConnected = true) }
                applySessionExtras(mediaController.sessionExtras)
                syncFromPlayer(mediaController)

                // "Reproducir al iniciar": solo una vez por apertura de la app.
                if (!autoPlayHandled && repository.autoPlay.value && !mediaController.playWhenReady) {
                    startPlayback(mediaController)
                }
                autoPlayHandled = true
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    // --- Reproducción -----------------------------------------------------------

    override fun togglePlayPause() {
        val player = controller ?: return
        if (player.playWhenReady) player.pause() else startPlayback(player)
    }

    override fun selectStation(id: String) {
        val player = controller ?: return
        val index = (0 until player.mediaItemCount)
            .firstOrNull { player.getMediaItemAt(it).mediaId == id } ?: return
        _state.update { it.copy(currentStationId = id, errorMessage = null, nowPlaying = null) }
        if (player.currentMediaItemIndex != index) player.seekToDefaultPosition(index)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    /** Salta a la emisora anterior (−1) o siguiente (+1) de la lista, dando la vuelta. */
    override fun skipStation(offset: Int) {
        val current = _state.value
        val stations = current.stations
        if (stations.isEmpty()) return
        val index = stations.indexOfFirst { it.id == current.currentStation?.id }.coerceAtLeast(0)
        selectStation(stations[(index + offset).mod(stations.size)].id)
    }

    private fun startPlayback(player: Player) {
        _state.update { it.copy(errorMessage = null) }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        // Una radio en vivo no tiene "pausa" real: al reanudar volvemos al directo.
        player.seekToDefaultPosition()
        player.play()
    }

    // --- Temporizador y ajustes -------------------------------------------------

    /** 0 desactiva el temporizador. */
    override fun setSleepTimer(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand(RadioCommands.SET_SLEEP_TIMER, Bundle.EMPTY),
            bundleOf(RadioCommands.EXTRA_MINUTES to minutes),
        )
    }

    override fun setAutoPlay(enabled: Boolean) = repository.setAutoPlay(enabled)

    override fun setTheme(id: String) = repository.setThemeId(id)

    // --- Gestión de emisoras ----------------------------------------------------

    /** Devuelve un mensaje de error para mostrar en el formulario, o null si se añadió. */
    override fun addStation(name: String, url: String): String? = repository.addStation(name, url)

    override fun removeStation(id: String) = repository.removeStation(id)

    override fun toggleFavorite(id: String) = repository.toggleFavorite(id)

    override fun moveStation(id: String, offset: Int) = repository.move(id, offset)

    // --- Sincronización ---------------------------------------------------------

    private fun syncFromPlayer(player: Player) {
        _state.update {
            it.copy(
                currentStationId = player.currentMediaItem?.mediaId ?: it.currentStationId,
                nowPlaying = player.liveTrackTitle(),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady,
                errorMessage = if (player.isPlaying) null else it.errorMessage,
            )
        }
    }

    private fun applySessionExtras(extras: Bundle) {
        _state.update {
            it.copy(
                sleepTimerEndsAt = extras.getLong(RadioCommands.KEY_SLEEP_TIMER_END, 0L),
                isReconnecting = extras.getBoolean(RadioCommands.KEY_RECONNECTING, false),
            )
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
