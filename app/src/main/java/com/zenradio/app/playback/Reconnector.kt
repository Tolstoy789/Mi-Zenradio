package com.zenradio.app.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Reintenta la conexión cuando el stream se corta por un problema de red,
 * con esperas crecientes (2 s, 4 s, 8 s, 16 s, 30 s, 30 s). Si vuelve la
 * conexión a Internet mientras espera, reintenta al instante.
 */
class Reconnector(
    context: Context,
    private val player: Player,
    private val onStateChanged: () -> Unit,
) : Player.Listener {

    var isReconnecting = false
        private set

    private var attempt = 0
    private val handler = Handler(Looper.getMainLooper())
    private val retry = Runnable {
        if (isReconnecting) {
            player.prepare()
            player.play()
        }
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                if (isReconnecting) {
                    handler.removeCallbacks(retry)
                    retry.run()
                }
            }
        }
    }

    init {
        player.addListener(this)
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (!error.isRecoverable() || attempt >= MAX_ATTEMPTS) {
            stop()
            return
        }
        attempt++
        setReconnecting(true)
        val delaySeconds = (1L shl attempt).coerceAtMost(30L)
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, delaySeconds * 1_000)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) stop()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // El usuario pausó: dejamos de insistir.
        if (!playWhenReady) stop()
    }

    fun release() {
        stop()
        player.removeListener(this)
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }

    private fun stop() {
        attempt = 0
        handler.removeCallbacks(retry)
        setReconnecting(false)
    }

    private fun setReconnecting(value: Boolean) {
        if (isReconnecting == value) return
        isReconnecting = value
        onStateChanged()
    }

    private fun PlaybackException.isRecoverable(): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> false
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> true
        else -> errorCode in 2000 until 3000 // familia ERROR_CODE_IO_*
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
    }
}
