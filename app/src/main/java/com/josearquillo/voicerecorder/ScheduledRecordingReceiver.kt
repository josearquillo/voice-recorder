package com.josearquillo.voicerecorder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class ScheduledRecordingReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "com.josearquillo.voicerecorder.SCHEDULED_START"
        const val ACTION_STOP = "com.josearquillo.voicerecorder.SCHEDULED_STOP"
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        fun scheduleAll(context: Context) {
            val schedules = ScheduleManager.getActiveSchedules(context)
            val now = System.currentTimeMillis()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            for (s in schedules) {
                // Si la programacion ya termino (apagado durante ella), eliminarla
                if (s.endMillis <= now) {
                    ScheduleManager.removeSchedule(context, s.id)
                    continue
                }
                // Alarma de inicio
                if (s.startMillis > now) {
                    scheduleAlarm(context, alarmManager, s.id, s.startMillis, ACTION_START)
                }
                // Alarma de fin
                if (s.endMillis > now) {
                    scheduleAlarm(context, alarmManager, s.id, s.endMillis, ACTION_STOP)
                }
            }
        }

        fun cancelAll(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Cancelar alarmas de todas las programaciones conocidas
            val schedules = ScheduleManager.getSchedules(context)
            for (s in schedules) {
                cancelAlarm(context, alarmManager, s.id, ACTION_START)
                cancelAlarm(context, alarmManager, s.id, ACTION_STOP)
            }
        }

        fun cancelSchedule(context: Context, scheduleId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelAlarm(context, alarmManager, scheduleId, ACTION_START)
            cancelAlarm(context, alarmManager, scheduleId, ACTION_STOP)
        }

        private fun cancelAlarm(
            context: Context,
            alarmManager: AlarmManager,
            scheduleId: String,
            action: String
        ) {
            val intent = Intent(context, ScheduledRecordingReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            }
            val requestCode = (scheduleId.hashCode() and 0x7FFFFFFF) +
                if (action == ACTION_START) 0 else 10000
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        private fun scheduleAlarm(
            context: Context,
            alarmManager: AlarmManager,
            scheduleId: String,
            triggerAtMillis: Long,
            action: String
        ) {
            val intent = Intent(context, ScheduledRecordingReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            }
            val requestCode = (scheduleId.hashCode() and 0x7FFFFFFF) +
                if (action == ACTION_START) 0 else 10000
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                Log.e("ScheduledReceiver", "No se puede programar alarma exacta", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        }

        private fun startRecordingService(context: Context) {
            // Si ya esta grabando, no iniciar otra
            if (SettingsManager.isRecording(context)) return
            SettingsManager.setScheduledRecording(context, true)
            val intent = Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun stopRecordingService(context: Context) {
            // Solo parar si la grabacion en curso fue iniciada por una programacion
            // (no detener grabaciones manuales del usuario)
            if (!SettingsManager.isScheduledRecording(context)) {
                Log.d("ScheduledReceiver", "No se detiene: grabacion manual en curso")
                return
            }
            val intent = Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                Log.d("ScheduledReceiver", "Iniciando grabacion programada")
                startRecordingService(context)
            }
            ACTION_STOP -> {
                Log.d("ScheduledReceiver", "Deteniendo grabacion programada")
                stopRecordingService(context)
                // Eliminar la grabacion programada ya completada
                val id = intent.getStringExtra(EXTRA_SCHEDULE_ID)
                if (id != null) {
                    ScheduleManager.removeSchedule(context, id)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("ScheduledReceiver", "Boot completado, re-programando alarmas")
                scheduleAll(context)
            }
        }
    }
}
