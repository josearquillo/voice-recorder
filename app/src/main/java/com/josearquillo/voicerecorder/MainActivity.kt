package com.josearquillo.voicerecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.josearquillo.voicerecorder.ui.VoiceRecorderApp
import com.josearquillo.voicerecorder.ui.theme.VoiceRecorderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceRecorderTheme {
                VoiceRecorderApp()
            }
        }
    }
}
