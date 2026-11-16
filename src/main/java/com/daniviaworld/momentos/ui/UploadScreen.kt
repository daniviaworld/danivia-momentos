package com.daniviaworld.momentos.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.daniviaworld.momentos.data.Repo
import com.daniviaworld.momentos.data.UploadManager
import com.daniviaworld.momentos.image.ImageProcessor
import com.daniviaworld.momentos.image.Frames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var enhance by rememberSaveable { mutableStateOf(vm.prefs.autoEnhance) }
    var launchedOnce by rememberSaveable { mutableStateOf(false) }
    val albums by vm.albums.collectAsState()
    var frame by remember { mutableStateOf("") }
    // por defecto, el álbum que se está viendo en la pantalla principal
    var albumId by remember {
        mutableStateOf(vm.albumFilter.takeIf { it != ALL_ALBUMS && it != NO_ALBUM })
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris -> if (uris.isNotEmpty()) selected = uris }

    var creatingAlbum by remember { mutableStateOf(false) }
    if (creatingAlbum) {
        AlbumEditDialog(
            title = "Nuevo álbum",
            initialName = "",
            initialEmoji = "📸",
            songs = vm.songs.collectAsState().value,
            onDismiss = { creatingAlbum = false },
            onSave = { name, emoji, music ->
                creatingAlbum = false
                vm.createAlbumAndThen(name, emoji, music) { id -> albumId = id }
            },
        )
    }

    fun openPicker() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    LaunchedEffect(Unit) {
        if (!launchedOnce) {
            launchedOnce = true
            openPicker()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.screen = Screen.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Subir fotos y vídeos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (selected.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Elige las fotos y vídeos que quieres compartir con la familia", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { openPicker() }) { Text("Abrir la galería") }
            }
            return@Column
        }

        // Todo en una sola lista con scroll; el botón de subir queda fijo abajo
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("✨ Mejora automática", fontWeight = FontWeight.Bold)
                        Text(
                            "Arregla luz, color y nitidez. Los vídeos se ajustan y se reducen a 720p.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enhance, onCheckedChange = {
                        enhance = it
                        vm.prefs.autoEnhance = it
                    })
                }
                val firstImage = remember(selected) { selected.firstOrNull { !Repo.isVideo(context, it) } }
                if ((enhance || frame.isNotBlank()) && firstImage != null) {
                    Spacer(Modifier.height(12.dp))
                    BeforeAfter(firstImage, enhance, frame)
                }
                if (firstImage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("🖼️ Marco", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    FramePicker(firstImage, frame) { frame = it }
                }
                val videoCount = remember(selected) { selected.count { Repo.isVideo(context, it) } }
                if (videoCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🎬 $videoCount vídeo(s): máximo 5 minutos cada uno. Mantén la app abierta mientras se suben.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "📁 Álbum",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { creatingAlbum = true },
                        label = { Text("➕ Nuevo álbum") },
                    )
                    FilterChip(
                        selected = albumId == null,
                        onClick = { albumId = null },
                        label = { Text("📂 Sin álbum") },
                    )
                    // el recién creado primero, para que se vea que está elegido
                    albums.sortedByDescending { it.id == albumId }.forEach { a ->
                        FilterChip(
                            selected = albumId == a.id,
                            onClick = { albumId = a.id },
                            label = { Text(a.title, maxLines = 1) },
                        )
                    }
                }
                Text(
                    if (selected.size == 1) "1 elegida" else "${selected.size} elegidas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
                )
            }
        }

            items(selected, key = { it.toString() }) { uri ->
                Box {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    if (Repo.isVideo(context, uri)) {
                        Text("🎬", modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { openPicker() }, modifier = Modifier.weight(1f)) {
                Text("Cambiar")
            }
            Button(
                onClick = {
                    val fid = vm.prefs.familyId ?: return@Button
                    UploadManager.start(context, fid, selected, enhance, vm.prefs.myName.ifBlank { "Alguien" }, albumId, frame)
                    vm.screen = Screen.Home
                },
                modifier = Modifier.weight(2f),
            ) {
                Text(if (selected.size == 1) "Subir 1 archivo" else "Subir ${selected.size} archivos")
            }
        }
    }
}

/** Miniaturas de la propia foto con cada marco, por categorías. */
@Composable
private fun FramePicker(uri: Uri, selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var base by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        base = withContext(Dispatchers.Default) {
            try {
                ImageProcessor.preview(context, uri, enhance = false, maxSide = 240)
            } catch (_: Exception) {
                null
            }
        }
    }
    FrameThumbPicker(base, selected, onSelect)
}

/** Miniaturas de [base] (una foto pequeña, ~240 px) con cada marco, por categorías. */
@Composable
fun FrameThumbPicker(base: Bitmap?, selected: String, onSelect: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(selected = category.isEmpty(), onClick = { category = "" }, label = { Text("Todos") })
        Frames.categories.forEach { cat ->
            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
        }
    }
    Spacer(Modifier.height(8.dp))
    val visible = remember(category) {
        Frames.all.filter { it.key.isEmpty() || category.isEmpty() || it.category == category }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(visible.size, key = { visible[it].key }) { i ->
            val f = visible[i]
            val thumb by produceState<Bitmap?>(null, base, f.key) {
                val b = base
                value = if (b == null) null else withContext(Dispatchers.Default) {
                    try {
                        Frames.apply(b, f.key)
                    } catch (_: Throwable) {
                        b
                    }
                }
            }
            val isSel = selected == f.key
            Column(
                Modifier
                    .width(96.dp)
                    .clickable { onSelect(f.key) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            if (isSel) 3.dp else 1.dp,
                            if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = thumb
                    if (bmp == null) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = f.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(5.dp),
                        )
                    }
                }
                Text(
                    f.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Enseña la primera foto antes y después de la mejora. */
@Composable
private fun BeforeAfter(uri: Uri, enhance: Boolean, frame: String) {
    val context = LocalContext.current
    var before by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var after by remember(uri, enhance, frame) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, enhance, frame) {
        withContext(Dispatchers.Default) {
            try {
                if (before == null) before = ImageProcessor.preview(context, uri, enhance = false, maxSide = 700)
                after = ImageProcessor.preview(context, uri, enhance = enhance, maxSide = 700, frame = frame)
            } catch (_: Exception) {
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Antes" to before, "Después" to after).forEach { (label, bmp) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp == null) CircularProgressIndicator(Modifier.size(28.dp))
                    else Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
