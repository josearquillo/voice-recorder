package com.josearquillo.voicerecorder.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class SortUiTest {

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
            File(recordingsDir, "REC_20260102_130000.m4a").writeBytes(ByteArray(4096))
            File(recordingsDir, "REC_20260103_140000.m4a").writeBytes(ByteArray(1024))
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

    private fun clickSortAndCheck(currentText: String, nextText: String) {
        composeTestRule.onNodeWithText(currentText).performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        Thread.sleep(500)
        val nodes = composeTestRule.onAllNodesWithText(nextText).fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected '$nextText' after clicking '$currentText'" }
    }

    @Test
    fun sort_defaultIsDate() {
        setup()
        val nodes = composeTestRule.onAllNodesWithText("↕ Fecha").fetchSemanticsNodes(false)
        assert(nodes.isNotEmpty()) { "Expected '↕ Fecha' by default" }
    }

    @Test
    fun sort_clickChangesToName() {
        setup()
        clickSortAndCheck("↕ Fecha", "↕ Nombre")
    }

    @Test
    fun sort_cycleThroughAllOrders() {
        setup()
        clickSortAndCheck("↕ Fecha", "↕ Nombre")
        clickSortAndCheck("↕ Nombre", "↕ Tamaño")
        clickSortAndCheck("↕ Tamaño", "↕ Duración")
        clickSortAndCheck("↕ Duración", "↕ Fecha")
    }
}
