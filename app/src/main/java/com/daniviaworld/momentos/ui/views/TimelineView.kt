package com.daniviaworld.momentos.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.daniviaworld.momentos.ui.dayKey
import com.daniviaworld.momentos.ui.dayLabel
import java.text.SimpleDateFormat
import java.util.Locale

/** 📅 Línea del tiempo: una línea vertical con cada día y sus fotos al lado, del más nuevo al más antiguo. */
@Composable
fun TimelineView(
    photos: List<Photo>,
    isTv: Boolean,
    onOpen: (Photo) -> Unit,
    onOpenIn: ((Photo, List<Photo>) -> Unit)? = null,
) {
    val days = remember(photos) { photos.groupBy { dayKey(it.createdAt) }.values.toList() }
    val dayNum = remember { SimpleDateFormat("d", Locale("es", "ES")) }
    val month = remember { SimpleDateFormat("MMM yy", Locale("es", "ES")) }
    val rail = if (isTv) 96.dp else 62.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (isTv) 32.dp else 4.dp, end = if (isTv) 32.dp else 0.dp, top = 8.dp, bottom = if (isTv) 32.dp else 96.dp),
    ) {
        items(days, key = { dayKey(it.first().createdAt) }) { day ->
            val first = day.first()
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // la línea del tiempo y su punto
                        val x = rail.toPx() / 2
                        drawLine(Violet.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                        drawCircle(Orange, radius = 7.dp.toPx(), center = Offset(x, 30.dp.toPx()))
                        drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x, 30.dp.toPx()))
                    },
            ) {
                Column(Modifier.width(rail).padding(top = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayNum.format(first.createdAt), fontWeight = FontWeight.Black, fontSize = if (isTv) 30.sp else 22.sp)
                    Text(
                        month.format(first.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp)) {
                    val who = day.map { it.uploaderName }.distinct().joinToString(", ")
                    Text(
                        dayLabel(first.createdAt).replaceFirstChar { it.uppercase() },
                        style = if (isTv) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${day.size} ${if (day.size == 1) "recuerdo" else "recuerdos"} · $who",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 6.dp),
                        contentPadding = PaddingValues(end = 12.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        items(day, key = { it.id }) { p ->
                            FocusCard(
                                onClick = { if (onOpenIn != null) onOpenIn(p, day) else onOpen(p) },
                                modifier = Modifier
                                    .width(if (isTv) 240.dp else 116.dp)
                                    .aspectRatio(if (isTv) 4f / 3f else 3f / 4f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                PhotoImage(if (isTv) p.url else p.thumbUrl, Modifier.fillMaxSize())
                                VideoBadge(p)
                                SocialBadge(p, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
