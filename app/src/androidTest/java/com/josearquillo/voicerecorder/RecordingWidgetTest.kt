package com.josearquillo.voicerecorder

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecordingWidgetTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val recordingsDir: File = File(context.getExternalFilesDir(null), "Recordings")

    @Before
    fun setup() {
        SettingsManager.setRecording(context, false)
        recordingsDir.mkdirs()
        recordingsDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun cleanup() {
        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(stopIntent)
        Thread.sleep(500)
        SettingsManager.setRecording(context, false)
        recordingsDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun widgetToggle_startsRecording() {
        sendToggleBroadcast()
        assertTrue("Widget did not start recording", waitForRecordingState(true, 10000))
    }

    @Test
    fun widgetToggle_stopsRecording() {
        startRecordingViaService()
        Thread.sleep(1500)
        assertTrue(SettingsManager.isActuallyRecording(context))

        sendToggleBroadcast()
        assertTrue("Widget did not stop recording", waitForRecordingState(false, 10000))
    }

    @Test
    fun widgetToggle_createsValidFile() {
        sendToggleBroadcast()
        Thread.sleep(2000)
        assertTrue(SettingsManager.isActuallyRecording(context))

        sendToggleBroadcast()
        assertTrue("Did not stop", waitForRecordingState(false, 10000))

        val files = recordingsDir.listFiles()?.toList() ?: emptyList()
        assertTrue("No file created", files.isNotEmpty())
        assertTrue("File too small", files.any { it.length() > 1000 })
    }

    @Test
    fun widgetToggle_whenNotRecording_startsNotStops() {
        assertFalse(SettingsManager.isRecording(context))
        sendToggleBroadcast()
        assertTrue("Did not start", waitForRecordingState(true, 10000))
    }

    @Test
    fun widgetToggle_staleHeartbeat_treatedAsNotRecording() {
        SettingsManager.setRecording(context, true)
        SettingsManager.setHeartbeat(context, System.currentTimeMillis() - 10000)

        sendToggleBroadcast()
        assertTrue("Did not start with stale heartbeat", waitForRecordingState(true, 10000))
    }

    private fun sendToggleBroadcast() {
        val intent = Intent(context, RecordingWidget::class.java).apply {
            action = RecordingWidget.ACTION_TOGGLE
        }
        context.sendBroadcast(intent)
    }

    private fun startRecordingViaService() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun waitForRecordingState(target: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            if (SettingsManager.isActuallyRecording(context) == target) return true
        }
        return false
    }
}
