package com.josearquillo.voicerecorder

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "voice_recorder_settings"
    private const val KEY_MAX_DURATION_MIN = "max_duration_minutes"
    private const val KEY_IS_RECORDING = "is_recording"
    private const val KEY_HEARTBEAT = "heartbeat_ms"
    private const val DEFAULT_MAX_DURATION_MIN = 480 // 8 horas

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMaxDurationMinutes(context: Context): Int =
        getPrefs(context).getInt(KEY_MAX_DURATION_MIN, DEFAULT_MAX_DURATION_MIN).coerceIn(60, 720)

    fun setMaxDurationMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_MAX_DURATION_MIN, minutes).apply()
    }

    fun isRecording(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IS_RECORDING, false)

    fun setRecording(context: Context, recording: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_RECORDING, recording).apply()
    }

    fun setHeartbeat(context: Context, timestampMs: Long) {
        getPrefs(context).edit().putLong(KEY_HEARTBEAT, timestampMs).apply()
    }

    fun getHeartbeat(context: Context): Long =
        getPrefs(context).getLong(KEY_HEARTBEAT, 0L)

    /**
     * Devuelve true si isRecording=true Y el heartbeat es reciente (< 3s).
     * Si isRecording=true pero el heartbeat es stale, el servicio fue matado
     * y se resetea el estado.
     */
    fun isActuallyRecording(context: Context): Boolean {
        if (!isRecording(context)) return false
        val age = System.currentTimeMillis() - getHeartbeat(context)
        if (age > 3000L) {
            setRecording(context, false)
            return false
        }
        return true
    }
}
