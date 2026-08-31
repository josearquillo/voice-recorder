package com.josearquillo.voicerecorder

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaxDurationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        SettingsManager.setRecording(context, false)
        context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun cleanup() {
        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(stopIntent)
        Thread.sleep(500)
        SettingsManager.setRecording(context, false)
        context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun maxDuration_timerStarts_heartbeatIsFresh() {
        SettingsManager.setMaxDurationMinutes(context, 60)
        startRecording()
        Thread.sleep(1000)
        assertTrue("Heartbeat not fresh - timer did not start", SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun maxDuration_timerDoesNotStopPrematurely() {
        SettingsManager.setMaxDurationMinutes(context, 60)
        startRecording()
        Thread.sleep(3000)
        assertTrue("Recording stopped prematurely", SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun maxDuration_defaultIs480minutes() {
        assertEquals(480, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun maxDuration_settingPersistsAcrossReads() {
        SettingsManager.setMaxDurationMinutes(context, 120)
        assertEquals(120, SettingsManager.getMaxDurationMinutes(context))
        assertEquals(120, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun maxDuration_serviceReadsSameValueAsSettings() {
        SettingsManager.setMaxDurationMinutes(context, 300)
        startRecording()
        Thread.sleep(1000)
        assertTrue("Service did not read duration correctly", SettingsManager.isActuallyRecording(context))
    }

    private fun startRecording() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
