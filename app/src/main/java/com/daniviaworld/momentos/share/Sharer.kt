package com.daniviaworld.momentos.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.daniviaworld.momentos.data.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Descarga la foto o el vídeo y abre el menú de compartir de Android (WhatsApp, Telegram, correo…). */
object Sharer {

    suspend fun share(context: Context, photo: Photo) {
        val app = context.applicationContext
        val isVideo = photo.isVideo && photo.videoUrl.isNotBlank()
        val url = if (isVideo) photo.videoUrl else photo.url
        val ext = if (isVideo) "mp4" else "jpg"
        val mime = if (isVideo) "video/mp4" else "image/jpeg"

        val file = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "shared").apply { mkdirs() }
            // Limpia lo compartido antes para no llenar la memoria
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "DaniviaMomentos_${photo.id}.$ext")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            if (conn.responseCode !in 200..299) error("Error ${conn.responseCode}")
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it, 64 * 1024) } }
            conn.disconnect()
            out
        }

        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, if (isVideo) "Compartir vídeo" else "Compartir foto")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) { app.startActivity(chooser) }
    }
}
