package com.daniviaworld.momentos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.daniviaworld.momentos.data.Album
import com.daniviaworld.momentos.data.MusicLibrary
import com.daniviaworld.momentos.data.FamilySong

/** Opciones del vídeo resumen: música (nuestra o del móvil) y segundos por foto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVideoDialog(
    album: Album?,
    photoCount: Int,
    songs: List<FamilySong>,
    onDismiss: () -> Unit,
    onCreate: (musicUri: Uri?, secondsPerPhoto: Int) -> Unit,
) {
    val context = LocalContext.current
    var musicKey by remember {
        mutableStateOf(album?.music?.takeIf { it.isNotBlank() } ?: songs.firstOrNull()?.let { MusicLibrary.familyKey(it) } ?: "alegre")
    }
    var ownSong by remember { mutableStateOf<Uri?>(null) }
    var seconds by remember { mutableIntStateOf(3) }
    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ownSong = uri
    }
    val usable = photoCount.coerceAtMost(30)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎬 Vídeo de ${album?.title ?: "Sin álbum"}") },
        text = {
            Column {
                Text(
                    "Con $usable fotos" + (if (photoCount > 30) " (de $photoCount, repartidas por todo el álbum)" else "") +
                        ", portada y música. Dura unos ${(usable * seconds + 5) / 60} min ${(usable * seconds + 5) % 60} s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text("Música", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                if (ownSong != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎵 Canción del móvil elegida", modifier = Modifier.weight(1f))
                        TextButton(onClick = { ownSong = null }) { Text("Quitar") }
                    }
                } else {
                    MusicChips(selected = musicKey, onSelect = { musicKey = it }, songs = songs)
                }
                TextButton(onClick = { pickSong.launch(arrayOf("audio/*")) }) {
                    Text("🎵 Usar una canción de mi móvil…")
                }
                Spacer(Modifier.height(6.dp))
                Text("Segundos por foto", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(2, 3, 4, 5).forEach { s ->
                        FilterChip(selected = seconds == s, onClick = { seconds = s }, label = { Text("$s s") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val uri = ownSong ?: MusicLibrary.resolve(context, musicKey, songs)
                onCreate(uri, seconds)
            }) { Text("Crear vídeo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Progreso y resultado de lo que se está creando (vídeo, collage o libro). */
@Composable
fun CreationDialogs(vm: MainViewModel) {
    when (val c = vm.creation) {
        null -> Unit
        is Creation.Working -> {
            KeepScreenOn()
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
                title = { Text("Creando…") },
                text = {
                    Column {
                        Text(c.text)
                        Spacer(Modifier.height(14.dp))
                        val p = c.progress
                        if (p == null) {
                            CircularProgressIndicator()
                        } else {
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Deja la app abierta mientras tanto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { vm.cancelCreation() }) { Text("Cancelar") } },
            )
        }
        is Creation.Done -> {
            val what = when (c.kind) {
                "video" -> "El vídeo"
                "gif" -> "El GIF animado"
                "collage" -> "El collage"
                else -> "El libro de fotos (PDF)"
            }
            val where = when (c.kind) {
                "video" -> "Películas › DANIVIA Momentos"
                "collage", "gif" -> "Imágenes › DANIVIA Momentos"
                else -> "Descargas › DANIVIA Momentos"
            }
            AlertDialog(
                onDismissRequest = { vm.closeCreation() },
                title = { Text("¡Listo! ✨") },
                text = {
                    Text(
                        "$what está creado." +
                            if (c.savedToDevice) "\nTambién lo tienes guardado en el móvil, en $where." else ""
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        Button(onClick = { vm.shareCreation() }) { Text("Compartir") }
                        // un GIF subido se convertiría en foto fija: se comparte (WhatsApp lo anima)
                        if (c.kind != "book" && c.kind != "gif") {
                            OutlinedButton(onClick = { vm.addCreationToAlbum() }) { Text("Añadir al álbum") }
                        }
                    }
                },
                dismissButton = { TextButton(onClick = { vm.closeCreation() }) { Text("Cerrar") } },
            )
        }
    }
}
