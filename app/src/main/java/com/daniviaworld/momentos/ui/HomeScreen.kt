package com.daniviaworld.momentos.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
    val photos by vm.photos.collectAsState()
    val family by vm.family.collectAsState()
    val upload by UploadManager.progress.collectAsState()

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

    val open: (Photo) -> Unit = { vm.screen = Screen.Detail(it.id) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                title = family?.name ?: "DANIVIA Momentos",
                isTv = vm.isTv,
                count = countLabel(photos),
                onFamily = { vm.screen = Screen.FamilyInfo },
            )
            ModeSelector(vm.viewMode, vm.isTv) { vm.selectViewMode(it) }

            if (upload.total > 0) UploadBanner(upload) { UploadManager.clear() }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (photos.isEmpty()) {
                    EmptyState(vm.isTv)
                } else {
                    when (vm.viewMode) {
                        ViewMode.GRID -> GridView(photos, vm.isTv, open)
                        ViewMode.CAROUSEL -> CarouselView(photos, vm.isTv, open)
                        ViewMode.CINEMA -> CinemaView(photos, vm.isTv, open)
                        ViewMode.CUBE -> CubeView(photos, vm.isTv, open)
                        ViewMode.SLIDESHOW -> SlideshowView(photos, vm.isTv, open)
                    }
                }
            }
        }

        if (!vm.isTv) {
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
}

@Composable
private fun Header(title: String, isTv: Boolean, count: String, onFamily: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTv) 48.dp else 16.dp,
                vertical = if (isTv) 20.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "DANIVIA MOMENTOS",
                color = Orange,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                title,
                style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            count,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        if (isTv) {
            OutlinedButton(onClick = onFamily) {
                Icon(Icons.Filled.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Familia")
            }
        } else {
            IconButton(onClick = onFamily) {
                Icon(Icons.Filled.Person, contentDescription = "Familia")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(current: ViewMode, isTv: Boolean, onSelect: (ViewMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 44.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val text = when {
                    p.running && p.currentIsVideo ->
                        "Preparando y subiendo vídeo… ${p.done + p.failed + 1} de ${p.total} (puede tardar un poco)"
                    p.running -> "Mejorando y subiendo… ${p.done + p.failed + 1} de ${p.total}"
                    p.failed == 0 -> "¡Listo! ${p.done} subidos ✨"
                    else -> "Subidos ${p.done}. Fallaron ${p.failed}: " + (p.lastError ?: "revisa la conexión.")
                }
                Text(text, modifier = Modifier.weight(1f))
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
