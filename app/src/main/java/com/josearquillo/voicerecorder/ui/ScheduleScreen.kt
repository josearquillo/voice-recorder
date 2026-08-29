package com.josearquillo.voicerecorder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.josearquillo.voicerecorder.ScheduleManager
import com.josearquillo.voicerecorder.ScheduledRecording
import com.josearquillo.voicerecorder.ScheduledRecordingReceiver
import com.josearquillo.voicerecorder.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var schedules by remember { mutableStateOf(ScheduleManager.getSchedules(context)) }
    var startCal by remember { mutableStateOf<Calendar?>(null) }
    var endCal by remember { mutableStateOf<Calendar?>(null) }
    var pickerState by remember { mutableStateOf(PickerState.NONE) }

    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    // Refrescar programaciones al entrar (pueden haber cambiado)
    LaunchedEffect(Unit) {
        schedules = ScheduleManager.getSchedules(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Grabaciones programadas",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        // Inicio
        Text("Inicio", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pickerState = PickerState.START_DATE },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Surface)
        ) {
            Text(
                if (startCal != null) sdf.format(startCal!!.time)
                else "Seleccionar fecha y hora de inicio",
                color = if (startCal != null) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Fin (deshabilitado hasta elegir inicio)
        Text("Fin", color = if (startCal != null) TextSecondary else TextSecondary.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pickerState = PickerState.END_DATE },
            modifier = Modifier.fillMaxWidth(),
            enabled = startCal != null,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (startCal != null) Surface else Surface.copy(alpha = 0.5f))
        ) {
            Text(
                if (endCal != null) sdf.format(endCal!!.time)
                else if (startCal == null) "Primero selecciona el inicio"
                else "Seleccionar fecha y hora de fin",
                color = if (endCal != null) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        val canSchedule = startCal != null && endCal != null &&
            endCal!!.timeInMillis > startCal!!.timeInMillis &&
            startCal!!.timeInMillis > System.currentTimeMillis() &&
            (endCal!!.timeInMillis - startCal!!.timeInMillis) <= 12 * 60 * 60 * 1000L

        val durationError = startCal != null && endCal != null &&
            endCal!!.timeInMillis > startCal!!.timeInMillis &&
            (endCal!!.timeInMillis - startCal!!.timeInMillis) > 12 * 60 * 60 * 1000L

        // Detectar solapamiento con programaciones existentes
        val overlapError = startCal != null && endCal != null && canSchedule && schedules.any { s ->
            val newStart = startCal!!.timeInMillis
            val newEnd = endCal!!.timeInMillis
            !(newEnd <= s.startMillis || newStart >= s.endMillis)
        }

        Button(
            onClick = {
                if (canSchedule && !overlapError) {
                    val schedule = ScheduleManager.addSchedule(
                        context,
                        startCal!!.timeInMillis,
                        endCal!!.timeInMillis
                    )
                    ScheduledRecordingReceiver.scheduleAll(context)
                    schedules = ScheduleManager.getSchedules(context)
                    startCal = null
                    endCal = null
                }
            },
            enabled = canSchedule && !overlapError,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Programar grabación")
        }

        if (!canSchedule && startCal != null && endCal != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    startCal!!.timeInMillis <= System.currentTimeMillis() ->
                        "La fecha de inicio debe ser futura"
                    endCal!!.timeInMillis <= startCal!!.timeInMillis ->
                        "La fecha de fin debe ser posterior a la de inicio"
                    durationError ->
                        "La duración máxima es de 12 horas"
                    overlapError ->
                        "Se solapa con otra programación existente"
                    else -> ""
                },
                color = Primary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // Lista
        Text(
            "Programadas (${schedules.size})",
            color = TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(12.dp))

        if (schedules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No hay grabaciones programadas",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedules) { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        sdf = sdf,
                        onDelete = {
                            ScheduledRecordingReceiver.cancelSchedule(context, schedule.id)
                            ScheduleManager.removeSchedule(context, schedule.id)
                            schedules = ScheduleManager.getSchedules(context)
                        }
                    )
                }
            }
        }
    }

    // Date/Time pickers
    DateTimePickers(
        pickerState = pickerState,
        startCal = startCal,
        endCal = endCal,
        onStartSelected = { cal -> startCal = cal },
        onEndSelected = { cal -> endCal = cal },
        onDismiss = { pickerState = PickerState.NONE }
    )
}

enum class PickerState { NONE, START_DATE, START_TIME, END_DATE, END_TIME }

@Composable
private fun DateTimePickers(
    pickerState: PickerState,
    startCal: Calendar?,
    endCal: Calendar?,
    onStartSelected: (Calendar) -> Unit,
    onEndSelected: (Calendar) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Usar LaunchedEffect para mostrar el dialogo solo una vez por cambio de estado
    LaunchedEffect(pickerState) {
        when (pickerState) {
            PickerState.START_DATE -> {
                val cal = startCal ?: Calendar.getInstance()
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val newCal = Calendar.getInstance()
                        newCal.set(year, month, day, 0, 0, 0)
                        newCal.set(Calendar.MILLISECOND, 0)
                        onStartSelected(newCal)
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                newCal.set(Calendar.HOUR_OF_DAY, hour)
                                newCal.set(Calendar.MINUTE, minute)
                                onStartSelected(newCal)
                                onDismiss()
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).apply {
                            setOnCancelListener { onDismiss() }
                            show()
                        }
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setOnCancelListener { onDismiss() }
                    show()
                }
            }
            PickerState.END_DATE -> {
                val cal = endCal ?: Calendar.getInstance()
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val newCal = Calendar.getInstance()
                        newCal.set(year, month, day, 0, 0, 0)
                        newCal.set(Calendar.MILLISECOND, 0)
                        onEndSelected(newCal)
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                newCal.set(Calendar.HOUR_OF_DAY, hour)
                                newCal.set(Calendar.MINUTE, minute)
                                onEndSelected(newCal)
                                onDismiss()
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).apply {
                            setOnCancelListener { onDismiss() }
                            show()
                        }
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setOnCancelListener { onDismiss() }
                    show()
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun ScheduleItem(
    schedule: ScheduledRecording,
    sdf: SimpleDateFormat,
    onDelete: () -> Unit
) {
    val startText = sdf.format(Date(schedule.startMillis))
    val endText = sdf.format(Date(schedule.endMillis))
    val durationMillis = schedule.durationMillis()
    val durationHours = durationMillis / (1000 * 60 * 60)
    val durationMinutes = (durationMillis / (1000 * 60)) % 60
    val durationText = if (durationHours > 0) "$durationHours h $durationMinutes min" else "$durationMinutes min"

    Surface(
        color = Surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    startText,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "→ $endText",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    "Duración: $durationText",
                    color = Accent,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
