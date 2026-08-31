package com.josearquillo.voicerecorder.ui

import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RenameDeleteUiTest {

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

    @Before
    fun setupFile() {
        recordingsDir.listFiles()?.forEach { it.delete() }
        File(recordingsDir, "REC_20260101_120000.m4a").writeBytes(ByteArray(2048))
        // Recrear la Activity para que cargue el archivo nuevo
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(5000)
        Thread.sleep(2000)
    }

    @Test
    fun renameDialog_opensWhenClicked() {
        composeTestRule.onNodeWithContentDescription("Renombrar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val nodes = composeTestRule.onAllNodesWithText("Renombrar grabación").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected rename dialog" }
    }

    @Test
    fun renameDialog_cancelDoesNotRename() {
        composeTestRule.onNodeWithContentDescription("Renombrar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("Cancelar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        assertTrue(File(recordingsDir, "REC_20260101_120000.m4a").exists())
    }

    @Test
    fun deleteDialog_opensWhenClicked() {
        composeTestRule.onNodeWithContentDescription("Eliminar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val nodes = composeTestRule.onAllNodesWithText("Eliminar grabación").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected delete dialog" }
    }

    @Test
    fun deleteDialog_cancelDoesNotDelete() {
        composeTestRule.onNodeWithContentDescription("Eliminar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("Cancelar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        assertTrue(File(recordingsDir, "REC_20260101_120000.m4a").exists())
    }

    @Test
    fun deleteDialog_confirmDeletesFile() {
        composeTestRule.onNodeWithContentDescription("Eliminar").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("Sí").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        assertFalse(File(recordingsDir, "REC_20260101_120000.m4a").exists())
    }
}
