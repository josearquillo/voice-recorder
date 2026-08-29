package com.josearquillo.voicerecorder.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderApp() {
    val context = LocalContext.current
    var hasMicPermission by remember { mutableStateOf(false) }
    var hasNotifPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf(listOf<File>()) }
    var currentlyPlaying by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var showSettings by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }

    // Actualizar progreso de reproduccion cada 200ms
    LaunchedEffect(currentlyPlaying) {
        while (currentlyPlaying != null && mediaPlayer != null) {
            try {
                val pos = mediaPlayer?.currentPosition ?: 0
                val dur = mediaPlayer?.duration ?: 1
                if (dur > 0) playbackProgress = pos.toFloat() / dur
            } catch (e: Exception) {}
            kotlinx.coroutines.delay(200)
        }
        playbackProgress = 0f
    }

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

    if (showSchedule) {
        ScheduleScreen(onBack = { showSchedule = false })
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
            IconButton(onClick = { showSchedule = true }) {
                Icon(Icons.Default.Schedule, contentDescription = "Programar", tint = TextSecondary, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = TextSecondary, modifier = Modifier.size(24.dp))
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
            val listState = rememberLazyListState()
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recordings) { file ->
                        RecordingItem(
                            file = file,
                            isPlaying = currentlyPlaying == file,
                            playbackProgress = if (currentlyPlaying == file) playbackProgress else 0f,
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
                            onSeek = { fraction ->
                                mediaPlayer?.let { mp ->
                                    val dur = mp.duration
                                    if (dur > 0) {
                                        mp.seekTo((fraction * dur).toInt())
                                    }
                                }
                            },
                            onDelete = {
                                showDeleteDialog = file
                            },
                            onRename = {
                                showRenameDialog = file
                            },
                            onShare = {
                                shareFile(context, file)
                            }
                        )
                    }
                }

                // Barra de scroll dibujada manualmente
                val layoutInfo = listState.layoutInfo
                val showScrollbar = layoutInfo.visibleItemsInfo.size < layoutInfo.totalItemsCount

                if (showScrollbar) {
                    val totalHeight = layoutInfo.viewportSize.height.toFloat()
                    val totalItems = layoutInfo.totalItemsCount.toFloat()
                    val visibleItems = layoutInfo.visibleItemsInfo.size.toFloat()
                    val firstOffset = layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
                    val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                    val contentHeight = totalItems * itemSize
                    val thumbHeight = (visibleItems / totalItems * totalHeight).coerceAtLeast(30f)
                    val maxScroll = contentHeight - totalHeight
                    val thumbY = if (maxScroll > 0) {
                        (-firstOffset / maxScroll * (totalHeight - thumbHeight)).coerceIn(0f, totalHeight - thumbHeight)
                    } else 0f

                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(4.dp)
                    ) {
                        // Pista
                        drawRect(
                            color = Surface.copy(alpha = 0.3f),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height)
                        )
                        // Thumb
                        drawRect(
                            color = TextSecondary.copy(alpha = 0.6f),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, thumbY),
                            size = androidx.compose.ui.geometry.Size(size.width, thumbHeight)
                        )
                    }
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

    // Dialogo de renombrado
    showRenameDialog?.let { file ->
        var newName by remember(file) { mutableStateOf(file.nameWithoutExtension) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Renombrar grabación") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Surface
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != file.nameWithoutExtension) {
                        val dir = file.parentFile
                        val newFile = File(dir, "$newName.m4a")
                        if (!newFile.exists()) {
                            file.renameTo(newFile)
                            refreshRecordings(context) { recordings = it }
                        }
                    }
                    showRenameDialog = null
                }) {
                    Text("Guardar", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
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
    playbackProgress: Float,
    onPlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit
) {
    val displayName = file.nameWithoutExtension
    val isDefaultName = displayName.startsWith("REC_")
    val (dateText, timeText) = if (isDefaultName) {
        try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val date = sdf.parse(displayName.replace("REC_", ""))
            val d = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date ?: Date())
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date ?: Date())
            d to t
        } catch (e: Exception) {
            displayName to ""
        }
    } else {
        // Nombre personalizado: mostrarlo como titulo
        displayName to ""
    }

    // Duracion del archivo
    val durationMs = remember(file) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            d
        } catch (e: Exception) {
            0L
        }
    }

    // Progreso de reproduccion (actualizado por el padre)
    val progressMs by remember(file, isPlaying) { mutableStateOf(0L) }

    val durationText = run {
        val totalSeconds = durationMs / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    // Tamano
    val sizeText = remember(file) {
        val sizeKB = file.length() / 1024
        if (sizeKB < 1024) "$sizeKB KB" else String.format("%.1f MB", sizeKB / 1024.0)
    }

    Surface(
        color = Surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Izquierda: boton play/stop
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Accent else Primary)
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Detener" else "Reproducir",
                        tint = OnPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Derecha: info en filas
                Column(modifier = Modifier.weight(1f)) {
                    // Fila 1: fecha/hora
                    Text(
                        if (isDefaultName) "$dateText $timeText" else dateText,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Fila 2: duracion y tamano
                    Text(
                        "$durationText  ·  $sizeText",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    // Fila 3: botones de accion (solo iconos)
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Editar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRename() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Renombrar", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        // Compartir
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onShare() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        // Eliminar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDelete() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Barra de progreso (solo visible mientras reproduce)
            if (isPlaying && durationMs > 0) {
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = playbackProgress.coerceIn(0f, 1f),
                    onValueChange = { onSeek(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // API 29+: leer desde MediaStore (Music/Recordings)
        val recordings = mutableListOf<File>()
        try {
            val collection = android.provider.MediaStore.Audio.Media.getContentUri(
                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.DATA
            )
            val selection = "${android.provider.MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("Music/Recordings/")
            val sortOrder = "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC"

            context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) recordings.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onResult(recordings)
    } else {
        // API < 29: leer desde carpeta publica
        @Suppress("DEPRECATION")
        val dir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC
            ), "Recordings"
        )
        val files = dir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        onResult(files)
    }
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
