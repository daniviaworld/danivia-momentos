package com.daniviaworld.momentos.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.FocusCard
import com.daniviaworld.momentos.ui.PhotoImage
import com.daniviaworld.momentos.ui.SocialBadge
import com.daniviaworld.momentos.ui.VideoBadge

/** 🧱 Mosaico: cada foto con su forma real (verticales altas, apaisadas anchas), estilo Pinterest. */
@Composable
fun MosaicView(photos: List<Photo>, isTv: Boolean, onOpen: (Photo) -> Unit) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(if (isTv) 250.dp else 150.dp),
        contentPadding = if (isTv) PaddingValues(horizontal = 48.dp, vertical = 16.dp) else PaddingValues(6.dp, 6.dp, 6.dp, 96.dp),
        verticalItemSpacing = if (isTv) 16.dp else 6.dp,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(photos, key = { it.id }) { p ->
            val ratio = if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.56f, 1.8f) else 1f
            FocusCard(
                onClick = { onOpen(p) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
                shape = RoundedCornerShape(if (isTv) 14.dp else 10.dp),
            ) {
                PhotoImage(if (isTv) p.url else p.thumbUrl, Modifier.fillMaxSize())
                VideoBadge(p)
                SocialBadge(p, compact = !isTv)
            }
        }
    }
}
