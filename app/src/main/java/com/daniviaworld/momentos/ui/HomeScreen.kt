package com.daniviaworld.momentos.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.daniviaworld.momentos.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.data.UploadManager
import com.daniviaworld.momentos.data.ViewMode
import com.daniviaworld.momentos.ui.views.CarouselView
import com.daniviaworld.momentos.ui.views.CinemaView
import com.daniviaworld.momentos.ui.views.CubeView
import com.daniviaworld.momentos.ui.views.GridView
import com.daniviaworld.momentos.ui.views.SlideshowView

@Composable
fun HomeScreen(vm: MainViewModel) {
    val allPhotos by vm.photos.collectAsState()
    val albums by vm.albums.collectAsState()
    val family by vm.family.collectAsState()
    val upload by UploadManager.progress.collectAsState()
    val photos = remember(allPhotos, vm.albumFilter) { vm.filterPhotos(allPhotos) }
    var picking by remember { mutableStateOf(false) }
    val canSelect = vm.isAdmin() && !vm.isTv

    // Permiso de notificaciones (Android 13+) para avisar de fotos nuevas
    if (!vm.isTv && Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (!vm.notificationsAsked) {
                vm.notificationsAsked = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val open: (Photo) -> Unit = {
        if (vm.selecting) vm.toggleSelected(it) else vm.screen = Screen.Detail(it.id)
    }
    BackHandler(enabled = vm.selecting) { vm.clearSelection() }

    // TV: "Atrás" en la pantalla principal lleva a la fila de modos (nunca te quedas atrapado en una vista)
    val modesFocus = remember { FocusRequester() }
    var modesHaveFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = vm.isTv && !modesHaveFocus) {
        try {
            modesFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (vm.selecting) {
                SelectionBar(
                    count = vm.selectedIds.size,
                    onClose = { vm.clearSelection() },
                    onMove = { picking = true },
                )
            } else if (vm.isTv) {
                Header(
                    title = family?.name ?: "DANIVIA Momentos",
                    isTv = true,
                    count = countLabel(allPhotos),
                    onFamily = { vm.screen = Screen.FamilyInfo },
                    onAlbums = { vm.screen = Screen.Albums },
                )
                ModeSelector(
                    current = vm.viewMode,
                    isTv = true,
                    focusRequester = modesFocus,
                    onFocusChange = { modesHaveFocus = it },
                ) { vm.selectViewMode(it) }
            } else {
                // Móvil: una sola barra compacta para que las fotos tengan todo el protagonismo
                PhoneTopBar(
                    title = family?.name ?: "DANIVIA Momentos",
                    count = countLabel(allPhotos),
                    mode = vm.viewMode,
                    onMode = { vm.selectViewMode(it) },
                    onAlbums = { vm.screen = Screen.Albums },
                    onFamily = { vm.screen = Screen.FamilyInfo },
                )
            }

            AlbumFilterRow(vm, albums, allPhotos)

            if (upload.total > 0) UploadBanner(upload) { UploadManager.clear() }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (photos.isEmpty()) {
                    if (allPhotos.isEmpty()) EmptyState(vm.isTv)
                    else Text(
                        "Este álbum está vacío",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                } else {
                    when (vm.viewMode) {
                        ViewMode.GRID -> GridView(
                            photos = photos,
                            isTv = vm.isTv,
                            onOpen = open,
                            selectedIds = vm.selectedIds,
                            onLongPress = if (canSelect) ({ p: Photo -> vm.toggleSelected(p) }) else null,
                        )
                        ViewMode.CAROUSEL -> CarouselView(photos, vm.isTv, open)
                        ViewMode.CINEMA -> CinemaView(
                            photos,
                            vm.isTv,
                            open,
                            albums = if (vm.albumFilter == ALL_ALBUMS) albums else emptyList(),
                        )
                        ViewMode.CUBE -> CubeView(photos, vm.isTv, open, musicKey = vm.musicKey(albums))
                        ViewMode.SLIDESHOW -> SlideshowView(photos, vm.isTv, open, musicKey = vm.musicKey(albums))
                    }
                }
            }
        }

        if (!vm.isTv && !vm.selecting) {
            ExtendedFloatingActionButton(
                onClick = { vm.screen = Screen.Upload },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Subir") },
                containerColor = Orange,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }

    if (picking) {
        AlbumPickerDialog(
            vm = vm,
            photoIds = vm.selectedIds.toList(),
            currentAlbumId = null,
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onMove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancelar selección") }
        Text(
            if (count == 1) "1 seleccionada" else "$count seleccionadas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onMove) { Text("Mover a álbum") }
    }
}

@Composable
private fun Header(title: String, isTv: Boolean, count: String, onFamily: () -> Unit, onAlbums: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTv) 48.dp else 16.dp,
                vertical = if (isTv) 20.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_momentos),
            contentDescription = null,
            modifier = Modifier.size(if (isTv) 56.dp else 40.dp),
        )
        Spacer(Modifier.width(if (isTv) 16.dp else 10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                count,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isTv) {
            OutlinedButton(onClick = onAlbums) { Text("🗂️  Álbumes") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onFamily) {
                Icon(Icons.Filled.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Familia")
            }
        } else {
            IconButton(onClick = onAlbums) { Text("🗂️", fontSize = 22.sp) }
            IconButton(onClick = onFamily) {
                Icon(Icons.Filled.Person, contentDescription = "Familia")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneTopBar(
    title: String,
    count: String,
    mode: ViewMode,
    onMode: (ViewMode) -> Unit,
    onAlbums: () -> Unit,
    onFamily: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_momentos),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                count,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Botón de modo de vista: icono del modo actual + menú
        Box {
            Surface(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mode.icon, fontSize = 17.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("▾", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Text(
                    "Ver como",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                ViewMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${m.icon}   ${m.label}",
                                fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal,
                                color = if (m == mode) Orange else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onMode(m)
                            menuOpen = false
                        },
                    )
                }
            }
        }
        IconButton(onClick = onAlbums) { Text("🗂️", fontSize = 20.sp) }
        IconButton(onClick = onFamily) { Icon(Icons.Filled.Person, contentDescription = "Familia") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    current: ViewMode,
    isTv: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onSelect: (ViewMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.hasFocus) }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 44.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onSelect(mode) },
                modifier = if (mode == current) Modifier.focusRequester(focusRequester) else Modifier,
                label = { Text("${mode.icon}  ${mode.label}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Orange,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun UploadBanner(p: UploadManager.Progress, onClose: () -> Unit) {
    if (p.running) KeepScreenOn()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val text = when {
                    p.running && p.currentIsVideo ->
                        "Preparando y subiendo vídeo… ${p.done + p.failed + 1} de ${p.total} (puede tardar un poco)"
                    p.running -> "Mejorando y subiendo… ${p.done + p.failed + 1} de ${p.total}"
                    p.failed == 0 -> "¡Listo! ${p.done} subidos ✨"
                    else -> "Subidos ${p.done}. Fallaron ${p.failed}: " + (p.lastError ?: "revisa la conexión.")
                }
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (!p.running) TextButton(onClick = onClose) { Text("Cerrar") }
            }
            if (p.running) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (p.done + p.failed).toFloat() / p.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isTv: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📷", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Todavía no hay fotos ni vídeos",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isTv) "Subidlas desde el móvil con DANIVIA Momentos y aparecerán aquí al momento."
            else "Pulsa «Subir» y elige fotos o vídeos de tu galería.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Relleno estándar para las vistas. */
fun viewPadding(isTv: Boolean) = if (isTv) PaddingValues(horizontal = 48.dp, vertical = 16.dp)
else PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp)

private fun countLabel(items: List<Photo>): String {
    val videos = items.count { it.isVideo }
    val photos = items.size - videos
    val p = if (photos == 1) "1 foto" else "$photos fotos"
    return if (videos == 0) p else p + " · " + (if (videos == 1) "1 vídeo" else "$videos vídeos")
}
