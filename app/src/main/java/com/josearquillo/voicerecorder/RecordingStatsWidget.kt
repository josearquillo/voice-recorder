package com.josearquillo.voicerecorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class RecordingStatsWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_STATS = "com.josearquillo.voicerecorder.UPDATE_STATS"
        const val EXTRA_ELAPSED_SECONDS = "elapsed_seconds"
        const val EXTRA_IS_RECORDING = "is_recording"

        fun sendUpdate(context: Context, elapsedSeconds: Long, recording: Boolean) {
            val intent = Intent(context, RecordingStatsWidget::class.java).apply {
                action = ACTION_UPDATE_STATS
                putExtra(EXTRA_ELAPSED_SECONDS, elapsedSeconds)
                putExtra(EXTRA_IS_RECORDING, recording)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id, 0L, RecordingWidget.isRecording)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_STATS) {
            val elapsed = intent.getLongExtra(EXTRA_ELAPSED_SECONDS, 0L)
            val recording = intent.getBooleanExtra(EXTRA_IS_RECORDING, false)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordingStatsWidget::class.java))
            ids.forEach { id ->
                updateWidget(context, manager, id, elapsed, recording)
            }
        } else if (intent.action == RecordingWidget.ACTION_TOGGLE) {
            // Tambien permitir toggle desde el widget grande
            if (RecordingWidget.isRecording) {
                val stopIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_STOP
                }
                context.startService(stopIntent)
                RecordingWidget.isRecording = false
            } else {
                val startIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
                RecordingWidget.isRecording = true
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordingStatsWidget::class.java))
            ids.forEach { id ->
                updateWidget(context, manager, id, 0L, RecordingWidget.isRecording)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        elapsedSeconds: Long,
        recording: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_recording_stats)

        // Icono y color
        if (recording) {
            views.setInt(R.id.stats_widget_icon_bg, "setBackgroundColor", 0xFF22C55E.toInt())
            views.setImageViewResource(R.id.stats_widget_icon, R.drawable.ic_stop_white)
        } else {
            views.setInt(R.id.stats_widget_icon_bg, "setBackgroundColor", 0xFFEF4444.toInt())
            views.setImageViewResource(R.id.stats_widget_icon, R.drawable.ic_mic_white)
        }

        // Tiempo transcurrido
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        val timeText = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
        views.setTextViewText(R.id.stats_widget_time, timeText)

        // Tamano estimado: 64 kbps = 8 KB/s
        val estimatedBytes = elapsedSeconds * 8000
        val sizeText = formatSize(estimatedBytes)
        views.setTextViewText(R.id.stats_widget_size, sizeText)

        // Click para iniciar/detener
        val toggleIntent = Intent(context, RecordingStatsWidget::class.java).apply {
            action = RecordingWidget.ACTION_TOGGLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 100, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.stats_widget_root, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
