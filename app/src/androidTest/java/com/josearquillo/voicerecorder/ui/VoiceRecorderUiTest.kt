package com.josearquillo.voicerecorder.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.josearquillo.voicerecorder.SettingsManager
import com.josearquillo.voicerecorder.ui.theme.VoiceRecorderTheme
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceRecorderUiTest {

    companion object {
        private val context: Context = ApplicationProvider.getApplicationContext()

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            SettingsManager.setRecording(context, false)
            context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
                .edit().clear().commit()
        }

        @AfterClass
        @JvmStatic
        fun cleanupClass() {
            context.getSharedPreferences("voice_recorder_settings", Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    @Test
    fun appLaunches_showsTitle() {
        composeTestRule.setContent {
            VoiceRecorderTheme {
                VoiceRecorderApp()
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Voice Recorder").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun emptyState_showsNoRecordings() {
        composeTestRule.setContent {
            VoiceRecorderTheme {
                VoiceRecorderApp()
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("No hay grabaciones todavía").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsButton_opensSettingsScreen() {
        composeTestRule.setContent {
            VoiceRecorderTheme {
                VoiceRecorderApp()
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Voice Recorder").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Ajustes").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Duración máxima de grabación").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsScreen_showsDefaultValue() {
        composeTestRule.setContent {
            VoiceRecorderTheme {
                VoiceRecorderApp()
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Voice Recorder").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Ajustes").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("8 horas").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsScreen_showsQualityInfo() {
        composeTestRule.setContent {
            VoiceRecorderTheme {
                VoiceRecorderApp()
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Voice Recorder").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Ajustes").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Calidad: AAC, 44.1 kHz, 64 kbps.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
