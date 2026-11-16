package com.daniviaworld.momentos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniviaworld.momentos.data.MusicLibrary
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.image.Frames
import com.daniviaworld.momentos.image.ImageProcessor
import com.daniviaworld.momentos.image.Sticker
import com.daniviaworld.momentos.image.Stickers
import com.daniviaworld.momentos.share.Sharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Editor de una foto ya subida (admin o quien la subió): pegatinas que se arrastran con el dedo
 * y marco. Se guarda para toda la familia; la original se conserva para poder volver a ella.
 */
@Composable
fun PhotoEditDialog(vm: MainViewModel, photo: Photo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val work = vm.editWork
    var tab by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf(photo.editFrame) }
    var stickers by remember { mutableStateOf(Stickers.fromJson(photo.editStickers)) }
    var selectedId by remember { mutableLongStateOf(-1L) }
    var nextId by remember { mutableLongStateOf(1000L) }
    var writing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // la foto de partida es siempre la original (sin marco), así los cambios no se acumulan
    val base by produceState<Bitmap?>(null, photo.id) {
        value = withContext(Dispatchers.IO) {
            try {
                val f = File(context.cacheDir, "editor_${photo.id}.jpg")
                Sharer.download(photo.originalUrl.ifBlank { photo.url }, f)
                ImageProcessor.decodeFile(f, 1280).also { f.delete() }
            } catch (_: Exception) {
                failed = true
                null
            }
        }
    }
    val small = remember(base) {
        base?.let { b ->
            val f = 240f / maxOf(b.width, b.height)
            Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(1), (b.height * f).roundToInt().coerceAtLeast(1), true)
        }
    }
    // vista previa final (pegatinas + marco) en la pestaña Marco
    val finalPreview by produceState<Bitmap?>(null, base, stickers, frame, tab) {
        val b = base
        value = if (b == null || tab != 1) null else withContext(Dispatchers.Default) {
            Frames.apply(Stickers.draw(b, stickers), frame)
        }
    }

    fun addSticker(text: String, isText: Boolean) {
        val s = Sticker(id = nextId++, text = text, size = if (isText) 0.12f else 0.2f, isText = isText)
        stickers = stickers + s
        selectedId = s.id
    }

    Dialog(
        onDismissRequest = { if (work == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0F1A), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, enabled = work == null) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                    Text("✏️ Editar foto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { vm.saveEditedPhoto(photo, stickers, frame) { onDismiss() } },
                        enabled = base != null && work == null && (stickers.isNotEmpty() || frame.isNotBlank() || photo.isEdited),
                    ) { Text("Guardar") }
                }

                // ── Zona de la foto ──
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val b = base
                    when {
                        failed -> Text("No se pudo descargar la foto. Revisa la conexión.")
                        b == null -> CircularProgressIndicator(color = Color.White)
                        tab == 1 -> {
                            val fp = finalPreview
                            if (fp == null) CircularProgressIndicator(color = Color.White)
                            else Image(fp.asImageBitmap(), "Vista previa", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        else -> StickerCanvas(
                            base = b,
                            stickers = stickers,
                            selectedId = selectedId,
                            onSelect = { selectedId = it },
                            onMove = { id, x, y -> stickers = stickers.map { if (it.id == id) it.copy(x = x, y = y) else it } },
                        )
                    }
                    if (work != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xDD000000))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(work.first, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { work.second }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                TabRow(selectedTabIndex = tab, containerColor = Color(0xFF181A2B), contentColor = Color.White) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("😀 Pegatinas") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🖼️ Marco") })
                }

                Column(Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (tab == 0) {
                        val sel = stickers.firstOrNull { it.id == selectedId }
                        if (sel != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tamaño", style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = sel.size,
                                    onValueChange = { v -> stickers = stickers.map { if (it.id == sel.id) it.copy(size = v) else it } },
                                    valueRange = 0.05f..0.6f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                )
                                IconButton(onClick = {
                                    stickers = stickers.filter { it.id != sel.id }
                                    selectedId = -1L
                                }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar pegatina", tint = Color.White) }
                            }
                        } else {
                            Text(
                                "Toca una pegatina para ponerla. Luego arrástrala con el dedo; tócala para cambiar su tamaño o quitarla.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB8BBD0),
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        OutlinedButton(onClick = { writing = true }) { Text("Aa  Escribir un texto") }
                        Spacer(Modifier.height(6.dp))
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            items(Stickers.emojis.size) { i ->
                                val e = Stickers.emojis[i]
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF23263A))
                                        .clickable { addSticker(e, false) },
                                    contentAlignment = Alignment.Center,
                                ) { Text(e, fontSize = 26.sp) }
                            }
                        }
                    } else {
                        FrameThumbPicker(small, frame) { frame = it }
                    }
                    if (photo.isEdited) {
                        TextButton(onClick = { vm.restoreOriginal(photo) { onDismiss() } }, enabled = work == null) {
                            Text("↩️ Volver a la foto original (sin marco ni pegatinas)")
                        }
                    }
                }
            }
        }
    }

    if (writing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text("Escribe un texto") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("¡Feliz cumple, abuela!") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) addSticker(text.trim(), true)
                    writing = false
                }) { Text("Poner") }
            },
            dismissButton = { TextButton(onClick = { writing = false }) { Text("Cancelar") } },
        )
    }
}

/** La foto con las pegatinas encima; cada pegatina se arrastra con el dedo. */
@Composable
private fun StickerCanvas(
    base: Bitmap,
    stickers: List<Sticker>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onMove: (Long, Float, Float) -> Unit,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(
        Modifier
            .aspectRatio(base.width.toFloat() / base.height)
            .onSizeChanged { box = it }
            .pointerInputTapToDeselect { onSelect(-1L) },
    ) {
        Image(base.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        if (box.width > 0) {
            val side = min(box.width, box.height).toFloat()
            stickers.forEach { s ->
                key(s.id) {
                    var own by remember { mutableStateOf(IntSize.Zero) }
                    // posición viva mientras se arrastra (se guarda al soltar)
                    var pos by remember { mutableStateOf(s.x to s.y) }
                    LaunchedEffect(s.x, s.y) { pos = s.x to s.y }
                    val px = s.size * side
                    val fontSize = with(density) { (if (s.isText) px else px * 0.85f).toSp() }
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (pos.first * box.width - own.width / 2f).roundToInt(),
                                    (pos.second * box.height - own.height / 2f).roundToInt(),
                                )
                            }
                            .onSizeChanged { own = it }
                            .then(
                                if (s.id == selectedId) Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .stickerGestures(
                                id = s.id,
                                onTap = { onSelect(s.id) },
                                onDrag = { dx, dy ->
                                    onSelect(s.id)
                                    pos = (pos.first + dx / box.width).coerceIn(0f, 1f) to (pos.second + dy / box.height).coerceIn(0f, 1f)
                                },
                                onEnd = { onMove(s.id, pos.first, pos.second) },
                            )
                            .padding(4.dp),
                    ) {
                        if (s.isText) {
                            Text(
                                s.text,
                                style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, color = Color.Black, drawStyle = Stroke(width = px * 0.12f)),
                            )
                            Text(
                                s.text,
                                style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, color = Color.White, shadow = Shadow(Color(0x78000000), blurRadius = px * 0.1f)),
                            )
                        } else {
                            Text(s.text, fontSize = fontSize)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.pointerInputTapToDeselect(onTap: () -> Unit): Modifier =
    this.then(Modifier.pointerInput("deselect") { detectTapGestures(onTap = { onTap() }) })

private fun Modifier.stickerGestures(
    id: Long,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = this
    .pointerInput("tap$id") { detectTapGestures(onTap = { onTap() }) }
    .pointerInput("drag$id") {
        detectDragGestures(
            onDragEnd = onEnd,
            onDragCancel = onEnd,
        ) { change, drag ->
            change.consume()
            onDrag(drag.x, drag.y)
        }
    }

/** Cambiar la música de un vídeo ya subido. */
@Composable
fun VideoMusicDialog(vm: MainViewModel, photo: Photo, onDismiss: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val album = vm.albumOf(photo)
    var musicKey by remember {
        mutableStateOf(album?.music?.takeIf { it.isNotBlank() } ?: songs.firstOrNull()?.let { MusicLibrary.familyKey(it) } ?: "alegre")
    }
    var keepOriginal by remember { mutableStateOf(false) }
    val work = vm.editWork
    AlertDialog(
        onDismissRequest = { if (work == null) onDismiss() },
        title = { Text("🎵 Cambiar la música del vídeo") },
        text = {
            Column {
                if (work != null) {
                    Text(work.first, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { work.second }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deja la app abierta; se está montando en tu móvil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Elige la música nueva:", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    MusicChips(selected = musicKey, onSelect = { musicKey = it }, songs = songs)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mantener el sonido original", fontWeight = FontWeight.Bold)
                            Text(
                                "Apagado para los vídeos de álbum (se quita la música vieja). " +
                                    "Enciéndelo en vídeos grabados con el móvil para oír también las voces.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = keepOriginal, onCheckedChange = { keepOriginal = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.changeVideoMusic(photo, musicKey, keepOriginal) { onDismiss() } },
                enabled = work == null && (musicKey.isNotBlank() || !keepOriginal),
            ) { Text("Cambiar música") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = work == null) { Text("Cancelar") }
        },
    )
}
