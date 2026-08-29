package com.josearquillo.voicerecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val EXTRA_DURATION_MS = "duration_ms"
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1

        fun start(context: Context, durationMs: Long) {
            val request = androidx.work.OneTimeWorkRequestBuilder<RecordingWorker>()
                .setInputData(
                    androidx.work.workDataOf(EXTRA_DURATION_MS to durationMs)
                )
                .build()
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("scheduled_recording", androidx.work.ExistingWorkPolicy.REPLACE, request)
            Log.d("RecordingWorker", "WorkManager enqueued for ${durationMs}ms")
        }

        fun stop(context: Context) {
            androidx.work.WorkManager.getInstance(context)
                .cancelUniqueWork("scheduled_recording")
            Log.d("RecordingWorker", "WorkManager cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val durationMs = inputData.getLong(EXTRA_DURATION_MS, 60_000L)
        Log.d("RecordingWorker", "doWork started, duration=${durationMs}ms")

        // Crear canal de notificacion
        createNotificationChannel()

        // Set foreground - esto es lo que permite WorkManager hacer desde background
        val notification = createNotification("Iniciando grabación…")
        setForeground(ForegroundInfo(NOTIFICATION_ID, notification))

        // Actualizar estado
        SettingsManager.setRecording(applicationContext, true)
        SettingsManager.setScheduledRecording(applicationContext, true)
        RecordingWidget.sendUpdate(applicationContext)

        // Crear archivo
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        var fileName = "REC_$timestamp.m4a"
        val recordingsDir = File(applicationContext.getExternalFilesDir(null), "Recordings").apply { mkdirs() }
        var counter = 1
        while (File(recordingsDir, fileName).exists()) {
            fileName = "REC_${timestamp}_$counter.m4a"
            counter++
        }
        val outputFile = File(recordingsDir, fileName)
        Log.d("RecordingWorker", "Output file: ${outputFile.absolutePath}")

        var recorder: MediaRecorder? = null
        try {
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            Log.d("RecordingWorker", "MediaRecorder started OK")

            // Actualizar notificacion con tiempo
            val startTime = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - startTime < durationMs) {
                val elapsed = (SystemClock.elapsedRealtime() - startTime) / 1000
                val h = elapsed / 3600
                val m = (elapsed % 3600) / 60
                val s = elapsed % 60
                val timeText = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
                               else String.format("%02d:%02d", m, s)

                val updatedNotification = createNotification("Grabando… $timeText")
                setForeground(ForegroundInfo(NOTIFICATION_ID, updatedNotification))

                // Actualizar widget cada 60s
                if (elapsed % 60 == 0L) {
                    RecordingWidget.sendUpdate(applicationContext)
                }

                kotlinx.coroutines.delay(1000)
            }

            // Parar recorder
            recorder.stop()
            recorder.release()
            recorder = null
            Log.d("RecordingWorker", "MediaRecorder stopped OK, file size=${outputFile.length()}")

            // Escanear archivo
            if (outputFile.exists() && outputFile.length() > 1000) {
                android.media.MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("audio/mp4"),
                    null
                )
            } else if (outputFile.exists()) {
                outputFile.delete()
            }

        } catch (e: Exception) {
            Log.e("RecordingWorker", "Error: ${e.message}", e)
            try { recorder?.stop() } catch (_: Exception) {}
            recorder?.release()
            // Si el archivo es demasiado pequeño, borrarlo
            if (outputFile.exists() && outputFile.length() < 1000) {
                outputFile.delete()
            }
        } finally {
            // Limpiar estado
            SettingsManager.setRecording(applicationContext, false)
            SettingsManager.setScheduledRecording(applicationContext, false)
            RecordingWidget.sendUpdate(applicationContext)
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): android.app.Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
