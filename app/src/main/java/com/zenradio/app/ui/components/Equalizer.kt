package com.zenradio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.zenradio.app.ui.theme.ZenColors

/**
 * Mini ecualizador con el audio real: cada barra es una banda de frecuencia
 * (graves a la izquierda, agudos a la derecha). Lee los niveles ya
 * suavizados en la fase de dibujo, así que no recompone la pantalla.
 */
@Composable
fun Equalizer(
    levels: State<FloatArray>,
    modifier: Modifier = Modifier,
    bars: Int = 4,
    color: Color = ZenColors.accent,
) {
    val top = lerp(color, Color.White, if (ZenColors.isLight) 0.15f else 0.4f)
    Canvas(modifier) {
        val values = levels.value
        val gap = size.width * 0.14f / (bars - 1).coerceAtLeast(1)
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val brush = Brush.verticalGradient(listOf(top, color))
        for (i in 0 until bars) {
            val level = if (values.isEmpty()) 0f else values[i * values.size / bars]
            val barHeight = size.height * (0.18f + 0.82f * level.coerceIn(0f, 1f))
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
