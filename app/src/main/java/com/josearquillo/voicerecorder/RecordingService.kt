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
                if (recorder == null) {
                    // Proceso fue matado y recreado: no hay recorder activo
                    // Los archivos ya estan en getExternalFilesDir/Recordings/ (no en cacheDir)
                    // Solo limpiar estado
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

        // Guardar directamente en getExternalFilesDir/Recordings (siempre funciona)
        val recordingsDir = File(getExternalFilesDir(null), "Recordings").apply { mkdirs() }
        outputFile = File(recordingsDir, fileName)

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

        // Auto-corte + actualizacion de notificacion cada segundo
        val maxMinutes = SettingsManager.getMaxDurationMinutes(this)
        val maxMillis = maxMinutes * 60_000L
        countdownTimer = object : CountDownTimer(maxMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = (maxMillis - millisUntilFinished) / 1000
                updateNotificationTime(elapsed)
            }
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

        // Escanear archivo con MediaScanner para que aparezca en exploradores
        outputFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                android.media.MediaScannerConnection.scanFile(
                    this,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mp4"),
                    null
                )
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

    private fun updateNotificationTime(elapsedSeconds: Long) {
        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        val timeText = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
                       else String.format("%02d:%02d", m, s)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Grabando… $timeText")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_recording),
                PendingIntent.getService(
                    this, 1, Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
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
