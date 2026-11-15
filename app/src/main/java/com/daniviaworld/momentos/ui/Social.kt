package com.daniviaworld.momentos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniviaworld.momentos.data.Comment
import com.daniviaworld.momentos.data.Photo

private val Heart = Color(0xFFFF4D6D)

/** "Les gusta a Laura y Mamá" a partir de la lista de nombres. */
fun likesText(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> if (names[0] == "ti") "Te gusta" else "Le gusta a ${names[0]}"
    2 -> "Les gusta a ${names[0]} y ${names[1]}"
    else -> "Les gusta a ${names[0]}, ${names[1]} y ${names.size - 2} más"
}

/**
 * Barra inferior de la foto: corazón, contador, quién le ha dado y acceso a comentarios.
 * En la TV sólo se muestra (con los últimos comentarios), sin botones.
 */
@Composable
fun SocialBar(
    vm: MainViewModel,
    photo: Photo,
    comments: List<Comment>,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liked = vm.isLikedByMe(photo)
    val names = vm.likerNames(photo)
    val pop by animateFloatAsState(
        targetValue = if (liked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 400f),
        label = "heartPop",
    )

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .padding(horizontal = if (vm.isTv) 40.dp else 8.dp, vertical = 8.dp)
    ) {
        if (vm.isTv) {
            // TV: últimos comentarios encima
            comments.takeLast(3).forEach { c ->
                Text(
                    buildCommentLine(c),
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            if (comments.isNotEmpty()) Spacer(Modifier.size(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.isTv) {
                Icon(
                    if (photo.likes.isEmpty()) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = if (photo.likes.isEmpty()) Color.White else Heart,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                IconButton(onClick = { vm.toggleLike(photo) }) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "Quitar me gusta" else "Me gusta",
                        tint = if (liked) Heart else Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer { scaleX = pop; scaleY = pop },
                    )
                }
            }
            Text(
                if (photo.likes.isEmpty()) "Sé el primero en darle me gusta" else likesText(names),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
            if (vm.isTv) {
                if (photo.commentCount > 0) {
                    Text("💬 ${photo.commentCount}", color = Color.White, fontSize = 18.sp)
                }
            } else {
                TextButton(onClick = onOpenComments) {
                    Text(
                        if (photo.commentCount == 0) "💬 Comentar" else "💬 ${photo.commentCount}",
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private fun buildCommentLine(c: Comment): String = "${c.name}: ${c.text}"

/** Panel de comentarios (móvil): lista + caja para escribir. */
@Composable
fun CommentsPanel(
    vm: MainViewModel,
    photo: Photo,
    comments: List<Comment>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Comment?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.size - 1)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .imePadding(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Comentarios",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
            }

            Box(Modifier.weight(1f)) {
                if (comments.isEmpty()) {
                    Text(
                        "Todavía no hay comentarios. ¡Escribe el primero!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        items(comments, key = { it.id }) { c ->
                            CommentBubble(
                                comment = c,
                                mine = c.uid == vm.myUid,
                                canDelete = vm.canDeleteComment(photo, c),
                                onDelete = { toDelete = c },
                            )
                        }
                        item { Spacer(Modifier.size(4.dp)) }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    placeholder = { Text("Escribe un comentario…") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        vm.addComment(photo, text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (text.isNotBlank()) Orange else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    toDelete?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar este comentario?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteComment(photo, c)
                    toDelete = null
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CommentBubble(comment: Comment, mine: Boolean, canDelete: Boolean, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) Orange.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mine) Orange else Violet,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shortDateTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            if (canDelete) {
                Text(
                    "Borrar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDelete)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

/** Corazón y comentarios pequeños sobre las miniaturas (abajo a la derecha). */
@Composable
fun BoxScope.SocialBadge(photo: Photo, compact: Boolean = false) {
    if (photo.likes.isEmpty() && (compact || photo.commentCount == 0)) return
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xAA000000))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photo.likes.isNotEmpty()) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Heart, modifier = Modifier.size(12.dp))
            Text(" ${photo.likes.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (photo.commentCount > 0 && !compact) {
            Text(
                (if (photo.likes.isNotEmpty()) "  " else "") + "💬 ${photo.commentCount}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
