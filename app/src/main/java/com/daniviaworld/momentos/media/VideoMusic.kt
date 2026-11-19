package com.daniviaworld.momentos.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import com.daniviaworld.momentos.share.Sharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Cambia la música de un vídeo ya subido (p. ej. un vídeo de álbum hecho con la música antigua). */
@OptIn(UnstableApi::class)
object VideoMusic {

    /**
     * [musicUri] = nueva música (o null = sin música). [keepOriginal] = mezclar con el sonido que ya tenía
     * (para vídeos grabados con el móvil); si es false se quita el sonido viejo (para vídeos de álbum).
     * Devuelve el MP4 nuevo.
     */
    suspend fun change(
        context: Context,
        videoUrl: String,
        musicUri: Uri?,
        keepOriginal: Boolean,
        onProgress: (String, Float) -> Unit,
    ): File {
        val app = context.applicationContext
        val dir = File(app.cacheDir, "videomusic").apply { deleteRecursively(); mkdirs() }
        val input = File(dir, "entrada.mp4")
        withContext(Dispatchers.Main) { onProgress("Descargando el vídeo…", 0.05f) }
        withContext(Dispatchers.IO) {
            if (videoUrl.startsWith("http")) Sharer.download(videoUrl, input)
            else File(videoUrl).copyTo(input, overwrite = true) // un vídeo del propio aparato (p. ej. un recuerdo)
        }

        var music = musicUri
        if (music != null && (music.scheme == "https" || music.scheme == "http")) {
            withContext(Dispatchers.Main) { onProgress("Descargando la música…", 0.15f) }
            val f = File(dir, "musica.mp3")
            withContext(Dispatchers.IO) { Sharer.download(music.toString(), f) }
            music = Uri.fromFile(f)
        }

        val video = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
            .setRemoveAudio(!keepOriginal)
            .build()
        val sequences = mutableListOf(EditedMediaItemSequence(listOf(video)))
        if (music != null) {
            val track = EditedMediaItem.Builder(MediaItem.fromUri(music)).setRemoveVideo(true).build()
            // la música se repite hasta que acaba el vídeo
            sequences += EditedMediaItemSequence(listOf(track), true)
        }
        val builder = Composition.Builder(sequences)
        if (music != null) builder.experimentalSetForceAudioTrack(true)

        val out = File(dir, "video_${System.currentTimeMillis()}.mp4")
        AlbumVideoMaker.runTransformer(app, builder.build(), out) { p ->
            onProgress("Poniendo la música nueva…", 0.2f + 0.8f * p)
        }
        input.delete()
        return out
    }
}
