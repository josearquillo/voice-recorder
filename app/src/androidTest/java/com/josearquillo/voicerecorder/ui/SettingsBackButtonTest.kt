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
class SettingsBackButtonTest {

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

    private fun setupAndWaitForMain() {
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
    fun backButton_returnsToMainScreen() {
        setupAndWaitForMain()
        composeTestRule.onNodeWithContentDescription("Ajustes").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Duración máxima de grabación").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Volver").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Voice Recorder").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsBackButton_returnsToMainScreen() {
        setupAndWaitForMain()
        composeTestRule.onNodeWithContentDescription("Ajustes").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Duración máxima de grabación").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Volver").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Voice Recorder").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
