package com.zenradio.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zenradio.app.data.StationCatalog
import com.zenradio.app.playback.RadioUiState
import com.zenradio.app.ui.components.AddStationDialog
import com.zenradio.app.ui.components.AnimatedBackground
import com.zenradio.app.ui.components.BrandMark
import com.zenradio.app.ui.components.GlassIconButton
import com.zenradio.app.ui.components.GlassSurface
import com.zenradio.app.ui.components.HeroOrb
import com.zenradio.app.ui.components.MiniPlayer
import com.zenradio.app.ui.components.SettingsSheet
import com.zenradio.app.ui.components.SilentLevels
import com.zenradio.app.ui.components.StationCard
import com.zenradio.app.ui.components.StatusPill
import com.zenradio.app.ui.components.ZenChip
import com.zenradio.app.ui.components.accentFor
import com.zenradio.app.ui.components.rememberSmoothedLevels
import com.zenradio.app.ui.theme.ZenColors
import com.zenradio.app.ui.theme.ZenRadioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Acciones de la pantalla, agrupadas para no pasar diez lambdas sueltas. */
interface RadioActions {
    fun selectStation(id: String)
    fun skipStation(offset: Int)
    fun togglePlayPause()
    fun toggleFavorite(id: String)
    fun moveStation(id: String, offset: Int)
    fun removeStation(id: String)
    fun addStation(name: String, url: String): String?
    fun setSleepTimer(minutes: Int)
    fun setAutoPlay(enabled: Boolean)
    fun setTheme(id: String)
}

/**
 * Pantalla principal: cabecera con el logo, el orbe que respira con la
 * emisora actual y, debajo, la lista de emisoras. Al bajar por la lista
 * aparece un mini reproductor.
 *
 * [levels] se pasa como función para leer el audio (30 veces por segundo)
 * solo en la fase de dibujo, sin recomponer toda la pantalla.
 */
@Composable
fun ZenRadioScreen(
    state: RadioUiState,
    levels: () -> FloatArray,
    actions: RadioActions,
) {
    val palette = ZenColors
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    val sleepTimerText = rememberCountdown(state.sleepTimerEndsAt)

    val station = state.currentStation
    val accent = palette.accentFor(station)
    val isBusy = state.isBuffering || state.isReconnecting
    val smoothLevels = rememberSmoothedLevels { if (state.isPlaying) levels() else SilentLevels }
    val subtitle = state.nowPlaying ?: station?.genre.orEmpty()

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val heroOutOfView by remember(density) {
        derivedStateOf { scrollState.value > with(density) { 380.dp.toPx() } }
    }
    val visibleStations = if (onlyFavorites) state.stations.filter { it.isFavorite } else state.stations

    Box(Modifier.fillMaxSize()) {
        AnimatedBackground(isPlaying = state.isPlaying, stationAccent = accent, levels = smoothLevels)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Header(onOpenSettings = { showSettings = true })

            // --- Ahora suena ------------------------------------------------------
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HeroOrb(
                    isPlaying = state.isPlaying,
                    isBusy = isBusy,
                    accent = accent,
                    levels = smoothLevels,
                    onPlayPause = actions::togglePlayPause,
                )
                AnimatedContent(
                    targetState = station?.name ?: "ZenRadio",
                    transitionSpec = {
                        (fadeIn(tween(450)) + slideInVertically { it / 3 }) togetherWith
                            (fadeOut(tween(200)) + slideOutVertically { -it / 3 })
                    },
                    label = "stationName",
                ) { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.displaySmall,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = "Emisora anterior",
                        onClick = { actions.skipStation(-1) },
                    )
                    StatusPill(
                        text = when {
                            state.isReconnecting -> "RECONECTANDO"
                            state.isBuffering -> "CONECTANDO"
                            state.isPlaying -> "EN VIVO"
                            else -> "EN PAUSA"
                        },
                        live = state.isPlaying,
                        timerText = sleepTimerText,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "Emisora siguiente",
                        onClick = { actions.skipStation(1) },
                    )
                }
            }

            // --- Emisoras -------------------------------------------------------
            Row(
                modifier = Modifier.padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "EMISORAS",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                ZenChip(label = "Todas", selected = !onlyFavorites, onClick = { onlyFavorites = false })
                ZenChip(label = "Favoritas", selected = onlyFavorites, onClick = { onlyFavorites = true })
                IconButton(onClick = { editMode = !editMode }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                        contentDescription = if (editMode) "Terminar de editar" else "Ordenar y borrar emisoras",
                        tint = palette.accentText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (visibleStations.isEmpty()) {
                Text(
                    "Aún no tienes favoritas. Toca la estrella de una emisora para guardarla aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                )
            }

            visibleStations.forEach { item ->
                key(item.id) {
                    val position = state.stations.indexOf(item)
                    StationCard(
                        station = item,
                        accent = palette.accentFor(item),
                        selected = item.id == station?.id,
                        levels = smoothLevels,
                        editMode = editMode,
                        canMoveUp = position > 0,
                        canMoveDown = position < state.stations.lastIndex,
                        onClick = { actions.selectStation(item.id) },
                        onToggleFavorite = { actions.toggleFavorite(item.id) },
                        onMove = { offset -> actions.moveStation(item.id, offset) },
                        onDelete = { actions.removeStation(item.id) },
                    )
                }
            }

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClickLabel = "Añadir emisora") { showAddDialog = true },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.accentText)
                    Spacer(Modifier.width(12.dp))
                    Text("Añadir emisora", style = MaterialTheme.typography.titleSmall, color = palette.accentText)
                }
            }

            AnimatedVisibility(visible = state.errorMessage != null && !state.isReconnecting) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Hueco para que el mini reproductor no tape la última tarjeta.
            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(104.dp)
            )
        }

        AnimatedVisibility(
            visible = heroOutOfView && station != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            MiniPlayer(
                stationName = station?.name.orEmpty(),
                subtitle = subtitle,
                isPlaying = state.isPlaying,
                isBusy = isBusy,
                accent = accent,
                levels = smoothLevels,
                onPlayPause = actions::togglePlayPause,
                onClick = { scope.launch { scrollState.animateScrollTo(0) } },
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            sleepTimerText = sleepTimerText,
            autoPlay = state.autoPlay,
            selectedThemeId = palette.id,
            onSetSleepTimer = actions::setSleepTimer,
            onAutoPlayChange = actions::setAutoPlay,
            onThemeChange = actions::setTheme,
            onDismiss = { showSettings = false },
        )
    }
    if (showAddDialog) {
        AddStationDialog(
            onAdd = actions::addStation,
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val palette = ZenColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(Modifier.size(width = 26.dp, height = 36.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "ZenRadio",
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
        )
        GlassIconButton(
            icon = Icons.Rounded.Tune,
            contentDescription = "Temporizador, tema y ajustes",
            onClick = onOpenSettings,
            tint = palette.accentText,
        )
    }
}

/** Texto "m:ss" que se actualiza cada segundo hasta [endsAt], o null si no hay temporizador. */
@Composable
private fun rememberCountdown(endsAt: Long): String? {
    val now by produceState(System.currentTimeMillis(), endsAt) {
        while (endsAt > 0) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (endsAt <= 0) return null
    val remaining = ((endsAt - now) / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(remaining / 60, remaining % 60)
}

@Preview(showBackground = true, backgroundColor = 0xFF071829, widthDp = 390, heightDp = 844)
@Composable
private fun ZenRadioScreenPreview() {
    ZenRadioTheme {
        ZenRadioScreen(
            state = RadioUiState(
                stations = StationCatalog.defaults,
                isPlaying = true,
                nowPlaying = "Artista – Canción tranquila",
            ),
            levels = { floatArrayOf(0.7f, 0.5f, 0.8f, 0.4f, 0.3f) },
            actions = object : RadioActions {
                override fun selectStation(id: String) {}
                override fun skipStation(offset: Int) {}
                override fun togglePlayPause() {}
                override fun toggleFavorite(id: String) {}
                override fun moveStation(id: String, offset: Int) {}
                override fun removeStation(id: String) {}
                override fun addStation(name: String, url: String): String? = null
                override fun setSleepTimer(minutes: Int) {}
                override fun setAutoPlay(enabled: Boolean) {}
                override fun setTheme(id: String) {}
            },
        )
    }
}
