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
import androidx.compose.material.icons.filled.Speed
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
import com.josearquillo.voicerecorder.SettingsManager
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
    var sortOrder by remember { mutableStateOf(SortOrder.DATE) }
    var currentlyPlaying by remember { mutableStateOf<File?>(null) }
    var isPaused by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var showSettings by remember { mutableStateOf(false) }

    // Liberar MediaPlayer al salir de la composicion
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let { mp ->
                try { mp.stop() } catch (_: Exception) {}
                mp.release()
            }
        }
    }

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
        isPaused = false
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
        isRecording = SettingsManager.isRecording(context)
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
        // Header con titulo y botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                        SettingsManager.setRecording(context, false)
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
                        SettingsManager.setRecording(context, true)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Grabaciones (${recordings.size})",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (recordings.isNotEmpty()) {
                TextButton(
                    onClick = {
                        sortOrder = when (sortOrder) {
                            SortOrder.DATE -> SortOrder.NAME
                            SortOrder.NAME -> SortOrder.SIZE
                            SortOrder.SIZE -> SortOrder.DURATION
                            SortOrder.DURATION -> SortOrder.DATE
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("↕ ${sortOrder.label}", color = Accent, fontSize = 12.sp)
                }
            }
        }

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
            val sortedRecordings = remember(recordings, sortOrder) {
                sortRecordings(recordings, sortOrder)
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedRecordings, key = { it.absolutePath }) { file ->
                        RecordingItem(
                            file = file,
                            isPlaying = currentlyPlaying == file,
                            isPaused = isPaused,
                            playbackSpeed = playbackSpeed,
                            playbackProgress = if (currentlyPlaying == file) playbackProgress else 0f,
                            onPlay = {
                                if (currentlyPlaying == file) {
                                    // Parar
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    currentlyPlaying = null
                                    isPaused = false
                                } else {
                                    // Iniciar
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    playbackSpeed = 1.0f
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        prepare()
                                        setOnCompletionListener {
                                            currentlyPlaying = null
                                            mediaPlayer = null
                                            isPaused = false
                                        }
                                        start()
                                    }
                                    currentlyPlaying = file
                                    isPaused = false
                                }
                            },
                            onPauseToggle = {
                                mediaPlayer?.let { mp ->
                                    if (isPaused) {
                                        mp.start()
                                        isPaused = false
                                    } else {
                                        mp.pause()
                                        isPaused = true
                                    }
                                }
                            },
                            onSpeedChange = { speed ->
                                playbackSpeed = speed
                                mediaPlayer?.let { mp ->
                                    try {
                                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                                    } catch (e: Exception) {}
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
                    val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                    val totalItems = layoutInfo.totalItemsCount
                    val visibleItems = layoutInfo.visibleItemsInfo.size

                    // Proporcion del thumb basada en items visibles vs totales
                    val thumbRatio = visibleItems.toFloat() / totalItems.toFloat()
                    val thumbHeight = (thumbRatio * viewportHeight).coerceAtLeast(30f)

                    // Posicion del thumb basada en el primer item visible
                    val firstVisibleIndex = listState.firstVisibleItemIndex
                    val firstVisibleOffset = listState.firstVisibleItemScrollOffset

                    // Estimar altura promedio del primer item visible
                    val avgItemHeight = if (visibleItems > 0) {
                        val totalVisibleHeight = layoutInfo.visibleItemsInfo.sumOf { it.size }
                        totalVisibleHeight.toFloat() / visibleItems.toFloat()
                    } else 1f

                    val scrollPx = firstVisibleIndex * avgItemHeight + firstVisibleOffset
                    val maxScrollPx = (totalItems - visibleItems) * avgItemHeight
                    val scrollProgress = if (maxScrollPx > 0) {
                        (scrollPx / maxScrollPx).coerceIn(0f, 1f)
                    } else 0f
                    val thumbY = scrollProgress * (viewportHeight - thumbHeight)

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
        var renameError by remember(file) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Renombrar grabación") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; renameError = null },
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent,
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Surface
                        )
                    )
                    renameError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = Primary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != file.nameWithoutExtension) {
                        val dir = file.parentFile
                        val newFile = File(dir, "$newName.m4a")
                        if (newFile.exists()) {
                            renameError = "Ya existe una grabación con ese nombre"
                        } else {
                            file.renameTo(newFile)
                            refreshRecordings(context) { recordings = it }
                            showRenameDialog = null
                        }
                    } else if (newName.isBlank()) {
                        renameError = "El nombre no puede estar vacío"
                    } else {
                        showRenameDialog = null
                    }
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
    isPaused: Boolean,
    playbackSpeed: Float,
    playbackProgress: Float,
    onPlay: () -> Unit,
    onPauseToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit,
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
                    // Fila 1: nombre de la grabacion
                    Text(
                        displayName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Fila 1b: fecha/hora
                    Text(
                        if (isDefaultName) "$dateText $timeText" else dateText,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
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

            // Barra de progreso + controles (solo visible mientras reproduce)
            if (isPlaying && durationMs > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Boton pausa/reanudar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Surface)
                            .clickable { onPauseToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Reanudar" else "Pausar",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Slider
                    Slider(
                        value = playbackProgress.coerceIn(0f, 1f),
                        onValueChange = { onSeek(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Surface
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    // Boton velocidad
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .clickable {
                                val next = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    else -> 1.0f
                                }
                                onSpeedChange(next)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${playbackSpeed}x",
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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

enum class SortOrder(val label: String) {
    DATE("Fecha"), NAME("Nombre"), DURATION("Duración"), SIZE("Tamaño")
}

private fun sortRecordings(files: List<File>, order: SortOrder): List<File> {
    return when (order) {
        SortOrder.DATE -> files.sortedByDescending { file ->
            // Para archivos REC_yyyyMMdd_HHmmss, extraer el timestamp del nombre
            val name = file.nameWithoutExtension
            if (name.startsWith("REC_")) {
                try {
                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    sdf.parse(name.removePrefix("REC_"))?.time ?: file.lastModified()
                } catch (e: Exception) {
                    file.lastModified()
                }
            } else {
                file.lastModified()
            }
        }
        SortOrder.NAME -> files.sortedBy { it.nameWithoutExtension.lowercase() }
        SortOrder.DURATION -> files.sortedByDescending { file ->
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(file.absolutePath)
                val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                r.release()
                d
            } catch (e: Exception) { 0L }
        }
        SortOrder.SIZE -> files.sortedByDescending { it.length() }
    }
}

private fun refreshRecordings(context: Context, onResult: (List<File>) -> Unit) {
    val dir = File(context.getExternalFilesDir(null), "Recordings")
    val files = dir.listFiles()?.toList() ?: emptyList()
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
