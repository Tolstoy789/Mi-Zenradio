package com.zenradio.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.zenradio.app.MainActivity
import com.zenradio.app.R

/**
 * Widget de la pantalla de inicio: emisora, canción actual, play/pausa y
 * siguiente emisora. Los botones envían eventos de "botón multimedia" a
 * Media3, que arranca RadioPlayerService si hace falta.
 */
class ZenRadioWidget : AppWidgetProvider() {

    data class State(
        val stationName: String,
        val nowPlaying: String?,
        val isPlaying: Boolean,
    )

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, buildViews(context, load(context)))
    }

    companion object {
        private const val PREFS = "zenradio_widget"

        /** Guarda el estado y repinta todos los widgets colocados. */
        fun update(context: Context, state: State) {
            save(context, state)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ZenRadioWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context, state))
        }

        private fun buildViews(context: Context, state: State): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_player).apply {
                setTextViewText(R.id.widget_status, if (state.isPlaying) "EN VIVO" else "ZENRADIO")
                setTextViewText(R.id.widget_title, state.stationName)
                setTextViewText(R.id.widget_subtitle, state.nowPlaying ?: "Toca para abrir")
                setImageViewResource(
                    R.id.widget_play,
                    if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
                setOnClickPendingIntent(
                    R.id.widget_play,
                    mediaButton(
                        context,
                        if (state.isPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY,
                    ),
                )
                setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }

        @OptIn(UnstableApi::class)
        private fun mediaButton(context: Context, keyCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getBroadcast(
                context,
                keyCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun save(context: Context, state: State) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString("station", state.stationName)
                putString("now_playing", state.nowPlaying)
                putBoolean("playing", state.isPlaying)
            }

        private fun load(context: Context): State {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return State(
                stationName = prefs.getString("station", null) ?: context.getString(R.string.app_name),
                nowPlaying = prefs.getString("now_playing", null),
                // Tras reiniciar el móvil el servicio no está activo: mostramos "play".
                isPlaying = false,
            )
        }
    }
}
