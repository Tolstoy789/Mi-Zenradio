package com.zenradio.app.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** Una emisora de radio en vivo (MP3/AAC por HTTPS). */
data class Station(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
)

/**
 * Catálogo inicial de ZenRadio: las tres emisoras originales y, detrás, una
 * selección que va de los beats tranquilos a los paisajes sonoros para dormir.
 * Todas emiten por HTTPS. El usuario puede añadir, ordenar y marcar favoritas;
 * las que se añadan aquí en una versión nueva le aparecen al final de su lista.
 *
 * Cada cambio aquí lo comprueba el flujo "Emisoras" de GitHub Actions
 * (tools/streams): conecta a cada emisora en varias rondas y la reproduce en un
 * emulador Android. SomaFM solo sirve la dirección completa del canal
 * (ice2.somafm.com/<canal>-128-mp3); la corta (ice.somafm.com/<canal>) da 404.
 */
object StationCatalog {
    val defaults: List<Station> = listOf(
        Station(
            id = "lofi",
            name = "Lo-Fi",
            genre = "Beats para estudiar y relajarse",
            streamUrl = "https://radio.cliamp.stream/lofi/stream",
        ),
        Station(
            id = "clasica",
            name = "Clásica",
            genre = "NPO Radio 4 · Ámsterdam",
            streamUrl = "https://icecast.omroep.nl/radio4-bb-mp3",
        ),
        Station(
            id = "ambiental",
            name = "Ambiental",
            genre = "Frisky Chill · Ambient y downtempo",
            streamUrl = "https://chill.friskyradio.com/friskychill_mp3_high",
        ),
        Station(
            id = "chill",
            name = "Chill",
            genre = "Groove Salad · Chillout y grooves",
            streamUrl = "https://ice2.somafm.com/groovesalad-128-mp3",
        ),
        Station(
            id = "beats",
            name = "Beats suaves",
            genre = "Fluid · Hip hop instrumental",
            streamUrl = "https://ice2.somafm.com/fluid-128-mp3",
        ),
        Station(
            id = "voces",
            name = "Voces suaves",
            genre = "Lush · Electrónica con voz",
            streamUrl = "https://ice2.somafm.com/lush-128-mp3",
        ),
        Station(
            id = "jazz",
            name = "Jazz",
            genre = "Jazz24 · Jazz clásico",
            streamUrl = "https://knkx-live-a.edge.audiocdn.com/6285_256k",
        ),
        Station(
            id = "atmosfera",
            name = "Atmósfera",
            genre = "Synphaera · Ambient electrónico",
            streamUrl = "https://ice2.somafm.com/synphaera-128-mp3",
        ),
        Station(
            id = "espacio",
            name = "Espacio profundo",
            genre = "Deep Space One · Ambient espacial",
            streamUrl = "https://ice2.somafm.com/deepspaceone-128-mp3",
        ),
        Station(
            id = "meditacion",
            name = "Meditación",
            genre = "Drone Zone · Texturas sin ritmo",
            streamUrl = "https://ice2.somafm.com/dronezone-128-mp3",
        ),
        Station(
            id = "dormir",
            name = "Dormir",
            genre = "Ambient Sleeping Pill · Sin ritmo",
            streamUrl = "https://radio.stereoscenic.com/asp-s",
        ),
    )
}

/** Convierte la emisora en un MediaItem con metadatos para la notificación y Android Auto. */
fun Station.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(streamUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(genre)
                .setStation(name)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()
        )
        .build()
