package com.josearquillo.voicerecorder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun cleanup() {
        context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun maxDuration_defaultIs480() {
        assertEquals(480, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun maxDuration_setAndGet() {
        SettingsManager.setMaxDurationMinutes(context, 120)
        assertEquals(120, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun maxDuration_belowRangeCoercedTo60() {
        SettingsManager.setMaxDurationMinutes(context, 30)
        assertEquals(60, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun maxDuration_aboveRangeCoercedTo720() {
        SettingsManager.setMaxDurationMinutes(context, 1000)
        assertEquals(720, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun maxDuration_setTo60AtBoundary() {
        SettingsManager.setMaxDurationMinutes(context, 60)
        assertEquals(60, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun maxDuration_setTo720AtBoundary() {
        SettingsManager.setMaxDurationMinutes(context, 720)
        assertEquals(720, SettingsManager.getMaxDurationMinutes(context))
    }

    @Test
    fun isRecording_defaultsToFalse() {
        assertFalse(SettingsManager.isRecording(context))
    }

    @Test
    fun isRecording_setAndGet() {
        SettingsManager.setRecording(context, true)
        assertTrue(SettingsManager.isRecording(context))
        SettingsManager.setRecording(context, false)
        assertFalse(SettingsManager.isRecording(context))
    }

    @Test
    fun isActuallyRecording_falseWhenNotRecording() {
        assertFalse(SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun isActuallyRecording_trueWithFreshHeartbeat() {
        SettingsManager.setRecording(context, true)
        SettingsManager.setHeartbeat(context, System.currentTimeMillis())
        assertTrue(SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun isActuallyRecording_falseWithStaleHeartbeat() {
        SettingsManager.setRecording(context, true)
        SettingsManager.setHeartbeat(context, System.currentTimeMillis() - 5000)
        assertFalse(SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun isActuallyRecording_resetsStateWhenHeartbeatStale() {
        SettingsManager.setRecording(context, true)
        SettingsManager.setHeartbeat(context, System.currentTimeMillis() - 5000)
        SettingsManager.isActuallyRecording(context)
        assertFalse(SettingsManager.isRecording(context))
    }

    @Test
    fun isActuallyRecording_trueAt2SecondHeartbeat() {
        SettingsManager.setRecording(context, true)
        SettingsManager.setHeartbeat(context, System.currentTimeMillis() - 2000)
        assertTrue(SettingsManager.isActuallyRecording(context))
    }

    @Test
    fun isActuallyRecording_falseAt4SecondHeartbeat() {
        SettingsManager.setRecording(context, true)
        SettingsManager.setHeartbeat(context, System.currentTimeMillis() - 4000)
        assertFalse(SettingsManager.isActuallyRecording(context))
    }
}
