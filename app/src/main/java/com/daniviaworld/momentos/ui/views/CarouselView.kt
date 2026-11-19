package com.daniviaworld.momentos.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.SocialBadge
import com.daniviaworld.momentos.ui.VideoBadge
import com.daniviaworld.momentos.ui.Prefetch
import com.daniviaworld.momentos.ui.bottomShade
import com.daniviaworld.momentos.ui.lerpF
import com.daniviaworld.momentos.ui.shortDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Carrusel: la foto central grande y las de los lados más pequeñas; avanza sola. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselView(photos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit) {
    val pager = rememberPagerState { photos.size }
    val scope = rememberCoroutineScope()
    var interaction by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    // Avance automático cada 5 s (se reinicia cuando tocas o usas el mando)
    LaunchedEffect(interaction, photos.size) {
        if (photos.size < 2) return@LaunchedEffect
        while (true) {
            delay(5000)
            pager.animateScrollToPage((pager.currentPage + 1) % photos.size)
        }
    }
    LaunchedEffect(Unit) {
        if (isTv) try { focus.requestFocus() } catch (_: Exception) {}
    }

    val current = photos.getOrNull(pager.currentPage)
    Prefetch(listOfNotNull(photos.getOrNull(pager.currentPage + 1)?.url, photos.getOrNull(pager.currentPage + 2)?.url))

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> {
                        interaction++
                        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(photos.size - 1)) }
                        true
                    }
                    Key.DirectionLeft -> {
                        interaction++
                        scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        photos.getOrNull(pager.currentPage)?.let(onOpen); true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = if (isTv) 240.dp else 44.dp),
            pageSpacing = if (isTv) 24.dp else 12.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val photo = photos[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val scale = lerpF(1f, 0.8f, offset)
            Box(
                Modifier
                    .fillMaxHeight(0.92f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = lerpF(1f, 0.45f, offset)
                        rotationY = ((page - pager.currentPage) - pager.currentPageOffsetFraction) * -10f
                        cameraDistance = 14f * density
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .then(if (isTv) Modifier else Modifier.clickable { interaction++; onOpen(photo) }),
            ) {
                PhotoImage(photo.url, Modifier.fillMaxSize(), sharp = isTv)
                VideoBadge(photo, big = true)
                SocialBadge(photo)
            }
        }

        if (current != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(bottomShade)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.uploaderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${shortDateTime(current.createdAt)}   ·   ${pager.currentPage + 1} / ${photos.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(if (isTv) 16.dp else 88.dp))
    }
}
