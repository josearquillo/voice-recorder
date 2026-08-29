package com.josearquillo.voicerecorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class RecordingWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.josearquillo.voicerecorder.WIDGET_TOGGLE"
        var isRecording = false

        fun sendUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordingWidget::class.java))
            val widget = RecordingWidget()
            ids.forEach { id ->
                widget.updateWidget(context, manager, id)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val isRec = SettingsManager.isRecording(context)
            if (isRec) {
                val stopIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_STOP
                }
                context.startService(stopIntent)
            } else {
                val startIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordingWidget::class.java))
            ids.forEach { id ->
                updateWidget(context, manager, id)
            }
        }
    }

    internal fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_recording)
        val isRec = SettingsManager.isRecording(context)

        if (isRec) {
            // Grabando: verde + icono stop
            views.setInt(R.id.widget_background, "setBackgroundColor", 0xFF22C55E.toInt())
            views.setImageViewResource(R.id.widget_icon, R.drawable.ic_stop_white)
        } else {
            // Parado: rojo + icono microfono
            views.setInt(R.id.widget_background, "setBackgroundColor", 0xFFEF4444.toInt())
            views.setImageViewResource(R.id.widget_icon, R.drawable.ic_mic_white)
        }

        val toggleIntent = Intent(context, RecordingWidget::class.java).apply {
            action = ACTION_TOGGLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }
}
