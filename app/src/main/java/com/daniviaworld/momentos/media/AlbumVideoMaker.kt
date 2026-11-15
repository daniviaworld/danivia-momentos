package com.daniviaworld.momentos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.daniviaworld.momentos.R
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.share.Sharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Crea un vídeo resumen de un álbum en el propio móvil (sin servidor):
 * portada con el título, cada foto unos segundos con un zoom lento, cierre con el logo y música de fondo.
 */
@OptIn(UnstableApi::class)
object AlbumVideoMaker {

    class Options(
        val title: String,
        /** Música: pista de MusicLibrary (uri android.resource://), canción del móvil o null = sin música. */
        val musicUri: Uri?,
        val secondsPerPhoto: Int = 3,
        val maxPhotos: Int = 30,
    )

    private val spanish = Locale("es", "ES")

    /** [onProgress]: (texto, 0..1). Devuelve el MP4 creado. */
    suspend fun make(
        context: Context,
        photos: List<Photo>,
        options: Options,
        onProgress: (String, Float) -> Unit,
    ): File {
        val app = context.applicationContext
        val chosen = pick(photos.filter { !it.isVideo }, options.maxPhotos)
        if (chosen.isEmpty()) throw IllegalArgumentException("Este álbum no tiene fotos para hacer un vídeo.")

        // Orientación: la de la mayoría de fotos
        val portrait = chosen.count { it.height > it.width } >= chosen.size / 2.0
        val w = if (portrait) 720 else 1280
        val h = if (portrait) 1280 else 720

        val work = File(Sharer.creationsDir(app), "video_tmp").apply {
            deleteRecursively(); mkdirs()
        }

        // 1. Descargar fotos
        val files = mutableListOf<File>()
        withContext(Dispatchers.IO) {
            chosen.forEachIndexed { i, p ->
                withContext(Dispatchers.Main) {
                    onProgress("Descargando fotos (${i + 1} de ${chosen.size})…", 0.3f * i / chosen.size)
                }
                val f = File(work, "f_$i.jpg")
                try {
                    Sharer.download(p.url, f)
                    files += f
                } catch (_: Exception) {
                    // si una falla, seguimos con las demás
                }
            }
        }
        if (files.isEmpty()) throw IllegalArgumentException("No se pudieron descargar las fotos. Revisa la conexión.")

        // Canción de la familia (en internet): se descarga antes de montar
        var music = options.musicUri
        if (music != null && (music.scheme == "https" || music.scheme == "http")) {
            withContext(Dispatchers.Main) { onProgress("Descargando la música…", 0.3f) }
            music = try {
                val f = File(work, "musica.mp3")
                withContext(Dispatchers.IO) { Sharer.download(music.toString(), f) }
                Uri.fromFile(f)
            } catch (_: Exception) {
                null
            }
        }

        // 2. Portada y cierre
        val dateText = dateRange(chosen)
        val cover = File(work, "portada.jpg")
        val ending = File(work, "cierre.jpg")
        withContext(Dispatchers.Default) {
            drawCover(files.first(), options.title, dateText, w, h, cover)
            drawEnding(app, w, h, ending)
        }

        // 3. Montaje (con fallbacks: sin zoom y sin música si el móvil no puede)
        val out = File(Sharer.creationsDir(app), "Momentos_${safeName(options.title)}.mp4")
        out.delete()
        val attempts = listOf(
            Triple(true, music, "con música"),
            Triple(false, music, "sin zoom"),
            Triple(false, null, "sin música"),
        ).distinctBy { it.first to it.second }
        var lastError: Exception? = null
        for ((zoom, track, _) in attempts) {
            try {
                val seq = buildList {
                    add(imageItem(cover, 2500, w, h, zoom = false))
                    files.forEach { add(imageItem(it, options.secondsPerPhoto * 1000L, w, h, zoom)) }
                    add(imageItem(ending, 2500, w, h, zoom = false))
                }
                export(app, seq, track, out) { p -> onProgress("Creando el vídeo…", 0.3f + 0.7f * p) }
                work.deleteRecursively()
                return out
            } catch (e: Exception) {
                lastError = e
                out.delete()
            }
        }
        work.deleteRecursively()
        throw lastError ?: IllegalStateException("No se pudo crear el vídeo")
    }

    /** Hasta [max] fotos repartidas por todo el álbum, en orden cronológico. */
    private fun pick(photos: List<Photo>, max: Int): List<Photo> {
        val sorted = photos.sortedBy { it.createdAt }
        if (sorted.size <= max) return sorted
        val step = sorted.size.toDouble() / max
        return (0 until max).map { sorted[(it * step).toInt()] }
    }

    private fun imageItem(file: File, durationMs: Long, w: Int, h: Int, zoom: Boolean): EditedMediaItem {
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMimeType(MimeTypes.IMAGE_JPEG)
            .setImageDurationMs(durationMs)
            .build()
        val effects = mutableListOf<Effect>(
            Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        )
        if (zoom) {
            val durUs = durationMs * 1000
            // zoom lento hacia dentro (efecto "Ken Burns")
            effects += MatrixTransformation { presentationTimeUs ->
                val p = ((presentationTimeUs % durUs).toFloat() / durUs).coerceIn(0f, 1f)
                val s = 1f + 0.08f * p
                Matrix().apply { postScale(s, s) }
            }
        }
        return EditedMediaItem.Builder(item)
            .setFrameRate(30)
            .setEffects(Effects(emptyList(), effects))
            .build()
    }

    private suspend fun export(
        context: Context,
        images: List<EditedMediaItem>,
        musicUri: Uri?,
        out: File,
        onProgress: (Float) -> Unit,
    ) = coroutineScope {
        withContext(Dispatchers.Main) {
            val sequences = mutableListOf(EditedMediaItemSequence(images))
            if (musicUri != null) {
                val music = EditedMediaItem.Builder(MediaItem.fromUri(musicUri)).setRemoveVideo(true).build()
                // la música se repite hasta que acaban las fotos
                sequences += EditedMediaItemSequence(listOf(music), true)
            }
            val compositionBuilder = Composition.Builder(sequences)
            if (musicUri != null) compositionBuilder.experimentalSetForceAudioTrack(true)
            val composition = compositionBuilder.build()

            val encoders = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(5_000_000).build())
                .build()

            var transformer: Transformer? = null
            val poll = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    delay(400)
                    val t = transformer ?: continue
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                }
            }
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val tr = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoders)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer = tr
                    tr.start(composition, out.absolutePath)
                    cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { tr.cancel() } }
                }
            } finally {
                poll.cancel()
            }
        }
    }

    // ---------- Portada y cierre ----------

    private fun loadScaled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Dibuja [src] recortada para llenar [dst]. */
    fun drawCrop(canvas: Canvas, src: Bitmap, dst: RectF, paint: Paint? = null) {
        val scale = max(dst.width() / src.width, dst.height() / src.height)
        val sw = (dst.width() / scale).toInt()
        val sh = (dst.height() / scale).toInt()
        val sx = (src.width - sw) / 2
        val sy = (src.height - sh) / 2
        canvas.drawBitmap(src, Rect(sx, sy, sx + sw, sy + sh), dst, paint ?: Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawCover(photo: File, title: String, date: String, w: Int, h: Int, out: File) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(17, 19, 37))
        loadScaled(photo, 1600)?.let { drawCrop(c, it, RectF(0f, 0f, w.toFloat(), h.toFloat())) }
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.argb(90, 0, 0, 0), Color.argb(200, 10, 8, 30)), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
        val big = w.coerceAtMost(h)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = big * 0.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(12f, 0f, 4f, Color.argb(160, 0, 0, 0))
        }
        // título en una o dos líneas
        val lines = wrap(title, titlePaint, w * 0.86f)
        var y = h * 0.48f - (lines.size - 1) * titlePaint.textSize * 0.6f
        lines.forEach {
            c.drawText(it, w / 2f, y, titlePaint)
            y += titlePaint.textSize * 1.15f
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 176, 61)
            textSize = big * 0.045f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        c.drawText(date.uppercase(spanish), w / 2f, y + sub.textSize * 0.6f, sub)
        save(bmp, out)
    }

    private fun drawEnding(context: Context, w: Int, h: Int, out: File) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(17, 19, 37))
        val big = w.coerceAtMost(h)
        BitmapFactory.decodeResource(context.resources, R.drawable.logo_momentos)?.let { logo ->
            val size = big * 0.35f
            val left = (w - size) / 2f
            val top = h / 2f - size * 0.8f
            c.drawBitmap(logo, null, RectF(left, top, left + size, top + size), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 120, 150)
            textSize = big * 0.05f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.25f
        }
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = big * 0.09f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("DANIVIA", w / 2f, h / 2f + big * 0.12f, p1)
        c.drawText("Momentos", w / 2f, h / 2f + big * 0.22f, p2)
        save(bmp, out)
    }

    fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) line = candidate
            else {
                lines += line; line = word
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines.take(3)
    }

    private fun save(bmp: Bitmap, out: File) {
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
    }

    fun dateRange(photos: List<Photo>): String {
        if (photos.isEmpty()) return ""
        val first = photos.minOf { it.createdAt }
        val last = photos.maxOf { it.createdAt }
        val day = SimpleDateFormat("d 'de' MMMM yyyy", spanish)
        val a = day.format(first)
        val b = day.format(last)
        return if (a == b) a else "${SimpleDateFormat("d MMM", spanish).format(first)} – $b"
    }

    fun safeName(text: String): String =
        text.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ]+"), "_").trim('_').take(40).ifBlank { "album" }
}
