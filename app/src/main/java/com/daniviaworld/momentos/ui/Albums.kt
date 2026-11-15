package com.daniviaworld.momentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniviaworld.momentos.data.Album
import com.daniviaworld.momentos.data.MusicLibrary
import com.daniviaworld.momentos.data.FamilySong
import com.daniviaworld.momentos.data.Photo

val albumEmojis = listOf(
    "📸", "🐬", "🎢", "🏖️", "🏔️", "🎂", "🎄", "🎃", "🎉", "⚽",
    "🏫", "✈️", "🚗", "🐶", "🌸", "☀️", "❄️", "❤️", "👨‍👩‍👧‍👧", "⭐",
)

/** Pantalla de álbumes: portadas, y para el admin crear, renombrar y borrar. */
@Composable
fun AlbumsScreen(vm: MainViewModel) {
    val albums by vm.albums.collectAsState()
    val photos by vm.photos.collectAsState()
    val songs by vm.songs.collectAsState()
    val admin = vm.isAdmin()
    var editing by remember { mutableStateOf<Album?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Album?>(null) }
    var videoFor by remember { mutableStateOf<AlbumCardData?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (vm.isTv) 40.dp else 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.screen = Screen.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Álbumes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (admin && !vm.isTv) {
                Button(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nuevo")
                }
            }
        }

        if (admin && !vm.isTv) {
            Text(
                "Para organizar: en Cuadrícula mantén pulsada una foto, marca varias y pulsa «Mover a álbum».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        val cards = remember(albums, photos) {
            buildList {
                albums.forEach { a -> add(AlbumCardData(a, photos.filter { it.albumId == a.id })) }
                val loose = photos.filter { it.albumId == null }
                if (loose.isNotEmpty()) add(AlbumCardData(null, loose))
            }
        }

        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (admin) "Todavía no hay álbumes. Pulsa «Nuevo» para crear el primero."
                    else "Todavía no hay álbumes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (vm.isTv) 260.dp else 160.dp),
                contentPadding = PaddingValues(if (vm.isTv) 40.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cards, key = { it.album?.id ?: "none" }) { card ->
                    Column {
                        FocusCard(
                            onClick = { vm.openAlbum(card.album?.id ?: NO_ALBUM) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.2f),
                        ) {
                            val cover = card.photos.firstOrNull()
                            if (cover != null) PhotoImage(cover.thumbUrl, Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize().background(NightCard))
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(bottomShade)
                            )
                            Column(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    card.album?.title ?: "📂 Sin álbum",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (card.photos.size == 1) "1 elemento" else "${card.photos.size} elementos",
                                    color = Color(0xFFDDDDDD),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (!vm.isTv) {
                            // Acciones del álbum: crear vídeo, collage, libro y (admin) editar/borrar
                            var menu by remember { mutableStateOf(false) }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { videoFor = card }) { Text("🎬 Vídeo") }
                                Box {
                                    IconButton(onClick = { menu = true }) { Text("⋯", fontSize = 22.sp) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("🎬  Crear vídeo con música") },
                                            onClick = { menu = false; videoFor = card },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("🧩  Hacer un collage") },
                                            onClick = { menu = false; vm.makeCollage(card.album) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📖  Libro de fotos (PDF)") },
                                            onClick = { menu = false; vm.makeBook(card.album) },
                                        )
                                        if (admin && card.album != null) {
                                            DropdownMenuItem(
                                                text = { Text("✏️  Editar álbum") },
                                                onClick = { menu = false; editing = card.album },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("🗑  Borrar álbum") },
                                                onClick = { menu = false; deleting = card.album },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    videoFor?.let { card ->
        CreateVideoDialog(
            album = card.album,
            photoCount = card.photos.count { !it.isVideo },
            songs = songs,
            onDismiss = { videoFor = null },
            onCreate = { music, seconds ->
                videoFor = null
                vm.makeVideo(card.album, music, seconds)
            },
        )
    }

    if (creating) {
        AlbumEditDialog(
            title = "Nuevo álbum",
            initialName = "",
            initialEmoji = "📸",
            songs = songs,
            onDismiss = { creating = false },
            onSave = { name, emoji, music ->
                vm.createAlbum(name, emoji, music = music)
                creating = false
            },
        )
    }
    editing?.let { album ->
        AlbumEditDialog(
            title = "Editar álbum",
            initialName = album.name,
            initialEmoji = album.emoji,
            initialMusic = album.music,
            songs = songs,
            onDismiss = { editing = null },
            onSave = { name, emoji, music ->
                vm.updateAlbum(album, name, emoji, music)
                editing = null
            },
        )
    }
    deleting?.let { album ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar «${album.title}»?") },
            text = { Text("Las fotos y vídeos NO se borran: pasan a «Sin álbum».") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAlbum(album)
                    deleting = null
                }) { Text("Borrar álbum", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

private data class AlbumCardData(val album: Album?, val photos: List<Photo>)

/** Diálogo para crear o renombrar un álbum (nombre + emoji). */
@Composable
fun AlbumEditDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    onDismiss: () -> Unit,
    initialMusic: String = "",
    songs: List<FamilySong> = emptyList(),
    onSave: (name: String, emoji: String, music: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji.ifBlank { "📸" }) }
    var music by remember { mutableStateOf(initialMusic) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nombre") },
                    placeholder = { Text("Oceanogràfic") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Icono", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    albumEmojis.forEach { e ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (e == emoji) Orange.copy(alpha = 0.3f) else Color.Transparent)
                                .then(if (e == emoji) Modifier.border(2.dp, Orange, CircleShape) else Modifier)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Música del álbum", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                MusicChips(selected = music, onSelect = { music = it }, songs = songs)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, emoji, music) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Elegir a qué álbum mover unas fotos. Incluye "Sin álbum" y "Nuevo álbum…".
 * [currentAlbumId] marca el álbum actual (si es una sola foto).
 */
@Composable
fun AlbumPickerDialog(
    vm: MainViewModel,
    photoIds: List<String>,
    currentAlbumId: String?,
    onDismiss: () -> Unit,
) {
    val albums by vm.albums.collectAsState()
    val songs by vm.songs.collectAsState()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        AlbumEditDialog(
            title = "Nuevo álbum",
            initialName = "",
            initialEmoji = "📸",
            songs = songs,
            onDismiss = { creating = false },
            onSave = { name, emoji, music ->
                vm.createAlbum(name, emoji, photoIds, music)
                creating = false
                onDismiss()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (photoIds.size == 1) "Mover a álbum" else "Mover ${photoIds.size} a álbum") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    PickerRow("➕  Nuevo álbum…", selected = false, highlight = true) { creating = true }
                }
                items(albums, key = { it.id }) { a ->
                    PickerRow(a.title, selected = a.id == currentAlbumId) {
                        vm.movePhotos(photoIds, a.id)
                        onDismiss()
                    }
                }
                item {
                    PickerRow("📂  Sin álbum", selected = currentAlbumId == null && photoIds.size == 1) {
                        vm.movePhotos(photoIds, null)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PickerRow(text: String, selected: Boolean, highlight: Boolean = false, onClick: () -> Unit) {
    Text(
        text + if (selected) "   ✓" else "",
        color = if (highlight) Orange else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected || highlight) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}

/** Fila de chips para ver "Todas", cada álbum o "Sin álbum". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumFilterRow(vm: MainViewModel, albums: List<Album>, photos: List<Photo>) {
    if (albums.isEmpty()) return
    val hasLoose = photos.any { it.albumId == null }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (vm.isTv) 44.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val options = buildList {
            add(ALL_ALBUMS to "Todas")
            albums.forEach { add(it.id to it.title) }
            if (hasLoose) add(NO_ALBUM to "📂 Sin álbum")
        }
        options.forEach { (id, label) ->
            FilterChip(
                selected = vm.albumFilter == id,
                onClick = { vm.selectAlbumFilter(id) },
                label = { Text(label, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Violet,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** Chips para elegir una pista de música ("" = sin música). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicChips(selected: String, onSelect: (String) -> Unit, songs: List<FamilySong> = emptyList()) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(selected = selected.isBlank(), onClick = { onSelect("") }, label = { Text("🔇 Sin música") })
        // primero las canciones de la familia (las de verdad), luego las propias de la app
        songs.forEach { s ->
            val key = MusicLibrary.familyKey(s)
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text("🎵 ${s.name}", maxLines = 1) },
            )
        }
        MusicLibrary.tracks.forEach { t ->
            FilterChip(selected = selected == t.key, onClick = { onSelect(t.key) }, label = { Text(t.label) })
        }
    }
}
