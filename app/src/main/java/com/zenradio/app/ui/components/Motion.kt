package com.zenradio.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import com.zenradio.app.playback.AudioLevels
import kotlin.math.abs
import kotlin.math.exp

/** Niveles a cero, para cuando la radio no suena. */
val SilentLevels = FloatArray(AudioLevels.BANDS)

/**
 * Reloj para las animaciones continuas (olas, partículas, anillo del orbe).
 * Da los segundos transcurridos en un bucle de una hora; todos los periodos
 * que usamos dividen 3600 s, así que el salto del bucle no se nota.
 *
 * Si el usuario desactivó las animaciones del sistema (accesibilidad), el
 * reloj se queda quieto y el fondo pasa a ser estático.
 */
@Composable
fun rememberLoopClock(): State<Float> = produceState(0f) {
    val animationsOff = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
    if (animationsOff) return@produceState
    val start = withFrameNanos { it }
    while (true) {
        withFrameNanos { now ->
            value = ((now - start) / 1_000_000L % LOOP_MS) / 1_000f
        }
    }
}

/**
 * Suaviza los niveles del analizador (llegan unas 30 veces por segundo) al
 * ritmo de la pantalla: suben rápido con cada golpe y bajan despacio, como
 * un vúmetro analógico. Se leen en la fase de dibujo, sin recomponer.
 */
@Composable
fun rememberSmoothedLevels(source: () -> FloatArray): State<FloatArray> {
    val currentSource by rememberUpdatedState(source)
    return produceState(SilentLevels) {
        var smoothed = SilentLevels
        var lastFrame = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrame = now
                val target = currentSource()
                val next = FloatArray(AudioLevels.BANDS) { i ->
                    val current = smoothed[i]
                    val goal = target.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                    val rate = if (goal > current) ATTACK_PER_SECOND else RELEASE_PER_SECOND
                    val stepped = current + (goal - current) * (1f - exp(-rate * dt))
                    if (abs(goal - stepped) < 0.002f) goal else stepped
                }
                if (!next.contentEquals(smoothed)) {
                    smoothed = next
                    value = next
                }
            }
        }
    }
}

private const val LOOP_MS = 3_600_000L
private const val ATTACK_PER_SECOND = 18f
private const val RELEASE_PER_SECOND = 4.5f
