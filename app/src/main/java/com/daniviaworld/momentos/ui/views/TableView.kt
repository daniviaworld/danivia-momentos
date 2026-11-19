package com.daniviaworld.momentos.ui.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.KeepScreenOn
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.VideoBadge
import com.daniviaworld.momentos.ui.dayLabel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private class Dropped(val seq: Int, val photo: Photo, val fx: Float, val fy: Float, val rot: Float)

/**
 * 🃏 Mesa de fotos: polaroids que van cayendo solas sobre una mesa, unas encima de otras.
 * Toca la de arriba (o OK en el mando) para verla; flecha derecha (o tocar la mesa) para que caiga otra.
 */
@Composable
fun TableView(photos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit, secondsPerPhoto: Int = 5) {
    if (photos.isEmpty()) return
    KeepScreenOn()
    var next by rememberSaveable { mutableIntStateOf(0) }
    var seq by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val pile = remember { mutableStateListOf<Dropped>() }
    val focus = remember { FocusRequester() }

    fun drop() {
        val p = photos[next.mod(photos.size)]
        next++
        val r = Random(seq * 7919 + 13)
        pile.add(Dropped(seq++, p, 0.18f + r.nextFloat() * 0.64f, 0.22f + r.nextFloat() * 0.56f, (r.nextFloat() - 0.5f) * 28f))
        if (pile.size > 7) pile.removeAt(0)
        tick++ // reinicia el temporizador
    }

    LaunchedEffect(photos) {
        pile.clear()
        repeat(minOf(3, photos.size)) { drop() }
    }
    LaunchedEffect(tick) {
        delay(secondsPerPhoto * 1000L)
        drop()
    }
    LaunchedEffect(Unit) { if (isTv) try { focus.requestFocus() } catch (_: Exception) {} }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF5A3B26), Color(0xFF3A2516), Color(0xFF1E130B)),
                ),
            )
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { drop(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { pile.lastOrNull()?.let { onOpen(it.photo) }; true }
                    else -> false
                }
            }
            .focusable()
            .clickable(enabled = !isTv) { drop() },
    ) {
        val density = LocalDensity.current
        val cardW = minOf(maxWidth * 0.46f, maxHeight * 0.6f)
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val cPx = with(density) { cardW.toPx() }
        pile.forEach { d ->
            key(d.seq) {
                val top = d == pile.last()
                val a = remember { Animatable(0f) }
                LaunchedEffect(Unit) { a.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(
                                (d.fx * wPx - cPx / 2).roundToInt(),
                                (d.fy * hPx - cPx * 0.6f).roundToInt(),
                            )
                        }
                        .width(cardW)
                        .graphicsLayer {
                            val v = a.value
                            val s = 1.6f - 0.6f * v
                            scaleX = s
                            scaleY = s
                            alpha = v
                            rotationZ = d.rot * (0.4f + 0.6f * v)
                        }
                        .shadow(if (top) 22.dp else 8.dp, RoundedCornerShape(3.dp))
                        .background(Color(0xFFFCFBF7), RoundedCornerShape(3.dp))
                        .then(if (top && !isTv) Modifier.clickable { onOpen(d.photo) } else Modifier)
                        .padding(start = cardW * 0.05f, end = cardW * 0.05f, top = cardW * 0.05f),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        PhotoImage(d.photo.url, Modifier.fillMaxSize())
                        VideoBadge(d.photo)
                    }
                    Text(
                        "${d.photo.uploaderName} · ${dayLabel(d.photo.createdAt)}",
                        fontFamily = FontFamily.Cursive,
                        fontSize = (cardW.value * 0.055f).sp,
                        color = Color(0xFF2B3A67),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = cardW * 0.035f),
                    )
                }
            }
        }
    }
}
