package com.dxaudio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackState
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver

class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, "DXAudio", "Нажмите для запуска")
        }
    }

    companion object {
        fun updateAllWidgets(context: Context, title: String, artist: String) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, title, artist)
            }
        }

        @OptIn(UnstableApi::class) // Для MediaButtonReceiver.buildMediaButtonPendingIntent
        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, title: String, artist: String) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)

            // Открытие приложения
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)

            // Кнопки управления (используем PlaybackState из Media3)
            val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackState.ACTION_PLAY_PAUSE)
            val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackState.ACTION_SKIP_TO_NEXT)
            val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackState.ACTION_SKIP_TO_PREVIOUS)

            views.setOnClickPendingIntent(R.id.widget_play_pause, playPauseIntent)
            views.setOnClickPendingIntent(R.id.widget_next, nextIntent)
            views.setOnClickPendingIntent(R.id.widget_prev, prevIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
