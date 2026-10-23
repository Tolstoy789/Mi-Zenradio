package com.zenradio.app.playback

import androidx.media3.common.Player

/** Comandos y claves compartidos entre la UI y RadioPlayerService. */
object RadioCommands {
    const val SET_SLEEP_TIMER = "com.zenradio.app.SET_SLEEP_TIMER"
    const val EXTRA_MINUTES = "minutes"

    /** Extras de sesión que publica el servicio. */
    const val KEY_SLEEP_TIMER_END = "sleep_timer_end"   // epoch millis, 0 = sin temporizador
    const val KEY_RECONNECTING = "reconnecting"
}

/**
 * Título de la canción que suena ahora (metadatos ICY del stream).
 * Devuelve null si la emisora no lo envía o solo repite el nombre de la emisora.
 */
fun Player.liveTrackTitle(): String? {
    val stationName = currentMediaItem?.mediaMetadata?.title?.toString()
    val live = mediaMetadata.title?.toString()?.trim() ?: return null
    if (live.trim('-', ' ').isEmpty() || live == stationName) return null
    return live
}
