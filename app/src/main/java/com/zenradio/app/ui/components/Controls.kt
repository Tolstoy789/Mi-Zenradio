package com.zenradio.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenradio.app.ui.theme.ZenColors
import kotlin.math.min

/** Botón redondo de cristal (ajustes, emisora anterior/siguiente). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = ZenColors.textPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "iconPress")
    GlassSurface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .align(Alignment.Center)
                .size(size * 0.46f),
        )
    }
}

/**
 * Píldora de estado: punto que late mientras suena, texto ("EN VIVO",
 * "CONECTANDO"…) y, si hay temporizador, la luna con el tiempo restante.
 */
@Composable
fun StatusPill(
    text: String,
    live: Boolean,
    timerText: String?,
    modifier: Modifier = Modifier,
) {
    val palette = ZenColors
    val dotColor by animateColorAsState(if (live) palette.accent else palette.idle, label = "dot")
    val textColor by animateColorAsState(if (live) palette.live else palette.textSecondary, label = "status")
    // La onda del punto solo existe (y solo consume) mientras suena.
    val pulse = if (live) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_400, easing = LinearOutSlowInEasing)),
            label = "pulseProgress",
        )
    } else {
        null
    }

    GlassSurface(modifier = modifier.height(40.dp), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .drawBehind {
                        val progress = pulse?.value
                        if (progress != null) {
                            drawCircle(
                                color = dotColor.copy(alpha = 0.6f * (1f - progress)),
                                radius = size.minDimension / 2f + 9.dp.toPx() * progress,
                            )
                        }
                        drawCircle(dotColor)
                    }
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (timerText != null) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "Temporizador activo",
                    tint = palette.accentText,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
                    color = palette.accentText,
                )
            }
        }
    }
}

/** Chip de filtro con el estilo del prototipo (Todas / Favoritas, minutos del temporizador). */
@Composable
fun ZenChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = ZenColors
    val shape = RoundedCornerShape(50)
    val background by animateColorAsState(
        if (selected) palette.accent.copy(alpha = if (palette.isLight) 0.12f else 0.16f) else palette.chip,
        label = "chipBackground",
    )
    val border by animateColorAsState(
        if (selected) {
            if (palette.isLight) palette.accent.copy(alpha = 0.55f) else palette.accentSoft.copy(alpha = 0.7f)
        } else {
            palette.outline
        },
        label = "chipBorder",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(34.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) palette.textPrimary else palette.textSecondary,
        )
    }
}

private const val MOON_PATH = "M50.19,26.08A20,20 0,1 0,71.27 51.37A17,17 0,0 1,50.19 26.08Z"

/** Las tres barras del logo, en el mismo sistema de coordenadas que la luna. */
private val BrandBars = listOf(
    Offset(37f, 72f) to Size(8f, 14f),
    Offset(50f, 66f) to Size(8f, 20f),
    Offset(63f, 74f) to Size(8f, 12f),
)

/** Logotipo "Luna zen" (luna creciente + tres barras) con los colores del tema. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val palette = ZenColors
    val moon = remember { PathParser().parsePathString(MOON_PATH).toPath() }
    Canvas(modifier) {
        // El dibujo ocupa la caja (28,22)–(76,90) del icono original.
        val factor = min(size.width / 48f, size.height / 68f)
        val left = (size.width - 48f * factor) / 2f - 28f * factor
        val top = (size.height - 68f * factor) / 2f - 22f * factor
        withTransform({
            translate(left, top)
            scale(factor, factor, pivot = Offset.Zero)
        }) {
            drawPath(moon, palette.brandMoon)
            BrandBars.forEachIndexed { index, (topLeft, barSize) ->
                drawRoundRect(
                    color = palette.brandBars[index % palette.brandBars.size],
                    topLeft = topLeft,
                    size = barSize,
                    cornerRadius = CornerRadius(4f),
                )
            }
        }
    }
}
