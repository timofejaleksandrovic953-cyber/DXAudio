package com.dxaudio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.PlaybackPendingIntentBuilder

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

        @OptIn(UnstableApi::class)
        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, title: String, artist: String) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)

            // Открытие приложения
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)

            // PlaybackPendingIntentBuilder — новый способ для виджетов в Media3
            val playPauseIntent = PlaybackPendingIntentBuilder(
                context, Player.COMMAND_PLAY_PAUSE, PlaybackService::class.java
            ).build()
            val nextIntent = PlaybackPendingIntentBuilder(
                context, Player.COMMAND_SEEK_TO_NEXT, PlaybackService::class.java
            ).build()
            val prevIntent = PlaybackPendingIntentBuilder(
                context, Player.COMMAND_SEEK_TO_PREVIOUS, PlaybackService::class.java
            ).build()

            views.setOnClickPendingIntent(R.id.widget_play_pause, playPauseIntent)
            views.setOnClickPendingIntent(R.id.widget_next, nextIntent)
            views.setOnClickPendingIntent(R.id.widget_prev, prevIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
