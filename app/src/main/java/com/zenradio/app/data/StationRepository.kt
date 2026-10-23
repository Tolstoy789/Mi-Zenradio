package com.zenradio.app.data

import android.content.Context
import android.util.Patterns
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Fuente única de verdad para las emisoras y las preferencias del usuario.
 * La comparten la UI y RadioPlayerService (mismo proceso), así que cualquier
 * cambio (añadir, ordenar, favoritas) llega a la vez a la pantalla, a la
 * notificación y a Android Auto.
 */
class StationRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _stations = MutableStateFlow(loadStations())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _autoPlay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, false))
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    /** Tema de color elegido en Ajustes (null = el tema por defecto, Océano). */
    private val _themeId = MutableStateFlow(prefs.getString(KEY_THEME, null))
    val themeId: StateFlow<String?> = _themeId.asStateFlow()

    /** Última emisora escuchada, para retomarla al abrir la app. */
    var lastStationId: String?
        get() = prefs.getString(KEY_LAST_STATION, null)
        set(value) = prefs.edit { putString(KEY_LAST_STATION, value) }

    fun setAutoPlay(enabled: Boolean) {
        _autoPlay.value = enabled
        prefs.edit { putBoolean(KEY_AUTOPLAY, enabled) }
    }

    fun setThemeId(id: String) {
        _themeId.value = id
        prefs.edit { putString(KEY_THEME, id) }
    }

    fun findById(id: String?): Station? = _stations.value.firstOrNull { it.id == id }

    /** Añade una emisora propia. Devuelve un mensaje de error o null si todo fue bien. */
    fun addStation(name: String, url: String): String? {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        return when {
            cleanName.isEmpty() -> "Ponle un nombre a la emisora."
            !cleanUrl.startsWith("https://", ignoreCase = true) -> "La URL debe empezar por https://"
            !Patterns.WEB_URL.matcher(cleanUrl).matches() -> "La URL no parece válida."
            _stations.value.any { it.streamUrl.equals(cleanUrl, ignoreCase = true) } ->
                "Esa emisora ya está en tu lista."
            else -> {
                update {
                    it + Station(
                        id = "custom_" + UUID.randomUUID().toString().take(8),
                        name = cleanName,
                        genre = "Emisora personalizada",
                        streamUrl = cleanUrl,
                        isCustom = true,
                    )
                }
                null
            }
        }
    }

    fun removeStation(id: String) = update { list -> list.filterNot { it.id == id && it.isCustom } }

    fun toggleFavorite(id: String) = update { list ->
        list.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
    }

    /** Mueve una emisora [offset] posiciones (−1 sube, +1 baja). */
    fun move(id: String, offset: Int) = update { list ->
        val from = list.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in list.indices) return@update list
        list.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun update(transform: (List<Station>) -> List<Station>) {
        val updated = transform(_stations.value)
        if (updated == _stations.value) return
        _stations.value = updated
        saveStations(updated)
    }

    private fun loadStations(): List<Station> {
        val json = prefs.getString(KEY_STATIONS, null) ?: return StationCatalog.defaults
        return runCatching {
            val array = JSONArray(json)
            val saved = List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Station(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    genre = o.optString("genre"),
                    streamUrl = o.getString("url"),
                    isCustom = o.optBoolean("custom"),
                    isFavorite = o.optBoolean("favorite"),
                )
            }
            mergeWithCatalog(saved)
        }.getOrDefault(StationCatalog.defaults)
    }

    /**
     * Respeta el orden y las favoritas guardadas, pero toma los datos de las
     * emisoras de fábrica del catálogo actual. Así una versión nueva de la app
     * puede corregir una URL, retirar una emisora o añadir otras, que aparecen
     * al final de la lista.
     */
    private fun mergeWithCatalog(saved: List<Station>): List<Station> {
        val catalog = StationCatalog.defaults.associateBy { it.id }
        val kept = saved.mapNotNull { station ->
            if (station.isCustom) station else catalog[station.id]?.copy(isFavorite = station.isFavorite)
        }
        val keptIds = kept.mapTo(HashSet()) { it.id }
        return kept + StationCatalog.defaults.filterNot { it.id in keptIds }
    }

    private fun saveStations(list: List<Station>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("genre", it.genre)
                    .put("url", it.streamUrl)
                    .put("custom", it.isCustom)
                    .put("favorite", it.isFavorite)
            )
        }
        prefs.edit { putString(KEY_STATIONS, array.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "zenradio"
        private const val KEY_STATIONS = "stations"
        private const val KEY_LAST_STATION = "last_station"
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_THEME = "theme"

        @Volatile
        private var instance: StationRepository? = null

        fun get(context: Context): StationRepository =
            instance ?: synchronized(this) {
                instance ?: StationRepository(context.applicationContext).also { instance = it }
            }
    }
}
