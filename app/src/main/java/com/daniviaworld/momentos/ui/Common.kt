package com.daniviaworld.momentos.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.daniviaworld.momentos.data.MusicLibrary
import androidx.compose.runtime.LaunchedEffect
import coil.imageLoader
import coil.request.ImageRequest
import com.daniviaworld.momentos.data.Photo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val spanish = Locale("es", "ES")

@Composable
fun PhotoImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(url) {
            ImageRequest.Builder(context).data(url).crossfade(350).build()
        },
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(Color(0xFF1A1C2E)),
    )
}

/** Foto a pantalla completa sin recortes: la foto entera encima de una copia difuminada. */
@Composable
fun FullPhoto(photo: Photo, modifier: Modifier = Modifier, imageModifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        if (Build.VERSION.SDK_INT >= 31) {
            PhotoImage(
                photo.thumbUrl,
                Modifier
                    .fillMaxSize()
                    .blur(28.dp)
                    .graphicsLayer { alpha = 0.55f },
                ContentScale.Crop
            )
        }
        PhotoImage(photo.url, imageModifier.fillMaxSize(), ContentScale.Fit)
    }
}

/** Precarga fotos para que las transiciones no esperen a la red. */
@Composable
fun Prefetch(urls: List<String>) {
    val context = LocalContext.current
    DisposableEffect(urls) {
        urls.forEach { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
        onDispose { }
    }
}

/** Tarjeta que se agranda y se marca al tener el foco (mando de la TV) y se puede pulsar. */
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    focusScale: Float = 1.08f,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else Modifier.clickable(onClick = onClick)
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

val bottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))

fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ---------- Fechas ----------

fun dayKey(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
}

fun dayLabel(millis: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
    val diff = now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "Hoy"
        sameYear && diff == 1 -> "Ayer"
        sameYear -> SimpleDateFormat("EEEE d 'de' MMMM", spanish).format(c.time).replaceFirstChar { it.uppercase() }
        else -> SimpleDateFormat("d 'de' MMMM 'de' yyyy", spanish).format(c.time)
    }
}

fun monthLabel(millis: Long): String =
    SimpleDateFormat("MMMM yyyy", spanish).format(millis).replaceFirstChar { it.uppercase() }

fun shortDateTime(millis: Long): String =
    "${dayLabel(millis)} · ${SimpleDateFormat("HH:mm", spanish).format(millis)}"

// ---------- Vídeo ----------

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Etiqueta ▶ 0:35 que se pone encima de las miniaturas de vídeo. */
@Composable
fun BoxScope.VideoBadge(photo: Photo, big: Boolean = false) {
    if (!photo.isVideo) return
    Row(
        Modifier
            .align(if (big) Alignment.Center else Alignment.BottomStart)
            .padding(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xAA000000))
            .padding(horizontal = if (big) 14.dp else 6.dp, vertical = if (big) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Vídeo",
            tint = Color.White,
            modifier = Modifier.size(if (big) 40.dp else 16.dp),
        )
        if (photo.durationMs > 0) {
            Text(
                formatDuration(photo.durationMs),
                color = Color.White,
                fontSize = if (big) 18.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Reproductor de vídeo (ExoPlayer). En el móvil con controles táctiles; en la TV se controla con el mando
 * desde la pantalla que lo contiene (onPlayer da acceso al reproductor).
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    showControls: Boolean = true,
    onEnded: (() -> Unit)? = null,
    onPlayer: ((ExoPlayer?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = autoPlay
        }
    }
    val endedCallback by rememberUpdatedState(onEnded)
    val playerCallback by rememberUpdatedState(onPlayer)
    KeepScreenOn()
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) endedCallback?.invoke()
            }
        }
        player.addListener(listener)
        playerCallback?.invoke(player)
        onDispose {
            player.removeListener(listener)
            playerCallback?.invoke(null)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = showControls
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view ->
            view.player = player
            view.useController = showControls
        },
        modifier = modifier,
    )
}

// ---------- Música ----------

/** Música de fondo en bucle (pistas de MusicLibrary). [play] = false la pausa (p. ej. mientras suena un vídeo). */
@Composable
fun BackgroundMusic(trackKey: String, play: Boolean = true, volume: Float = 0.55f) {
    val track = MusicLibrary.byKey(trackKey) ?: return
    val context = LocalContext.current
    val player = remember(track.key) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(MusicLibrary.uri(context, track)))
            repeatMode = Player.REPEAT_MODE_ALL
            this.volume = volume
            prepare()
        }
    }
    LaunchedEffect(player, play) { player.playWhenReady = play }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
}
