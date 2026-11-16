package com.daniviaworld.momentos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.image.Frames
import com.daniviaworld.momentos.image.ImageProcessor
import com.daniviaworld.momentos.media.AlbumVideoMaker
import com.daniviaworld.momentos.media.CollageMaker
import com.daniviaworld.momentos.media.CollageOptions
import com.daniviaworld.momentos.media.PhotoBookMaker
import com.daniviaworld.momentos.share.Sharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Miniatura de una foto descargada a un Bitmap (para las vistas previas). */
private suspend fun loadThumb(cacheDir: File, photo: Photo, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val f = File(cacheDir, "prev_${photo.id}.jpg")
        if (!f.exists()) Sharer.download(photo.thumbUrl.ifBlank { photo.url }, f)
        ImageProcessor.decodeFile(f, maxSide)
    } catch (_: Exception) {
        null
    }
}

private fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
    val f = maxSide.toFloat() / maxOf(b.width, b.height)
    if (f >= 1f) return b
    return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(1), (b.height * f).roundToInt().coerceAtLeast(1), true)
}

@Composable
private fun ChipRows(options: List<Pair<String, String>>, selected: String, perRow: Int = 2, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) ->
                    FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) })
                }
            }
        }
    }
}

/** Collage: elegir las fotos (y su orden), la forma, el formato, el fondo y un marco, con vista previa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageDialog(
    title: String,
    photos: List<Photo>,
    onDismiss: () -> Unit,
    onCreate: (List<Photo>, CollageOptions) -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var chosenIds by remember { mutableStateOf(CollageMaker.suggested(photos).map { it.id }) }
    var options by remember { mutableStateOf(CollageOptions()) }
    val thumbs = remember { mutableStateMapOf<String, Bitmap>() }
    val chosen = chosenIds.mapNotNull { id -> photos.firstOrNull { it.id == id } }

    LaunchedEffect(chosenIds) {
        for (p in chosen) {
            if (thumbs[p.id] == null) loadThumb(context.cacheDir, p, 520)?.let { thumbs[p.id] = it }
        }
    }
    val ready = chosen.mapNotNull { thumbs[it.id] }
    val dates = AlbumVideoMaker.dateRange(chosen)
    // vista previa sin marco (también sirve para las miniaturas de marcos)
    val plain by produceState<Bitmap?>(null, ready.size, chosenIds, options.copy(frame = "")) {
        value = if (ready.isEmpty()) null else withContext(Dispatchers.Default) {
            val (w, h) = CollageMaker.size(options.format, 540)
            CollageMaker.render(ready, options.copy(frame = ""), w, h, title, dates)
        }
    }
    val preview by produceState<Bitmap?>(null, plain, options.frame) {
        val p = plain
        value = if (p == null) null else withContext(Dispatchers.Default) { Frames.apply(p, options.frame) }
    }
    val small = remember(plain) { plain?.let { scaleDown(it, 240) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0F1A), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White) }
                    Text("🧩 Collage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = { onCreate(chosen, options) }, enabled = chosen.isNotEmpty()) { Text("Crear") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    val p = preview
                    when {
                        chosen.isEmpty() -> Text("Elige al menos una foto en la pestaña «Fotos».")
                        p == null -> CircularProgressIndicator(color = Color.White)
                        else -> Image(p.asImageBitmap(), "Vista previa", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                TabRow(selectedTabIndex = tab, containerColor = Color(0xFF181A2B), contentColor = Color.White) {
                    listOf("📷 Fotos", "🔷 Forma", "🎨 Estilo", "🖼️ Marco").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
                    }
                }
                Box(Modifier.fillMaxWidth().height(270.dp).padding(12.dp)) {
                    when (tab) {
                        0 -> Column {
                            Text(
                                "Elegidas ${chosen.size} de ${CollageMaker.MAX_PHOTOS} máximo · el número es su orden",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB8BBD0),
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(76.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(photos.size) { i ->
                                    val ph = photos[i]
                                    val order = chosenIds.indexOf(ph.id)
                                    Box(
                                        Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                if (order >= 0) 3.dp else 0.dp,
                                                if (order >= 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .clickable {
                                                chosenIds = when {
                                                    order >= 0 -> chosenIds - ph.id
                                                    chosenIds.size < CollageMaker.MAX_PHOTOS -> chosenIds + ph.id
                                                    else -> chosenIds
                                                }
                                            },
                                    ) {
                                        AsyncImage(model = ph.thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        if (order >= 0) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center,
                                            ) { Text("${order + 1}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            ChipRows(CollageMaker.layouts, options.layout) { options = options.copy(layout = it) }
                        }
                        2 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text("Formato", fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CollageMaker.formats.forEach { (key, label) ->
                                    FilterChip(selected = options.format == key, onClick = { options = options.copy(format = key) }, label = { Text(label) })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Fondo", fontWeight = FontWeight.Bold)
                            ChipRows(CollageMaker.backgrounds, options.background, perRow = 3) { options = options.copy(background = it) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Título y fechas arriba", modifier = Modifier.weight(1f))
                                Switch(checked = options.showTitle, onCheckedChange = { options = options.copy(showTitle = it) })
                            }
                        }
                        else -> FrameThumbPicker(small, options.frame) { options = options.copy(frame = it) }
                    }
                }
            }
        }
    }
}

/** Libro de fotos: elegir el diseño de las páginas (todo lo que rodea a la foto), con vista previa. */
@Composable
fun BookDialog(
    title: String,
    photos: List<Photo>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val context = LocalContext.current
    var theme by remember { mutableStateOf("vintage") }
    val sample = remember(photos) { photos.maxByOrNull { it.likes.size + it.reactions.size } ?: photos.firstOrNull() }
    val sampleBmp by produceState<Bitmap?>(null, sample?.id) {
        value = sample?.let { loadThumb(context.cacheDir, it, 520) }
    }
    val small = remember(sampleBmp) { sampleBmp?.let { scaleDown(it, 240) } }
    val page by produceState<Bitmap?>(null, sampleBmp, theme) {
        val b = sampleBmp
        value = if (b == null || sample == null) null else withContext(Dispatchers.Default) {
            PhotoBookMaker.previewPage(b, theme, 540, sample.uploaderName, sample.createdAt)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0F1A), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("📖 Libro de fotos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${minOf(photos.size, 40)} páginas con foto · PDF para imprimir",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB8BBD0),
                        )
                    }
                    Button(onClick = { onCreate(theme) }, enabled = photos.isNotEmpty()) { Text("Crear") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    val p = page
                    if (p == null) CircularProgressIndicator(color = Color.White)
                    else Image(p.asImageBitmap(), "Así quedará cada página", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Diseño de las páginas", fontWeight = FontWeight.Bold)
                    Text(
                        "Lo que rodea a cada foto. Todas las páginas llevan el mismo diseño, con quién la subió y cuándo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8BBD0),
                    )
                    Spacer(Modifier.height(8.dp))
                    FrameThumbPicker(small, theme) { theme = it }
                }
            }
        }
    }
}
