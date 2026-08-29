package com.josearquillo.voicerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.josearquillo.voicerecorder.START"
        const val ACTION_STOP = "com.josearquillo.voicerecorder.STOP"
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var countdownTimer: CountDownTimer? = null
    private var statsHandler: Handler? = null
    private var recordingStartTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> {
                // Si el proceso fue matado y recreado, recorder sera null
                // pero puede haber un archivo temporal en cacheDir que mover
                if (recorder == null) {
                    // Buscar archivos REC_*.m4a en cacheDir y moverlos
                    cacheDir.listFiles { file ->
                        file.name.startsWith("REC_") && file.name.endsWith(".m4a")
                    }?.forEach { tempFile ->
                        if (tempFile.length() > 0) {
                            moveToPublicStorage(tempFile)
                        }
                    }
                    // Actualizar widgets y estado
                    RecordingWidget.sendUpdate(this)
                    RecordingStatsWidget.sendUpdate(this, 0L, false)
                    SettingsManager.setRecording(this, false)
                    cleanPastSchedules()
                    stopSelf()
                } else {
                    stopRecording()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        // Si ya esta grabando, ignorar
        if (recorder != null) return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "REC_$timestamp.m4a"

        // Guardar en archivo temporal; al parar, mover a carpeta publica
        outputFile = File(cacheDir, fileName)

        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(64000)
            setOutputFile(outputFile!!.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
                return
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        SettingsManager.setRecording(this, true)

        // Actualizar ambos widgets a estado grabando
        RecordingWidget.sendUpdate(this)
        RecordingStatsWidget.sendUpdate(this, 0L, true)

        // Auto-corte: temporizador que detiene la grabacion al llegar al limite
        val maxMinutes = SettingsManager.getMaxDurationMinutes(this)
        val maxMillis = maxMinutes * 60_000L
        countdownTimer = object : CountDownTimer(maxMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stopRecording()
            }
        }.start()

        // Actualizar widget de stats cada minuto (minimo consumo de bateria)
        recordingStartTime = System.currentTimeMillis()
        statsHandler = Handler(Looper.getMainLooper())
        statsHandler?.post(object : Runnable {
            override fun run() {
                if (recorder != null) {
                    val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                    RecordingStatsWidget.sendUpdate(
                        this@RecordingService,
                        elapsed,
                        true
                    )
                    statsHandler?.postDelayed(this, 60000)
                }
            }
        })
    }

    private fun stopRecording() {
        // Cancelar timers y handler siempre, aunque recorder sea null
        countdownTimer?.cancel()
        countdownTimer = null

        statsHandler?.removeCallbacksAndMessages(null)
        statsHandler = null

        // Detener recorder si existe
        recorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            release()
        }
        recorder = null

        // Mover archivo temporal a carpeta publica (Music/Recordings)
        outputFile?.let { tempFile ->
            if (tempFile.exists() && tempFile.length() > 0) {
                moveToPublicStorage(tempFile)
            }
        }
        outputFile = null

        // Actualizar widgets a estado parado SIEMPRE
        RecordingWidget.sendUpdate(this)
        RecordingStatsWidget.sendUpdate(this, 0L, false)
        SettingsManager.setRecording(this, false)

        // Limpiar programaciones pasadas
        cleanPastSchedules()

        // Cancelar notificacion SIEMPRE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_recording), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun moveToPublicStorage(tempFile: File) {
        val fileName = tempFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: MediaStore
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings/")
                }
                val collection = android.provider.MediaStore.Audio.Media.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                val uri = contentResolver.insert(collection, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Si falla, dejar el archivo temporal
            }
        } else {
            // API < 29: copiar directamente a carpeta publica
            try {
                @Suppress("DEPRECATION")
                val recordingsDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_MUSIC
                    ), "Recordings"
                ).apply { mkdirs() }
                val destFile = File(recordingsDir, fileName)
                tempFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun cleanPastSchedules() {
        val now = System.currentTimeMillis()
        val schedules = ScheduleManager.getSchedules(this)
        val past = schedules.filter { it.endMillis <= now }
        if (past.isNotEmpty()) {
            past.forEach { s ->
                ScheduledRecordingReceiver.cancelSchedule(this, s.id)
                ScheduleManager.removeSchedule(this, s.id)
            }
        }
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        countdownTimer = null
        statsHandler?.removeCallbacksAndMessages(null)
        statsHandler = null
        recorder?.release()
        recorder = null
        super.onDestroy()
    }
}
