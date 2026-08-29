package com.josearquillo.voicerecorder.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.josearquillo.voicerecorder.RecordingService
import com.josearquillo.voicerecorder.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceRecorderApp() {
    val context = LocalContext.current
    var hasMicPermission by remember { mutableStateOf(false) }
    var hasNotifPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf(listOf<File>()) }
    var currentlyPlaying by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotifPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            hasNotifPermission = true
        }
    }

    LaunchedEffect(Unit) {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasMicPermission || !hasNotifPermission) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
        refreshRecordings(context) { recordings = it }
    }

    // Refrescar lista al volver de segundo plano
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            refreshRecordings(context) { recordings = it }
        }
    }

    if (!hasMicPermission) {
        PermissionScreen {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
        return
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        // Header con titulo y boton de ajustes
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Voice Recorder",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Sin anuncios. Grabación offline.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = TextSecondary, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(Modifier.height(32.dp))

        // Boton de grabar
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            RecordButton(
                isRecording = isRecording,
                onToggle = {
                    if (isRecording) {
                        val intent = Intent(context, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_STOP
                        }
                        context.startService(intent)
                        isRecording = false
                    } else {
                        val intent = Intent(context, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        isRecording = true
                    }
                }
            )
        }

        if (isRecording) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Grabando… La grabación continúa aunque cierres la app.",
                color = Primary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))

        // Lista de grabaciones
        Text(
            "Grabaciones (${recordings.size})",
            color = TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(12.dp))

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No hay grabaciones todavía",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordings) { file ->
                    RecordingItem(
                        file = file,
                        isPlaying = currentlyPlaying == file,
                        onPlay = {
                            if (currentlyPlaying == file) {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                                currentlyPlaying = null
                            } else {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(file.absolutePath)
                                    prepare()
                                    setOnCompletionListener {
                                        currentlyPlaying = null
                                        mediaPlayer = null
                                    }
                                    start()
                                }
                                currentlyPlaying = file
                            }
                        },
                        onDelete = {
                            showDeleteDialog = file
                        },
                        onShare = {
                            shareFile(context, file)
                        }
                    )
                }
            }
        }
    }

    // Dialogo de confirmacion de borrado
    showDeleteDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Eliminar grabación") },
            text = { Text("¿Eliminar ${file.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    if (currentlyPlaying == file) {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        currentlyPlaying = null
                    }
                    file.delete()
                    refreshRecordings(context) { recordings = it }
                    showDeleteDialog = null
                }) {
                    Text("Sí", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isRecording) PrimaryDark else Primary)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Detener" else "Grabar",
                tint = OnPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (isRecording) "Detener" else "Grabar",
            color = if (isRecording) Primary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecordingItem(
    file: File,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val displayName = file.nameWithoutExtension.replace("REC_", "")
    val (dateText, timeText) = try {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val date = sdf.parse(displayName)
        val d = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date ?: Date())
        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date ?: Date())
        d to t
    } catch (e: Exception) {
        displayName to ""
    }

    Surface(
        color = Surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Stop
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Accent else Primary)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Detener" else "Reproducir",
                    tint = OnPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dateText,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    timeText,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                val sizeKB = file.length() / 1024
                val sizeText = if (sizeKB < 1024) "$sizeKB KB" else "${sizeKB / 1024} MB"
                Text(
                    sizeText,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Compartir
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Compartir", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }

            // Eliminar
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Necesito acceso al micrófono para grabar audio.",
            color = TextPrimary,
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Conceder permiso")
        }
    }
}

private fun refreshRecordings(context: Context, onResult: (List<File>) -> Unit) {
    val dir = File(context.getExternalFilesDir(null), "Recordings")
    val files = dir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    onResult(files)
}

private fun shareFile(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir grabación"))
}
