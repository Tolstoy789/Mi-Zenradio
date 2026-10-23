package com.zenradio.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zenradio.app.ui.theme.ZenColors

/**
 * Superficie de cristal esmerilado: relleno translúcido en diagonal y un
 * borde fino que brilla arriba a la izquierda, como si la luz viniera de
 * ahí. Si está [highlighted] se tiñe con [tint] (el color de la emisora).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    highlighted: Boolean = false,
    tint: Color = ZenColors.accent,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = ZenColors
    val fill = if (highlighted) {
        Brush.linearGradient(listOf(tint.copy(alpha = if (palette.isLight) 0.14f else 0.20f), palette.glassBottom))
    } else {
        Brush.linearGradient(listOf(palette.glassTop, palette.glassBottom))
    }
    val edge = if (highlighted) {
        Brush.linearGradient(listOf(tint.copy(alpha = 0.8f), tint.copy(alpha = 0.3f)))
    } else {
        Brush.linearGradient(listOf(palette.glassEdgeTop, palette.glassEdgeBottom))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape),
        content = content,
    )
}

/**
 * Resplandor difuso de [color] alrededor de [shape], como el box-shadow del
 * prototipo. Ponlo antes de cualquier clip en la cadena de modificadores.
 * Usa setShadowLayer, que con aceleración por hardware funciona desde
 * Android 9; en versiones anteriores simplemente no se dibuja.
 */
fun Modifier.softGlow(
    color: Color,
    radius: Dp,
    shape: Shape = CircleShape,
    offsetY: Dp = 0.dp,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || color.alpha < 0.01f) return this
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        // Con el pincel transparente solo se ve la sombra; su alfa sale del
        // color de la sombra siempre que este no sea opaco del todo.
        val shadowColor = color.copy(alpha = color.alpha.coerceAtMost(0.99f)).toArgb()
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = android.graphics.Color.TRANSPARENT
        frameworkPaint.setShadowLayer(radius.toPx(), 0f, offsetY.toPx(), shadowColor)
        onDrawBehind {
            drawIntoCanvas { canvas -> canvas.drawPath(path, paint) }
        }
    }
}
