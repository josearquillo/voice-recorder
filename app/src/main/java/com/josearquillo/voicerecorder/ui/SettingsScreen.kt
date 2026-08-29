package com.josearquillo.voicerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.josearquillo.voicerecorder.SettingsManager
import com.josearquillo.voicerecorder.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var maxMinutes by remember {
        mutableStateOf(SettingsManager.getMaxDurationMinutes(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        // Header con boton volver
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Ajustes",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(32.dp))

        // Seccion: auto-corte
        Text(
            "Grabación",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(16.dp))

        Surface(
            color = Surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Duración máxima de grabación",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La grabación se detendrá automáticamente al llegar a este tiempo.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(20.dp))

                // Slider de horas (1-12)
                val maxHours = maxMinutes / 60
                Text(
                    "${maxHours} horas",
                    color = Accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Slider(
                    value = maxHours.toFloat(),
                    onValueChange = { newHours ->
                        val newMinutes = (newHours.toInt() * 60).coerceAtLeast(1)
                        maxMinutes = newMinutes
                        SettingsManager.setMaxDurationMinutes(context, newMinutes)
                    },
                    valueRange = 1f..12f,
                    steps = 10, // 1h, 2h, ... 12h
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Surface
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1h", color = TextSecondary, fontSize = 12.sp)
                    Text("12h", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Info adicional
        Surface(
            color = Surface.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Valor por defecto: 8 horas (rango 1-12).\n" +
                "Calidad: AAC, 44.1 kHz, 64 kbps.\n" +
                "Formato: M4A.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
