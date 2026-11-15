package com.daniviaworld.momentos.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.FocusCard
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.SocialBadge
import com.daniviaworld.momentos.ui.VideoBadge
import com.daniviaworld.momentos.ui.dayKey
import com.daniviaworld.momentos.ui.dayLabel
import com.daniviaworld.momentos.ui.viewPadding

/** Cuadrícula agrupada por día de subida, de la más nueva a la más antigua. */
@Composable
fun GridView(photos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit) {
    val groups = remember(photos) { photos.groupBy { dayKey(it.createdAt) }.values.toList() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 210.dp else 108.dp),
        contentPadding = viewPadding(isTv),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEach { group ->
            val first = group.first()
            item(key = "h_${dayKey(first.createdAt)}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    dayLabel(first.createdAt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                )
            }
            items(group, key = { it.id }) { photo ->
                FocusCard(
                    onClick = { onOpen(photo) },
                    modifier = Modifier.aspectRatio(1f),
                    focusScale = 1.1f,
                ) {
                    PhotoImage(photo.thumbUrl, Modifier.fillMaxSize())
                    VideoBadge(photo)
                    SocialBadge(photo)
                }
            }
        }
    }
}
