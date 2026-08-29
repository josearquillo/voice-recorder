package com.josearquillo.voicerecorder

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "voice_recorder_settings"
    private const val KEY_MAX_DURATION_MIN = "max_duration_minutes"
    private const val KEY_IS_RECORDING = "is_recording"
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
}
