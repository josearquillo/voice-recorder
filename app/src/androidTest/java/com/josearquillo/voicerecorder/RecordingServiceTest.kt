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
class RecordingServiceTest {

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
        stopRecordingService()
        Thread.sleep(500)
        SettingsManager.setRecording(context, false)
        recordingsDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun startAndStop_createsValidFile() {
        startRecordingService()
        Thread.sleep(1500)
        assertTrue(SettingsManager.isActuallyRecording(context))

        stopRecordingService()
        Thread.sleep(1000)

        assertFalse(SettingsManager.isRecording(context))
        val files = recordingsDir.listFiles()?.toList() ?: emptyList()
        assertTrue("No file created", files.isNotEmpty())
        assertTrue("File too small", files.any { it.length() > 1000 })
    }

    @Test
    fun startRecording_heartbeatIsFresh() {
        startRecordingService()
        Thread.sleep(1000)
        assertTrue(SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun startRecording_doesNotStopPrematurely() {
        SettingsManager.setMaxDurationMinutes(context, 60)
        startRecordingService()
        Thread.sleep(3000)
        assertTrue("Recording stopped prematurely", SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun stopWithoutRecording_cleansState() {
        SettingsManager.setRecording(context, true)
        SettingsManager.setHeartbeat(context, System.currentTimeMillis() - 10000)
        stopRecordingService()
        Thread.sleep(500)
        assertFalse(SettingsManager.isRecording(context))
    }

    @Test
    fun startTwice_ignoresSecondStart() {
        startRecordingService()
        Thread.sleep(1000)
        val filesAfterFirst = recordingsDir.listFiles()?.size ?: 0

        startRecordingService()
        Thread.sleep(1000)
        val filesAfterSecond = recordingsDir.listFiles()?.size ?: 0
        assertTrue("Second start created new file", filesAfterSecond == filesAfterFirst)
    }

    @Test
    fun startRecording_fileHasCorrectName() {
        startRecordingService()
        Thread.sleep(1000)
        val files = recordingsDir.listFiles()?.toList() ?: emptyList()
        assertTrue("No file created", files.isNotEmpty())
        assertTrue("Bad name prefix", files.first().name.startsWith("REC_"))
        assertTrue("Bad name suffix", files.first().name.endsWith(".m4a"))
    }

    private fun startRecordingService() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopRecordingService() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(intent)
    }
}
