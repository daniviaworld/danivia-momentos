package com.daniviaworld.momentos.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.FocusCard
import com.daniviaworld.momentos.ui.Orange
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.SocialBadge
import com.daniviaworld.momentos.ui.VideoBadge
import com.daniviaworld.momentos.ui.Violet
import com.daniviaworld.momentos.ui.bottomShade
import com.daniviaworld.momentos.ui.dayKey
import com.daniviaworld.momentos.ui.dayLabel
import com.daniviaworld.momentos.ui.shortDateTime

/**
 * Cuadrícula agrupada por día, de lo más nuevo a lo más antiguo.
 * En el móvil: fotos de borde a borde (3 por fila) y la más reciente en grande arriba.
 */
@Composable
fun GridView(
    photos: List<Photo>,
    isTv: Boolean,
    onOpen: (Photo) -> Unit,
    selectedIds: Set<String> = emptySet(),
    onLongPress: ((Photo) -> Unit)? = null,
) {
    val selecting = selectedIds.isNotEmpty()
    // La foto destacada sólo en el móvil y fuera del modo selección
    val hero = if (!isTv && !selecting) photos.firstOrNull() else null
    val rest = if (hero != null) photos.drop(1) else photos
    val groups = remember(rest) { rest.groupBy { dayKey(it.createdAt) }.values.toList() }
    val gap = if (isTv) 14.dp else 2.dp
    val tileShape = RoundedCornerShape(if (isTv) 12.dp else 3.dp)

    LazyVerticalGrid(
        columns = if (isTv) GridCells.Adaptive(210.dp) else GridCells.Adaptive(112.dp),
        contentPadding = if (isTv) PaddingValues(horizontal = 48.dp, vertical = 16.dp)
        else PaddingValues(bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hero != null) {
            item(key = "hero_${hero.id}", span = { GridItemSpan(maxLineSpan) }) {
                FocusCard(
                    onClick = { onOpen(hero) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    shape = RoundedCornerShape(0.dp),
                    focusScale = 1f,
                    onLongClick = onLongPress?.let { cb -> { cb(hero) } },
                ) {
                    PhotoImage(hero.url, Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(bottomShade)
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    ) {
                        Text("LO ÚLTIMO", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text(
                            hero.uploaderName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(shortDateTime(hero.createdAt), color = Color(0xFFE0E0E0), fontSize = 13.sp)
                    }
                    VideoBadge(hero, big = true)
                    SocialBadge(hero)
                }
            }
        }

        groups.forEach { group ->
            val first = group.first()
            item(key = "h_${dayKey(first.createdAt)}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    dayLabel(first.createdAt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        top = if (isTv) 12.dp else 14.dp,
                        bottom = if (isTv) 4.dp else 6.dp,
                        start = if (isTv) 4.dp else 12.dp,
                    ),
                )
            }
            items(group, key = { it.id }) { photo ->
                val selected = photo.id in selectedIds
                FocusCard(
                    onClick = { onOpen(photo) },
                    modifier = Modifier.aspectRatio(1f),
                    shape = tileShape,
                    focusScale = 1.1f,
                    onLongClick = onLongPress?.let { cb -> { cb(photo) } },
                ) {
                    // en la tele, la foto completa (las miniaturas se quedan cortas en pantalla grande)
                    PhotoImage(if (isTv) photo.url else photo.thumbUrl, Modifier.fillMaxSize())
                    VideoBadge(photo)
                    // en el móvil las miniaturas son pequeñas: sólo el corazón para no pisar la etiqueta de vídeo
                    SocialBadge(photo, compact = !isTv)
                    if (selecting) {
                        // modo selección: velo + círculo marcado
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(if (selected) Violet.copy(alpha = 0.35f) else Color(0x33000000))
                                .then(if (selected) Modifier.border(3.dp, Violet) else Modifier)
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (selected) Violet else Color(0x66000000))
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
