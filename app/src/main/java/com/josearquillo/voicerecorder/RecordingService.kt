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
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val recordingsDir = File(getExternalFilesDir(null), "Recordings").apply { mkdirs() }
        outputFile = File(recordingsDir, "REC_$timestamp.m4a")

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

        // Auto-corte: temporizador que detiene la grabacion al llegar al limite
        val maxMinutes = SettingsManager.getMaxDurationMinutes(this)
        val maxMillis = maxMinutes * 60_000L
        countdownTimer = object : CountDownTimer(maxMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                stopRecording()
            }
        }.start()

        // Actualizar widget de stats cada 3 segundos (reduce consumo de bateria)
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
                    statsHandler?.postDelayed(this, 3000)
                }
            }
        })
    }

    private fun stopRecording() {
        countdownTimer?.cancel()
        countdownTimer = null

        statsHandler?.removeCallbacksAndMessages(null)
        statsHandler = null

        recorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            release()
        }
        recorder = null

        // Actualizar widget grande a estado parado
        RecordingStatsWidget.sendUpdate(this, 0L, false)

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

    override fun onBind(intent: Intent?): IBinder? = null

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
