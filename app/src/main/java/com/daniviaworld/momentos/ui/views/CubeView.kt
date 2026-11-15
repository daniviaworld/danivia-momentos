package com.daniviaworld.momentos.ui.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.KeepScreenOn
import com.daniviaworld.momentos.ui.BackgroundMusic
import com.daniviaworld.momentos.ui.Prefetch
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.VideoBadge
import com.daniviaworld.momentos.ui.Violet
import com.daniviaworld.momentos.ui.bottomShade
import com.daniviaworld.momentos.ui.shortDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.sin

/**
 * Cubo gigante en 3D: cada cara es una foto. Gira solo cada pocos segundos,
 * alternando giro horizontal y vertical, y "respira" suavemente mientras espera.
 * En la TV: ◀ ▶ giran el cubo, OK abre la foto, ▲ vuelve a los modos de vista.
 */
@Composable
fun CubeView(photos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit, musicUri: android.net.Uri? = null) {
    KeepScreenOn()
    BackgroundMusic(musicUri)
    var index by remember { mutableIntStateOf(0) }
    var vertical by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }
    val mutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    var interaction by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    if (photos.isEmpty()) return
    val count = photos.size
    val safeIndex = index.mod(count)

    suspend fun spin(dir: Int, vert: Boolean) {
        if (count < 2) return
        mutex.withLock {
            forward = dir > 0
            vertical = vert
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
            index = (index + dir).mod(count)
            progress.snapTo(0f)
        }
    }

    // Giro automático
    LaunchedEffect(interaction, count) {
        var turn = 0
        while (true) {
            delay(4500)
            val vert = turn % 2 == 1
            // se lanza aparte para que, si tocas el mando a mitad de giro, el giro termine limpio
            scope.launch { spin(1, vert) }.join()
            turn++
        }
    }
    LaunchedEffect(Unit) {
        if (isTv) try { focus.requestFocus() } catch (_: Exception) {}
    }

    // Balanceo suave mientras está quieto
    val idle = rememberInfiniteTransition(label = "idle")
    val wobble by idle.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse),
        label = "wobble",
    )

    val p = progress.value
    val current = photos[safeIndex]
    val next = photos[(safeIndex + if (forward) 1 else -1).mod(count)]
    Prefetch(listOf(photos[(safeIndex + 1).mod(count)].url, photos[(safeIndex - 1).mod(count)].url))

    val shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp)
    val shown = if (p > 0.5f) next else current

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Violet.copy(alpha = 0.35f), Color.Transparent)))
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val action: (suspend () -> Unit)? = when (e.key) {
                    Key.DirectionRight -> ({ spin(1, false) })
                    Key.DirectionLeft -> ({ spin(-1, false) })
                    else -> null
                }
                when {
                    action != null -> {
                        interaction++
                        scope.launch { action() }
                        true
                    }
                    e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                        onOpen(photos[index.mod(count)]); true
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        // El cubo
        Box(
            Modifier
                .fillMaxSize(if (isTv) 0.78f else 0.82f)
                .graphicsLayer {
                    // se aleja un poco a mitad de giro para que se vea el volumen del cubo
                    val s = 1f - 0.2f * sin(PI * p).toFloat()
                    scaleX = s
                    scaleY = s
                    cameraDistance = 18f * density
                    rotationY = wobble * (1f - sin(PI * p).toFloat())
                }
                .then(if (isTv) Modifier else Modifier.clickable { onOpen(photos[index.mod(count)]) }),
        ) {
            // Cara que se va
            Face(
                photo = current,
                shape = shape,
                shade = p,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        cameraDistance = 18f * density
                        if (!vertical) {
                            transformOrigin = TransformOrigin(if (forward) 1f else 0f, 0.5f)
                            rotationY = (if (forward) -90f else 90f) * p
                            translationX = (if (forward) -size.width else size.width) * p
                        } else {
                            transformOrigin = TransformOrigin(0.5f, if (forward) 1f else 0f)
                            rotationX = (if (forward) 90f else -90f) * p
                            translationY = (if (forward) -size.height else size.height) * p
                        }
                    },
            )
            // Cara que llega
            if (p > 0f) {
                Face(
                    photo = next,
                    shape = shape,
                    shade = 1f - p,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            cameraDistance = 18f * density
                            if (!vertical) {
                                transformOrigin = TransformOrigin(if (forward) 0f else 1f, 0.5f)
                                rotationY = (if (forward) 90f else -90f) * (1f - p)
                                translationX = (if (forward) size.width else -size.width) * (1f - p)
                            } else {
                                transformOrigin = TransformOrigin(0.5f, if (forward) 0f else 1f)
                                rotationX = (if (forward) -90f else 90f) * (1f - p)
                                translationY = (if (forward) size.height else -size.height) * (1f - p)
                            }
                        },
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = if (isTv) 8.dp else 92.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${shown.uploaderName} · ${shortDateTime(shown.createdAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Face(photo: Photo, shape: RoundedCornerShape, shade: Float, modifier: Modifier) {
    Box(
        modifier
            .clip(shape)
            .border(2.dp, Color.White.copy(alpha = 0.25f), shape)
    ) {
        // Fondo: la misma foto ampliada y difuminada, para rellenar la cara sin bandas negras
        PhotoImage(
            photo.thumbUrl,
            Modifier
                .fillMaxSize()
                .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(30.dp) else Modifier)
                .graphicsLayer { alpha = 0.6f },
            ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        // Delante: la foto ENTERA y centrada, sin recortar caras
        PhotoImage(photo.url, Modifier.fillMaxSize(), ContentScale.Fit)
        VideoBadge(photo, big = true)
        // las caras que giran se oscurecen, como si les diera menos luz
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (shade * 0.55f).coerceIn(0f, 0.55f)))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(bottomShade)
                .padding(16.dp)
        ) {
            Text(photo.uploaderName, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
