package com.zenradio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenradio.app.ui.theme.ZenColors

/**
 * Reproductor compacto que aparece abajo cuando el orbe sale de la vista al
 * desplazarte por la lista. Tocarlo te devuelve arriba.
 */
@Composable
fun MiniPlayer(
    stationName: String,
    subtitle: String,
    isPlaying: Boolean,
    isBusy: Boolean,
    accent: Color,
    levels: State<FloatArray>,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = ZenColors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softGlow(palette.shadow, radius = 24.dp, shape = shape, offsetY = 8.dp)
            .clip(shape)
            .background(palette.miniPlayer)
            .border(1.dp, Brush.linearGradient(listOf(palette.glassEdgeTop, palette.glassEdgeBottom)), shape)
            .clickable(onClickLabel = "Volver arriba", role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Equalizer(levels = levels, color = accent, modifier = Modifier.size(width = 26.dp, height = 22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stationName,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayButton(
            isPlaying = isPlaying,
            isBusy = isBusy,
            onClick = onPlayPause,
            size = 48.dp,
            glowColor = accent,
            showGlow = false,
        )
    }
}
