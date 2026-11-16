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
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import com.daniviaworld.momentos.data.ViewMode
import kotlin.math.abs
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.pager.PagerState
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
    var editFor by remember { mutableStateOf<Photo?>(null) }
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
                        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(photos.size - 1), animationSpec = transitionSpec(vm.viewMode)) }; true
                    }
                    Key.DirectionLeft -> {
                        scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0), animationSpec = transitionSpec(vm.viewMode)) }; true
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
        val transition = vm.viewMode
        var zoomed by remember { mutableStateOf(false) }
        LaunchedEffect(pager.settledPage) { zoomed = false }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed, // con la foto ampliada, el dedo mueve la foto y no cambia de foto
            // al soltar el dedo la foto termina de pasar despacio, para que se vea el efecto (cubo, cine…)
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager,
                snapAnimationSpec = transitionSpec(vm.viewMode),
            ),
        ) { page ->
            val item = photos[page]
            Box(Modifier.fillMaxSize().pageTransition(pager, page, transition)) {
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
                ZoomablePhoto(item, enabled = !vm.isTv, onZoomed = { zoomed = it }) { showUi = !showUi }
            }
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
                if (vm.canEdit(current) && (!current.isVideo || current.videoUrl.isNotBlank())) {
                    IconButton(onClick = { editFor = current }) {
                        Text(if (current.isVideo) "🎵" else "✏️", fontSize = 20.sp)
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

    editFor?.let { photo ->
        // la versión al día de la foto (por si alguien la ha cambiado mientras)
        val live = photos.firstOrNull { it.id == photo.id } ?: photo
        if (live.isVideo) VideoMusicDialog(vm, live) { editFor = null }
        else PhotoEditDialog(vm, live) { editFor = null }
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
private fun ZoomablePhoto(photo: Photo, enabled: Boolean, onZoomed: (Boolean) -> Unit = {}, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale > 1f) { onZoomed(scale > 1f) }

    val gestures = if (!enabled) Modifier else Modifier
        .pointerInput(photo.id) {
            detectTapGestures(
                onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f; offset = Offset.Zero
                    } else {
                        // amplía hacia donde has tocado
                        scale = 2.5f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        offset = clampOffset((center - tap) * 1.5f, 2.5f, size.width, size.height)
                    }
                },
                onTap = { onTap() },
            )
        }
        .pointerInput(photo.id) {
            // Pellizcar con dos dedos amplía siempre. Con un dedo y sin zoom no se toca nada,
            // así se sigue pudiendo deslizar entre fotos; ya ampliada, un dedo mueve la foto.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val fingers = event.changes.count { it.pressed }
                    if (fingers >= 2 || scale > 1f) {
                        val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                        scale = newScale
                        offset = if (newScale <= 1f) Offset.Zero
                        else clampOffset(offset + event.calculatePan(), newScale, size.width, size.height)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (scale < 1.05f) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }
        }

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

/**
 * Forma de pasar de foto según el modo de vista elegido. Sólo afecta al movimiento:
 * con la foto quieta (desplazamiento 0) todo vuelve a su tamaño normal.
 */
private fun Modifier.pageTransition(pager: PagerState, page: Int, mode: ViewMode): Modifier =
    this
        .graphicsLayer {
            // > 0: la página se va hacia la izquierda; < 0: llega desde la derecha
            val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
            val a = abs(o)
            when (mode) {
                ViewMode.CUBE -> {
                    // cara de un cubo que gira sobre su arista
                    cameraDistance = 14f * density
                    transformOrigin = TransformOrigin(if (o > 0f) 1f else 0f, 0.5f)
                    rotationY = -90f * o
                }
                ViewMode.CINEMA -> {
                    // la foto que se va se queda detrás, se aleja y se apaga; la nueva entra por encima
                    if (o > 0f) {
                        translationX = size.width * o
                        val sc = 1f - 0.18f * o
                        scaleX = sc
                        scaleY = sc
                        alpha = 1f - 0.7f * o
                    }
                }
                ViewMode.CAROUSEL -> {
                    // carrusel: las fotos se inclinan y encogen al pasar
                    cameraDistance = 14f * density
                    rotationY = 35f * o
                    val sc = 1f - 0.22f * a
                    scaleX = sc
                    scaleY = sc
                }
                ViewMode.SLIDESHOW -> {
                    // fundido con un ligero zoom, como en las diapositivas
                    translationX = size.width * o
                    alpha = 1f - a
                    val sc = 1f + 0.08f * a
                    scaleX = sc
                    scaleY = sc
                }
                else -> Unit // cuadrícula: deslizamiento normal
            }
        }
        .drawWithContent {
            drawContent()
            if (mode == ViewMode.CUBE) {
                val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                // la cara que gira recibe menos luz
                drawRect(Color.Black.copy(alpha = abs(o) * 0.5f))
            }
        }

/** Que la foto ampliada no se salga de la pantalla al moverla. */
private fun clampOffset(o: Offset, scale: Float, width: Int, height: Int): Offset {
    val maxX = width * (scale - 1f) / 2f
    val maxY = height * (scale - 1f) / 2f
    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
}

/** Duración del paso de una foto a otra: lo bastante lento para que se aprecie el efecto del modo. */
private fun transitionSpec(mode: ViewMode): AnimationSpec<Float> = when (mode) {
    ViewMode.CUBE -> tween(durationMillis = 900, easing = FastOutSlowInEasing)
    ViewMode.CINEMA -> tween(durationMillis = 850, easing = FastOutSlowInEasing)
    ViewMode.CAROUSEL -> tween(durationMillis = 750, easing = FastOutSlowInEasing)
    ViewMode.SLIDESHOW -> tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
    else -> tween(durationMillis = 450, easing = FastOutSlowInEasing)
}
