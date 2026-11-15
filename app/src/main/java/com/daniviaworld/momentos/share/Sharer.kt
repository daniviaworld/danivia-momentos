package com.daniviaworld.momentos.share

import android.content.ClipData
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

    /** Comparte un archivo creado por la app (vídeo resumen, collage, PDF…). */
    fun shareFile(context: Context, file: File, mime: String, title: String) {
        val app = context.applicationContext
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(
            Intent.createChooser(send, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    /**
     * Guarda una copia en la galería / Descargas del móvil (Android 10+).
     * [collection]: "video", "image" o "download". Devuelve true si se guardó.
     */
    fun saveToDevice(context: Context, file: File, mime: String, collection: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = context.contentResolver
            val (uriBase, folder) = when (collection) {
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/DANIVIA Momentos"
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/DANIVIA Momentos"
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/DANIVIA Momentos"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val item = resolver.insert(uriBase, values) ?: return false
            resolver.openOutputStream(item)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(item, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Carpeta de trabajo para lo que crea la app. */
    fun creationsDir(context: Context): File =
        File(context.applicationContext.cacheDir, "creations").apply { mkdirs() }

    /** Descarga una URL a un archivo. */
    fun download(url: String, out: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode !in 200..299) error("Error ${conn.responseCode}")
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it, 64 * 1024) } }
        } finally {
            conn.disconnect()
        }
    }

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
