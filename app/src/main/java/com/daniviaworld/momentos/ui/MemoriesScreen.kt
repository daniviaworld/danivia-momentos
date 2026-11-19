package com.daniviaworld.momentos.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniviaworld.momentos.media.Memories
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Peticiones que llegan de fuera de la pantalla (p. ej. al tocar una notificación). */
object OpenRequests {
    var memories by mutableStateOf(false)
}

/** 🎬 Recuerdos: vídeos de momentos especiales que la app prepara sola. */
@Composable
fun MemoriesScreen(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.refreshMemories() }
    var playing by remember { mutableStateOf<Memories.Memory?>(null) }
    var uploading by remember { mutableStateOf<Memories.Memory?>(null) }
    var musicFor by remember { mutableStateOf<Memories.Memory?>(null) }
    val list = vm.memories
    val day = remember { SimpleDateFormat("d 'de' MMMM", Locale("es", "ES")) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp),
            contentPadding = PaddingValues(horizontal = if (vm.isTv) 48.dp else 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!vm.isTv) {
                        IconButton(onClick = { vm.screen = Screen.Home }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                    Column {
                        Text("🎬 Recuerdos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "La app busca sola momentos especiales y te prepara un vídeo con música (de noche, cargando y con wifi). Te avisa cuando hay uno nuevo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Usa sólo fotos con ❤️ me gusta o reacciones (sin capturas ni ráfagas repetidas) y la música de la familia. Busca: «Tal día como hoy», lo mejor del mes pasado, un gran día y las favoritas. Si uno no te gusta, bórralo y pide otro.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (vm.preparingMemory) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Preparando tu recuerdo… puede tardar un par de minutos.")
                            }
                        } else {
                            Button(onClick = { vm.prepareMemoryNow() }) { Text("✨ Prepárame uno ahora") }
                        }
                    }
                }
            }
            vm.memoryWork?.let { w ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(w)
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay recuerdos. ¡Sube fotos y dales cariño ❤️; la app hará el resto!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(list, key = { it.key }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        ) {
                            PhotoImage(m.coverUrl, Modifier.fillMaxSize())
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
                            )
                            Column(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp),
                            ) {
                                if (!m.seen) {
                                    Text(
                                        "NUEVO",
                                        color = Color(0xFF2A1A00),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFFFB347))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text(m.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "${m.subtitle} · preparado el ${day.format(m.createdAt)}",
                                    color = Color(0xFFE0DDF0),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = {
                                vm.markMemorySeen(m)
                                playing = m
                            }) { Text("▶ Ver") }
                            if (!vm.isTv) {
                                OutlinedButton(onClick = { vm.shareMemory(m) }) { Text("📤 Compartir") }
                                OutlinedButton(onClick = { musicFor = m }, enabled = vm.memoryWork == null) { Text("🎵 Música") }
                            }
                            OutlinedButton(onClick = { uploading = m }) { Text("👨‍👩‍👧 Subir a la familia") }
                            TextButton(onClick = { vm.deleteMemory(m) }) { Text("🗑 Borrar") }
                        }
                    }
                }
            }
        }
    }

    playing?.let { m ->
        Dialog(onDismissRequest = { playing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize()) {
                    KeepScreenOn()
                    VideoPlayer(Uri.fromFile(File(m.videoPath)).toString(), Modifier.fillMaxSize(), onEnded = { playing = null })
                    if (!vm.isTv) {
                        IconButton(onClick = { playing = null }, modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    uploading?.let { m -> UploadMemoryDialog(vm, m) { uploading = null } }
    musicFor?.let { m -> MemoryMusicDialog(vm, m) { musicFor = null } }
}

/** Cambiar la música de un recuerdo. */
@Composable
private fun MemoryMusicDialog(vm: MainViewModel, m: Memories.Memory, onDismiss: () -> Unit) {
    val songs by vm.songs.collectAsState()
    var key by remember { mutableStateOf(songs.firstOrNull()?.let { com.daniviaworld.momentos.data.MusicLibrary.familyKey(it) } ?: "alegre") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎵 Música de «${m.title}»") },
        text = {
            Column {
                Text("Elige la canción nueva (primero las de la familia):")
                Spacer(Modifier.height(8.dp))
                MusicChips(selected = key, onSelect = { key = it }, songs = songs)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.changeMemoryMusic(m, key)
                onDismiss()
            }) { Text("Cambiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Elegir en qué álbum se sube el recuerdo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadMemoryDialog(vm: MainViewModel, m: Memories.Memory, onDismiss: () -> Unit) {
    val albums by vm.albums.collectAsState()
    var albumId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subir «${m.title}»") },
        text = {
            Column {
                Text("Lo verá toda la familia, también en la tele. ¿En qué álbum?")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = albumId == null, onClick = { albumId = null }, label = { Text("📂 Sin álbum") })
                    albums.forEach { a ->
                        FilterChip(selected = albumId == a.id, onClick = { albumId = a.id }, label = { Text(a.title, maxLines = 1) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.uploadMemory(m, albumId)
                onDismiss()
            }) { Text("Subir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Aviso en la pantalla principal cuando hay un recuerdo nuevo sin ver. */
@Composable
fun NewMemoryBanner(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.refreshMemories() }
    val m = vm.memories.firstOrNull { !it.seen } ?: return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (vm.isTv) 40.dp else 10.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎬", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Te hemos preparado un recuerdo", fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    "${m.title} · ${m.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = { vm.screen = Screen.Memories }) { Text("Ver") }
        }
    }
}
