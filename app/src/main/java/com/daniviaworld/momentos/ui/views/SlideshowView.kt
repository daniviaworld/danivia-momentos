package com.daniviaworld.momentos.ui.views

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.KeepScreenOn
import com.daniviaworld.momentos.ui.BackgroundMusic
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.VideoPlayer
import com.daniviaworld.momentos.ui.Prefetch
import com.daniviaworld.momentos.ui.bottomShade
import com.daniviaworld.momentos.ui.shortDateTime
import kotlinx.coroutines.delay

/** Pase de diapositivas con fundido y efecto "Ken Burns" (zoom y desplazamiento lentos). */
@Composable
fun SlideshowView(photos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit, musicUri: android.net.Uri? = null) {
    KeepScreenOn()
    var index by remember { mutableIntStateOf(0) }
    var interaction by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val count = photos.size

    // Las fotos duran 7 s; los vídeos se ven enteros y al acabar pasa al siguiente
    LaunchedEffect(index, interaction, count) {
        if (count < 2) return@LaunchedEffect
        val cur = photos[index.mod(count)]
        if (cur.isVideo) {
            delay(cur.durationMs.coerceAtLeast(3000L) + 20_000L) // seguridad por si el vídeo no avisa del final
        } else {
            delay(7000)
        }
        index = (index + 1).mod(count)
    }
    LaunchedEffect(Unit) {
        if (isTv) try { focus.requestFocus() } catch (_: Exception) {}
    }
    val safeIndex = index.mod(count.coerceAtLeast(1))
    // la música se pausa mientras suena un vídeo
    BackgroundMusic(musicUri, play = photos.getOrNull(safeIndex)?.isVideo != true)
    Prefetch(listOfNotNull(photos.getOrNull((safeIndex + 1).mod(count.coerceAtLeast(1)))?.url))

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { index = (index + 1).mod(count); interaction++; true }
                    Key.DirectionLeft -> { index = (index - 1).mod(count); interaction++; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        photos.getOrNull(safeIndex)?.let(onOpen); true
                    }
                    else -> false
                }
            }
            .focusable()
            .then(if (isTv) Modifier else Modifier.clickable { photos.getOrNull(safeIndex)?.let(onOpen) }),
    ) {
        val photo = photos.getOrNull(safeIndex) ?: return@Box
        Crossfade(targetState = photo, animationSpec = tween(1600), label = "slide") { p ->
            if (p.isVideo && p.videoUrl.isNotBlank()) {
                VideoPlayer(
                    url = p.videoUrl,
                    modifier = Modifier.fillMaxSize(),
                    autoPlay = true,
                    showControls = false,
                    onEnded = { if (count > 1) index = (index + 1).mod(count) },
                )
            } else {
                KenBurns(p, seed = photos.indexOf(p))
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(bottomShade)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (isTv) 20.dp else 96.dp)
        ) {
            Text(photo.uploaderName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(shortDateTime(photo.createdAt), color = Color(0xFFDDDDDD))
        }
    }
}

@Composable
private fun KenBurns(photo: Photo, seed: Int) {
    val anim = remember(photo.id) { Animatable(0f) }
    LaunchedEffect(photo.id) {
        anim.animateTo(1f, tween(9000, easing = LinearEasing))
    }
    val t = anim.value
    val zoomIn = seed % 2 == 0
    val dirX = if (seed % 3 == 0) 1f else -1f
    val dirY = if (seed % 4 < 2) 1f else -1f
    Box(Modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= 31) {
            PhotoImage(
                photo.thumbUrl,
                Modifier
                    .fillMaxSize()
                    .blur(30.dp)
                    .graphicsLayer { alpha = 0.5f },
            )
        }
        PhotoImage(
            photo.url,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (zoomIn) 1.0f + 0.12f * t else 1.12f - 0.12f * t
                    scaleX = s
                    scaleY = s
                    translationX = dirX * 0.03f * size.width * (t - 0.5f)
                    translationY = dirY * 0.03f * size.height * (t - 0.5f)
                },
            ContentScale.Fit,
        )
    }
}
