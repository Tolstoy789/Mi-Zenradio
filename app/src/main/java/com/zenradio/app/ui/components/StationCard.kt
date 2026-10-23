package com.zenradio.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenradio.app.data.Station
import com.zenradio.app.data.StationCatalog
import com.zenradio.app.ui.theme.ZenColors
import com.zenradio.app.ui.theme.ZenPalette

fun Station.icon(): ImageVector = when (id) {
    "lofi" -> Icons.Rounded.Headphones
    "clasica" -> Icons.Rounded.MusicNote
    "ambiental" -> Icons.Rounded.Air
    "chill" -> Icons.Rounded.Waves
    "beats" -> Icons.Rounded.Album
    "voces" -> Icons.Rounded.Mic
    "jazz" -> Icons.Rounded.Piano
    "atmosfera" -> Icons.Rounded.Cloud
    "espacio" -> Icons.Rounded.AutoAwesome
    "meditacion" -> Icons.Rounded.SelfImprovement
    "dormir" -> Icons.Rounded.NightsStay
    else -> Icons.Rounded.Radio
}

/**
 * Color propio de cada emisora dentro de la paleta activa: las de serie lo
 * toman por su posición en el catálogo y las personalizadas reciben uno
 * estable según su id.
 */
fun ZenPalette.accentFor(station: Station?): Color {
    if (station == null) return accent
    val index = StationCatalog.defaults.indexOfFirst { it.id == station.id }
        .takeIf { it >= 0 } ?: station.id.hashCode()
    return stationAccents[index.mod(stationAccents.size)]
}

/**
 * Tarjeta de cristal para elegir emisora. La activa se tiñe y brilla con el
 * color de la emisora y muestra el ecualizador en vivo. En modo edición
 * enseña los controles para subir, bajar o borrar.
 */
@Composable
fun StationCard(
    station: Station,
    accent: Color,
    selected: Boolean,
    levels: State<FloatArray>,
    editMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = ZenColors
    val shape = RoundedCornerShape(22.dp)
    val iconShape = RoundedCornerShape(14.dp)
    val glow by animateColorAsState(
        targetValue = accent.copy(alpha = if (selected) 0.18f else 0f),
        animationSpec = tween(500),
        label = "cardGlow",
    )

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .softGlow(glow, radius = 24.dp, shape = shape, offsetY = if (palette.isLight) 8.dp else 0.dp)
            .clip(shape)
            .selectable(selected = selected, enabled = !editMode, role = Role.RadioButton, onClick = onClick),
        shape = shape,
        highlighted = selected,
        tint = accent,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(iconShape)
                    .background(accent.copy(alpha = if (selected) 0.22f else 0.10f))
                    .border(1.dp, accent.copy(alpha = 0.35f), iconShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = station.icon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = station.genre,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (editMode) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Subir",
                        tint = palette.accentText.copy(alpha = if (canMoveUp) 1f else 0.3f),
                    )
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Bajar",
                        tint = palette.accentText.copy(alpha = if (canMoveDown) 1f else 0.3f),
                    )
                }
                if (station.isCustom) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = palette.error)
                    }
                }
            } else {
                if (selected) {
                    Equalizer(
                        levels = levels,
                        color = accent,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(width = 22.dp, height = 18.dp),
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (station.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (station.isFavorite) {
                            "Quitar ${station.name} de favoritas"
                        } else {
                            "Añadir ${station.name} a favoritas"
                        },
                        tint = if (station.isFavorite) palette.accentText else palette.textSecondary,
                    )
                }
            }
        }
    }
}
