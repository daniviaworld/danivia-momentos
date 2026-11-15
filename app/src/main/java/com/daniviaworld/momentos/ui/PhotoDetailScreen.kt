package com.daniviaworld.momentos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import com.daniviaworld.momentos.data.Comment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.daniviaworld.momentos.data.Photo
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch

/** Foto a pantalla completa. Desliza (o ◀ ▶ en la TV) para pasar; doble toque para ampliar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoDetailScreen(vm: MainViewModel, photos: List<Photo>, startIndex: Int) {
    val pager = rememberPagerState(initialPage = startIndex) { photos.size }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var toDelete by remember { mutableStateOf<Photo?>(null) }
    var showUi by remember { mutableStateOf(true) }
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var commentsOpen by remember { mutableStateOf(false) }
    var albumPickFor by remember { mutableStateOf<Photo?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }

    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) {} }
    val current = photos.getOrNull(pager.currentPage)
    Prefetch(listOfNotNull(photos.getOrNull(pager.currentPage + 1)?.url, photos.getOrNull(pager.currentPage - 1)?.url))

    // Comentarios de la foto que se está viendo (en directo)
    val currentId = current?.id
    DisposableEffect(currentId) {
        comments = emptyList()
        val reg = currentId?.let { id -> vm.listenComments(id) { comments = it } }
        onDispose { reg?.remove() }
    }
    BackHandler(enabled = commentsOpen) { commentsOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> {
                        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(photos.size - 1)) }; true
                    }
                    Key.DirectionLeft -> {
                        scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        val p = activePlayer
                        if (p != null) p.playWhenReady = !p.playWhenReady else showUi = !showUi
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val item = photos[page]
            if (item.isVideo && item.videoUrl.isNotBlank()) {
                if (page == pager.settledPage) {
                    // Sólo la página visible tiene reproductor (ahorra memoria y datos)
                    VideoPlayer(
                        url = item.videoUrl,
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = true,
                        showControls = !vm.isTv,
                        onPlayer = { activePlayer = it },
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        FullPhoto(item, Modifier.fillMaxSize())
                        VideoBadge(item, big = true)
                    }
                }
            } else {
                ZoomablePhoto(item, enabled = !vm.isTv) { showUi = !showUi }
            }
        }

        if (showUi && current != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.screen = Screen.Home }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        current.uploaderName + (vm.albumOf(current)?.let { "  ·  ${it.title}" } ?: ""),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // en el móvil el contador va aquí para no amontonar la barra
                        (if (vm.isTv) "" else "${pager.currentPage + 1}/${photos.size}  ·  ") +
                            shortDateTime(current.createdAt) +
                            (if (current.isVideo) "  ·  ${formatDuration(current.durationMs)}" else "") +
                            (if (current.enhanced) "  ·  ✨" else ""),
                        color = Color(0xFFDDDDDD),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (vm.isTv) {
                    Text(
                        "${pager.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                if (!vm.isTv) {
                    if (vm.sharingId == current.id) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp),
                        )
                    } else {
                        IconButton(onClick = { vm.share(current) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartir", tint = Color.White)
                        }
                    }
                }
                if (!vm.isTv && vm.isAdmin()) {
                    IconButton(onClick = { albumPickFor = current }) {
                        Text("🗂️", fontSize = 20.sp)
                    }
                }
                if (!vm.isTv && vm.canDelete(current)) {
                    IconButton(onClick = { toDelete = current }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = Color.White)
                    }
                }
            }

            SocialBar(
                vm = vm,
                photo = current,
                comments = comments,
                onOpenComments = { commentsOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // en los vídeos del móvil dejamos sitio a los controles del reproductor
                    .padding(bottom = if (current.isVideo && !vm.isTv) 72.dp else 0.dp),
            )
        }

        if (commentsOpen && current != null && !vm.isTv) {
            CommentsPanel(
                vm = vm,
                photo = current,
                comments = comments,
                onClose = { commentsOpen = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    albumPickFor?.let { photo ->
        AlbumPickerDialog(
            vm = vm,
            photoIds = listOf(photo.id),
            currentAlbumId = photo.albumId,
            onDismiss = { albumPickFor = null },
        )
    }

    toDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (photo.isVideo) "¿Borrar este vídeo?" else "¿Borrar esta foto?") },
            text = { Text("Se borrará para toda la familia.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePhoto(photo)
                    toDelete = null
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ZoomablePhoto(photo: Photo, enabled: Boolean, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val gestures = if (!enabled) Modifier else Modifier
        .pointerInput(photo.id) {
            detectTapGestures(
                onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f; offset = Offset.Zero
                    } else scale = 2.5f
                },
                onTap = { onTap() },
            )
        }
        .then(
            // Sólo capturamos gestos de zoom cuando ya está ampliada, para no bloquear el deslizar entre fotos
            if (scale > 1f) Modifier.pointerInput(photo.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            } else Modifier
        )

    FullPhoto(
        photo,
        modifier = Modifier
            .fillMaxSize()
            .then(gestures),
        imageModifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        },
    )
}
