package com.zenradio.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zenradio.app.ui.theme.ZenColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val RING_BARS = 64
private const val TWO_PI_F = (2 * PI).toFloat()

/**
 * Corazón de la pantalla: un disco de cristal que respira (ciclo de 10 s,
 * pensado para acompasar la respiración), un halo del color de la emisora y
 * un anillo de 64 barras que dibuja el espectro real del audio: graves arriba,
 * agudos abajo, reflejado a ambos lados. En pausa el anillo ondula despacio.
 */
@Composable
fun HeroOrb(
    isPlaying: Boolean,
    isBusy: Boolean,
    accent: Color,
    levels: State<FloatArray>,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = ZenColors
    val clock = rememberLoopClock()
    val activity = animateFloatAsState(if (isPlaying) 1f else 0f, tween(900), label = "orbActivity")
    val tint = animateColorAsState(accent, tween(1_200), label = "orbAccent")
    val discFill = remember(palette) {
        Brush.linearGradient(
            listOf(
                palette.glassTop.copy(alpha = (palette.glassTop.alpha * 1.4f).coerceAtMost(0.92f)),
                palette.glassBottom,
            )
        )
    }
    val discEdge = remember(palette) { Brush.linearGradient(listOf(palette.glassEdgeTop, palette.glassEdgeBottom)) }

    Box(modifier.size(272.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val t = clock.value
            val energy = activity.value
            val color = tint.value
            val bands = levels.value
            val breath = 0.5f - 0.5f * cos(TWO_PI_F * t / 10f) // 0 → 1 → 0 cada 10 s

            // Halo: sobresale un poco del orbe y respira con el disco.
            val haloRadius = 144.dp.toPx() * (0.94f + 0.12f * breath)
            val bass = if (bands.size >= 2) (bands[0] + bands[1]) / 2f else 0f
            val haloAlpha = ((0.14f + 0.20f * energy) * (0.75f + 0.25f * breath) + 0.12f * bass * energy) *
                palette.glowStrength
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = haloAlpha.coerceIn(0f, 1f)), color.copy(alpha = 0f)),
                    center = center,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = center,
            )

            // Anillo de barras: crecen hacia fuera desde un radio de 102 dp.
            val inner = 102.dp.toPx()
            val maxLength = 24.dp.toPx()
            val ringBrush = Brush.radialGradient(
                inner / (inner + maxLength) to color.copy(alpha = 0.22f),
                1f to color.copy(alpha = 0.95f),
                center = center,
                radius = inner + maxLength,
            )
            val spin = TWO_PI_F * t / 120f // una vuelta cada dos minutos
            for (i in 0 until RING_BARS) {
                val position = i.toFloat() / RING_BARS
                // 0 arriba → 1 abajo, igual por los dos lados.
                val mirrored = 1f - abs(2f * position - 1f)
                val level = spectrumAt(bands, mirrored)
                val shimmer = 0.78f + 0.22f * sin(TWO_PI_F * t / 1.5f + i * 1.7f)
                val playing = 0.16f + 0.84f * (level * shimmer).coerceIn(0f, 1f)
                val idle = 0.12f + 0.08f * (0.5f + 0.5f * sin(TWO_PI_F * (t / 6f - position * 3f)))
                val fraction = idle + (playing - idle) * energy

                val angle = TWO_PI_F * position + spin
                val dx = sin(angle)
                val dy = -cos(angle)
                val outer = inner + maxLength * fraction
                drawLine(
                    brush = ringBrush,
                    start = Offset(center.x + dx * inner, center.y + dy * inner),
                    end = Offset(center.x + dx * outer, center.y + dy * outer),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Disco de cristal que respira; el escalado va en la capa, sin recomponer.
        Box(
            modifier = Modifier
                .size(176.dp)
                .graphicsLayer {
                    val breath = 0.5f - 0.5f * cos(TWO_PI_F * clock.value / 10f)
                    val scale = 1f + 0.045f * breath
                    scaleX = scale
                    scaleY = scale
                }
                .softGlow(palette.shadow, radius = 40.dp, offsetY = 20.dp)
                .background(discFill, CircleShape)
                .border(1.dp, discEdge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PlayButton(
                isPlaying = isPlaying,
                isBusy = isBusy,
                onClick = onPlayPause,
                glowColor = tint.value,
            )
        }
    }
}

/** Interpola el espectro de 5 bandas en una posición continua 0..1 (graves → agudos). */
private fun spectrumAt(bands: FloatArray, position: Float): Float {
    if (bands.isEmpty()) return 0f
    val scaled = position.coerceIn(0f, 1f) * (bands.size - 1)
    val low = floor(scaled).toInt().coerceIn(0, bands.size - 1)
    val high = (low + 1).coerceAtMost(bands.size - 1)
    val fraction = scaled - low
    return bands[low] + (bands[high] - bands[low]) * fraction
}

/**
 * Botón principal de reproducir/pausar: degradado del acento, brillo de
 * color cuando suena, se hunde un poco al pulsarlo y gira un aro fino
 * mientras conecta.
 */
@Composable
fun PlayButton(
    isPlaying: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    glowColor: Color = ZenColors.accent,
    showGlow: Boolean = true,
) {
    val palette = ZenColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playPress",
    )
    val glowAmount by animateFloatAsState(if (isPlaying) 1f else 0f, tween(700), label = "playGlow")
    val glow = lerp(
        palette.shadow,
        glowColor.copy(alpha = if (palette.isLight) 0.40f else 0.50f),
        glowAmount,
    )
    val fill = remember(palette) {
        Brush.linearGradient(
            0f to palette.accentSoft,
            0.45f to palette.accent,
            1f to palette.accentDeep,
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (showGlow) Modifier.softGlow(glow, radius = size * 0.4f, offsetY = size * 0.12f) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
            label = "playIcon",
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pausar" else "Reproducir",
                tint = palette.onAccent,
                modifier = Modifier.size(size * 0.4f),
            )
        }
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .matchParentSize()
                    .padding(3.dp),
                color = palette.onAccent.copy(alpha = 0.55f),
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
            )
        }
    }
}
