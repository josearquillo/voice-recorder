package com.josearquillo.voicerecorder.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.josearquillo.voicerecorder.MainActivity
import com.josearquillo.voicerecorder.SettingsManager
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlaybackUiTest {

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

            val file = File(recordingsDir, "REC_20260101_120000.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            Thread.sleep(2000)
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
            assertTrue("No valid recording created", file.length() > 1000)
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

    private fun setup() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(5000)
        Thread.sleep(2000)
    }

    private fun clickAndCheck(buttonDesc: String, expectedDesc: String) {
        composeTestRule.onNodeWithContentDescription(buttonDesc).performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val nodes = composeTestRule.onAllNodesWithContentDescription(expectedDesc).fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected '$expectedDesc' after clicking '$buttonDesc'" }
    }

    @Test
    fun recordingAppearsInList() {
        setup()
        val nodes = composeTestRule.onAllNodesWithText("Grabaciones (1)").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected 'Grabaciones (1)'" }
    }

    @Test
    fun playButton_exists() {
        setup()
        val nodes = composeTestRule.onAllNodesWithContentDescription("Reproducir").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected 'Reproducir' button" }
    }

    @Test
    fun play_clickStartsPlayback() {
        setup()
        clickAndCheck("Reproducir", "Detener")
    }

    @Test
    fun play_pauseAndResume() {
        setup()
        composeTestRule.onNodeWithContentDescription("Reproducir").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        composeTestRule.onNodeWithContentDescription("Pausar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val resumeNodes = composeTestRule.onAllNodesWithContentDescription("Reanudar").fetchSemanticsNodes(false)
        assert(resumeNodes.isNotEmpty()) { "Expected 'Reanudar' after pause" }

        composeTestRule.onNodeWithContentDescription("Reanudar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val pauseNodes = composeTestRule.onAllNodesWithContentDescription("Pausar").fetchSemanticsNodes(false)
        assert(pauseNodes.isNotEmpty()) { "Expected 'Pausar' after resume" }
    }

    @Test
    fun play_speedButtonExists() {
        setup()
        composeTestRule.onNodeWithContentDescription("Reproducir").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val nodes = composeTestRule.onAllNodesWithText("1.0x").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected '1.0x' speed button" }
    }

    @Test
    fun play_speedChangesOnClick() {
        setup()
        composeTestRule.onNodeWithContentDescription("Reproducir").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("1.0x").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val nodes = composeTestRule.onAllNodesWithText("1.5x").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected '1.5x' after clicking '1.0x'" }
    }

    @Test
    fun play_stopReturnsToPlayButton() {
        setup()
        composeTestRule.onNodeWithContentDescription("Reproducir").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        composeTestRule.onNodeWithContentDescription("Detener").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val nodes = composeTestRule.onAllNodesWithContentDescription("Reproducir").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected 'Reproducir' after stop" }
    }

    @Test
    fun shareButton_exists() {
        setup()
        val nodes = composeTestRule.onAllNodesWithContentDescription("Compartir").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected 'Compartir' button" }
    }
}
