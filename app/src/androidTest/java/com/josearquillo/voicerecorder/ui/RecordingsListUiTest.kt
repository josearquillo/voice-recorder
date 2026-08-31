package com.josearquillo.voicerecorder.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.josearquillo.voicerecorder.MainActivity
import com.josearquillo.voicerecorder.SettingsManager
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecordingsListUiTest {

    companion object {
        private val context: Context = ApplicationProvider.getApplicationContext()
        private val recordingsDir: File = File(context.getExternalFilesDir(null), "Recordings")

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            SettingsManager.setRecording(context, false)
            context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
                .edit().clear().commit()
            recordingsDir.mkdirs()
            recordingsDir.listFiles()?.forEach { it.delete() }
            File(recordingsDir, "REC_20260101_120000.m4a").writeBytes(ByteArray(2048))
            File(recordingsDir, "REC_20260101_120100.m4a").writeBytes(ByteArray(2048))
            File(recordingsDir, "REC_20260101_120200.m4a").writeBytes(ByteArray(2048))
        }

        @AfterClass
        @JvmStatic
        fun cleanupClass() {
            recordingsDir.listFiles()?.forEach { it.delete() }
            context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    @Test
    fun recordingsList_showsThreeFiles() {
        composeTestRule.mainClock.autoAdvance = false
        // Avanzar el reloj manualmente para procesar composiciones iniciales
        composeTestRule.mainClock.advanceTimeBy(5000)
        // Verificar sin esperar idle
        Thread.sleep(2000)
        val nodes = composeTestRule.onAllNodesWithText("Grabaciones (3)").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected 'Grabaciones (3)' text" }
    }

    @Test
    fun recordingsList_showsFileName() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(5000)
        Thread.sleep(2000)
        val nodes = composeTestRule.onAllNodesWithText("REC_20260101_120000").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected 'REC_20260101_120000' text" }
    }
}
