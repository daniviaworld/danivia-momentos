package com.daniviaworld.momentos.ui.views

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.FocusCard
import com.daniviaworld.momentos.ui.Night
import com.daniviaworld.momentos.ui.Orange
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.SocialBadge
import com.daniviaworld.momentos.ui.VideoBadge
import com.daniviaworld.momentos.ui.monthLabel
import com.daniviaworld.momentos.ui.shortDateTime
import kotlinx.coroutines.delay
import java.util.Calendar

private data class Shelf(val title: String, val photos: List<Photo>)

/** Estilo plataforma de cine: portada grande arriba y filas de fotos que se recorren de lado. */
@Composable
fun CinemaView(photos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit) {
    val shelves = remember(photos) { buildShelves(photos) }
    val heroPhotos = remember(photos) { photos.take(6) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (isTv) 32.dp else 96.dp),
    ) {
        item(key = "hero") { Hero(heroPhotos, isTv, onOpen) }
        shelves.forEach { shelf ->
            item(key = "s_${shelf.title}") {
                Column(Modifier.padding(top = if (isTv) 22.dp else 16.dp)) {
                    Text(
                        shelf.title,
                        style = if (isTv) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = if (isTv) 48.dp else 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 10.dp),
                    ) {
                        items(shelf.photos, key = { "${shelf.title}_${it.id}" }) { photo ->
                            FocusCard(
                                onClick = { onOpen(photo) },
                                modifier = Modifier
                                    .width(if (isTv) 280.dp else 160.dp)
                                    .aspectRatio(16f / 10f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                PhotoImage(photo.thumbUrl, Modifier.fillMaxSize())
                                VideoBadge(photo)
                                SocialBadge(photo)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(heroPhotos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(heroPhotos.size) {
        if (heroPhotos.size < 2) return@LaunchedEffect
        while (true) {
            delay(7000)
            index = (index + 1) % heroPhotos.size
        }
    }
    val safeIndex = if (heroPhotos.isEmpty()) 0 else index % heroPhotos.size
    val photo = heroPhotos.getOrNull(safeIndex) ?: return

    Box(
        Modifier
            .fillMaxWidth()
            .height(if (isTv) 380.dp else 260.dp)
    ) {
        Crossfade(targetState = photo, animationSpec = tween(1200), label = "hero") { p ->
            PhotoImage(p.url, Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Night.copy(alpha = 0.85f), Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Night)))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (isTv) 48.dp else 16.dp, bottom = 20.dp, end = 16.dp)
        ) {
            Text("LO ÚLTIMO", color = Orange, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(
                (if (photo.isVideo) "Vídeo de " else "Foto de ") + photo.uploaderName,
                fontSize = if (isTv) 40.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(shortDateTime(photo.createdAt), color = Color(0xFFDDDDDD))
            Spacer(Modifier.height(12.dp))
            Row {
                Button(
                    onClick = { onOpen(photo) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ver")
                }
            }
        }
    }
}

private fun buildShelves(photos: List<Photo>): List<Shelf> {
    val shelves = mutableListOf<Shelf>()
    shelves += Shelf("Recién subidas", photos.take(20))

    val loved = photos.filter { it.likes.isNotEmpty() }.sortedByDescending { it.likes.size }
    if (loved.isNotEmpty()) shelves += Shelf("Las más queridas ❤️", loved.take(20))

    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val thisWeek = photos.filter { it.createdAt >= weekAgo }
    if (thisWeek.size > 20) shelves += Shelf("Esta semana", thisWeek)

    val videos = photos.filter { it.isVideo }
    if (videos.isNotEmpty()) shelves += Shelf("Vídeos", videos.take(40))

    photos.groupBy { it.uploaderName }
        .entries
        .sortedByDescending { e -> e.value.first().createdAt }
        .forEach { (name, list) -> shelves += Shelf("Fotos de $name", list.take(40)) }

    photos.groupBy {
        val c = Calendar.getInstance().apply { timeInMillis = it.createdAt }
        c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
    }.values.forEach { list ->
        shelves += Shelf(monthLabel(list.first().createdAt), list.take(60))
    }

    // Una fila sorpresa con fotos al azar (cambia cada día)
    if (photos.size > 12) {
        val seed = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        shelves += Shelf("Recuerdos al azar", photos.shuffled(java.util.Random(seed.toLong())).take(20))
    }
    return shelves
}
