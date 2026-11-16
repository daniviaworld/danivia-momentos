package com.daniviaworld.momentos.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.media.VoiceRecorder
import kotlinx.coroutines.delay
import java.io.File

/** Fila de la nota de voz de una foto: escucharla (y en la TV suena sola), grabarla o quitarla. */
@Composable
fun VoiceNoteRow(vm: MainViewModel, photo: Photo) {
    val context = LocalContext.current
    val canEdit = !vm.isTv && vm.canEditVoice(photo)
    var recording by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) recording = true else vm.message = "Hace falta permiso de micrófono para grabar la nota de voz."
    }

    if (photo.voiceUrl.isNotBlank()) {
        var playing by remember(photo.voiceUrl) { mutableStateOf(false) }
        val player = remember(photo.voiceUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(photo.voiceUrl))
                prepare()
            }
        }
        DisposableEffect(player) {
            val l = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            }
            player.addListener(l)
            onDispose {
                player.removeListener(l)
                player.release()
            }
        }
        // En la tele la nota suena sola al abrir la foto
        LaunchedEffect(photo.id) {
            if (vm.isTv) {
                delay(600)
                player.seekTo(0)
                player.play()
            }
        }
        Row(
            Modifier.padding(start = if (vm.isTv) 0.dp else 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (playing) "🔊 Sonando…  " else "🔈 Nota de voz  ") + "de ${photo.voiceBy}",
                color = Color.White,
                fontSize = if (vm.isTv) 20.sp else 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Violet.copy(alpha = 0.55f))
                    .clickable {
                        if (player.isPlaying) player.pause() else {
                            player.seekTo(0); player.play()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            if (canEdit) {
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { vm.deleteVoice(photo) }) { Text("Quitar", color = Color(0xFFDDDDDD)) }
            }
        }
    } else if (canEdit) {
        Text(
            "🎙️ Añadir nota de voz",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(start = 12.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x44FFFFFF))
                .clickable {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) recording = true else askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }

    if (recording) {
        VoiceRecordDialog(
            onDone = { file ->
                recording = false
                vm.uploadVoice(photo, file)
            },
            onCancel = { recording = false },
        )
    }
}

@Composable
private fun VoiceRecordDialog(onDone: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var seconds by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        try {
            recorder.start()
        } catch (_: Exception) {
            failed = true
        }
        onDispose { if (recorder.isRecording) recorder.cancel() }
    }
    LaunchedEffect(Unit) {
        while (seconds < VoiceRecorder.MAX_MS / 1000) {
            delay(1000)
            seconds++
        }
        recorder.stop()?.let(onDone) ?: onCancel()
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (failed) "No se pudo grabar" else "🎙️ Grabando…") },
        text = {
            Column {
                if (failed) {
                    Text("El micrófono no está disponible ahora mismo.")
                } else {
                    Text("Habla ahora. Máximo 30 segundos.")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "0:%02d".format(seconds),
                        style = MaterialTheme.typography.displaySmall,
                        color = Orange,
                    )
                }
            }
        },
        confirmButton = {
            if (!failed) {
                Button(onClick = {
                    val f = recorder.stop()
                    if (f != null) onDone(f) else onCancel()
                }) { Text("Parar y guardar") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                recorder.cancel()
                onCancel()
            }) { Text("Cancelar") }
        },
    )
}
