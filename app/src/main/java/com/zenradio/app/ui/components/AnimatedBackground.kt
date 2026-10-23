package com.zenradio.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.zenradio.app.ui.theme.ZenColors
import com.zenradio.app.ui.theme.ZenPalette
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondo "océano nocturno": degradado profundo, tres focos de luz que derivan
 * muy despacio (uno con el color de la emisora), olas suaves en la parte de
 * abajo, partículas de luz que suben y un grano fino de película.
 *
 * Con la radio sonando las olas crecen y los graves las hacen respirar. Todo
 * se dibuja en la fase de dibujo con un reloj propio, así que no provoca
 * recomposiciones mientras se anima.
 */
@Composable
fun AnimatedBackground(
    isPlaying: Boolean,
    stationAccent: Color,
    levels: State<FloatArray>,
    modifier: Modifier = Modifier,
) {
    val palette = ZenColors
    val clock = rememberLoopClock()
    val accent = animateColorAsState(stationAccent, tween(1_200), label = "fieldAccent")
    val activity = animateFloatAsState(if (isPlaying) 1f else 0f, tween(1_400), label = "activity")
    val particles = remember { buildParticles() }
    val wavePaths = remember { List(4) { Path() } }
    val grain = remember { ShaderBrush(ImageShader(noiseTexture(), TileMode.Repeated, TileMode.Repeated)) }
    val baseGradient = remember(palette) {
        Brush.verticalGradient(
            0f to palette.backgroundTop,
            0.48f to palette.backgroundMid,
            1f to palette.backgroundBottom,
        )
    }

    Box(modifier.fillMaxSize()) {
        // Capa propia: al redibujarse cada fotograma no arrastra al resto de la pantalla.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val t = clock.value
            val energy = activity.value
            val bands = levels.value
            val bass = if (bands.size >= 2) (bands[0] + bands[1]) / 2f else 0f

            drawRect(baseGradient)
            drawLightFields(palette, accent.value, t, energy, bass)
            drawWaves(palette, wavePaths, t, energy, bass)
            drawParticles(palette, particles, t, energy)
        }
        // El grano no se anima: va en otra capa para no regenerarlo cada fotograma.
        Canvas(Modifier.fillMaxSize()) {
            val texel = (density * 0.6f).coerceAtLeast(1f)
            scale(scale = texel, pivot = Offset.Zero) {
                drawRect(
                    brush = grain,
                    size = Size(size.width / texel, size.height / texel),
                    alpha = palette.grainAlpha,
                )
            }
        }
    }
}

private const val TWO_PI = (2 * PI).toFloat()

/** Onda senoidal de periodo [periodSeconds] (debe dividir 3600 s). */
private fun wave(t: Float, periodSeconds: Float, phase: Float = 0f): Float =
    sin(TWO_PI * t / periodSeconds + phase)

private fun DrawScope.drawLightFields(
    palette: ZenPalette,
    accent: Color,
    t: Float,
    energy: Float,
    bass: Float,
) {
    val w = size.width
    val h = size.height
    val unit = min(w, h * 0.62f)

    fun field(color: Color, center: Offset, radius: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    // Arriba a la izquierda: la luz principal del tema.
    field(
        color = palette.glowPrimary,
        center = Offset(w * 0.23f + unit * 0.07f * wave(t, 40f), h * 0.08f + unit * 0.06f * wave(t, 50f, 1f)),
        radius = unit * 0.62f * (1f + 0.05f * wave(t, 45f)),
    )
    // A la derecha: el color de la emisora; brilla más cuando suena y late con los graves.
    val accentAlpha = (0.14f + 0.18f * energy + 0.10f * bass * energy) * palette.glowStrength
    field(
        color = accent.copy(alpha = accentAlpha.coerceIn(0f, 1f)),
        center = Offset(w * 0.97f - unit * 0.06f * wave(t, 36f), h * 0.45f + unit * 0.08f * wave(t, 48f, 2f)),
        radius = unit * 0.52f * (1f + 0.06f * wave(t, 30f, 1f) + 0.05f * bass * energy),
    )
    // Abajo: una luz más fría que ilumina las olas.
    field(
        color = palette.glowSecondary,
        center = Offset(w * 0.41f + unit * 0.08f * wave(t, 60f, 3f), h * 1.02f - unit * 0.04f * wave(t, 30f)),
        radius = unit * 0.66f * (1f + 0.04f * wave(t, 75f, 2f)),
    )
}

private class WaveSpec(
    val baselineDp: Float,
    val amplitudeDp: Float,
    val wavelengthDp: Float,
    val periodSeconds: Float,
    val strokeAlpha: Float,
    val strokeWidthDp: Float,
)

private val Waves = listOf(
    WaveSpec(baselineDp = 110f, amplitudeDp = 12f, wavelengthDp = 195f, periodSeconds = 8f, strokeAlpha = 0.24f, strokeWidthDp = 1.5f),
    WaveSpec(baselineDp = 84f, amplitudeDp = 9f, wavelengthDp = 260f, periodSeconds = -15f, strokeAlpha = 0.15f, strokeWidthDp = 1.2f),
    WaveSpec(baselineDp = 58f, amplitudeDp = 9f, wavelengthDp = 390f, periodSeconds = 30f, strokeAlpha = 0.10f, strokeWidthDp = 1f),
)

private fun DrawScope.drawWaves(
    palette: ZenPalette,
    paths: List<Path>,
    t: Float,
    energy: Float,
    bass: Float,
) {
    val w = size.width
    val h = size.height
    val step = 4.dp.toPx()
    // En pausa las olas se calman; sonando crecen y respiran con los graves.
    val swell = 0.55f + 0.45f * energy + 0.35f * bass * energy

    Waves.forEachIndexed { index, spec ->
        val baseline = h - spec.baselineDp.dp.toPx()
        val amplitude = spec.amplitudeDp.dp.toPx() * swell
        val k = TWO_PI / spec.wavelengthDp.dp.toPx()
        val phase = TWO_PI * t / spec.periodSeconds
        val path = paths[index]
        path.reset()
        // Bajo la primera ola, un velo de color que se funde con el fondo.
        val fill = if (index == 0) paths[3].apply { reset() } else null
        val startY = baseline + amplitude * sin(phase)
        path.moveTo(0f, startY)
        fill?.moveTo(0f, startY)
        var x = 0f
        while (x < w) {
            x = (x + step).coerceAtMost(w)
            val y = baseline + amplitude * sin(k * x + phase)
            path.lineTo(x, y)
            fill?.lineTo(x, y)
        }

        if (fill != null) {
            fill.lineTo(w, h)
            fill.lineTo(0f, h)
            fill.close()
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(palette.waveFill, palette.waveFill.copy(alpha = 0f)),
                    startY = baseline - amplitude,
                    endY = h,
                ),
            )
        }
        drawPath(
            path = path,
            color = palette.wave.copy(alpha = spec.strokeAlpha),
            style = Stroke(width = spec.strokeWidthDp.dp.toPx()),
        )
    }
}

private class Particle(
    val x: Float,
    val radiusDp: Float,
    val periodSeconds: Float,
    val offset: Float,
    val swayDp: Float,
)

private fun buildParticles(count: Int = 18): List<Particle> {
    val random = Random(7)
    // Periodos que dividen 3600 s para que el bucle del reloj no se note.
    val periods = floatArrayOf(18f, 20f, 24f, 25f, 30f, 36f)
    return List(count) { i ->
        Particle(
            x = (i + random.nextFloat()) / count,
            radiusDp = 0.8f + random.nextFloat() * 1.2f,
            periodSeconds = periods[random.nextInt(periods.size)],
            offset = random.nextFloat(),
            swayDp = 6f + random.nextFloat() * 10f,
        )
    }
}

private fun DrawScope.drawParticles(
    palette: ZenPalette,
    particles: List<Particle>,
    t: Float,
    energy: Float,
) {
    val w = size.width
    val h = size.height
    val margin = 8.dp.toPx()
    val brightness = 0.6f + 0.4f * energy
    particles.forEach { p ->
        val progress = (t / p.periodSeconds + p.offset) % 1f
        // Aparece abajo, brilla al subir y se desvanece antes de llegar arriba.
        val fade = when {
            progress < 0.12f -> progress / 0.12f * 0.75f
            progress < 0.88f -> 0.75f - (progress - 0.12f) / 0.76f * 0.35f
            else -> 0.4f * (1f - progress) / 0.12f
        }
        val alpha = (fade * brightness).coerceIn(0f, 1f)
        val center = Offset(
            x = p.x * w + p.swayDp.dp.toPx() * sin(TWO_PI * progress),
            y = h + margin - progress * (h + 2 * margin),
        )
        val radius = p.radiusDp.dp.toPx()
        // Un halo muy tenue alrededor de cada partícula le da aspecto de bokeh.
        drawCircle(palette.particle.copy(alpha = alpha * 0.14f), radius = radius * 3.2f, center = center)
        drawCircle(palette.particle.copy(alpha = alpha), radius = radius, center = center)
    }
}

/** Ruido de dos tonos (claro y oscuro) que se repite en mosaico como grano de película. */
private fun noiseTexture(sizePx: Int = 128): ImageBitmap {
    val random = Random(42)
    val pixels = IntArray(sizePx * sizePx) {
        val alpha = random.nextInt(256)
        val rgb = if (random.nextBoolean()) 0xFFFFFF else 0x000000
        (alpha shl 24) or rgb
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}
