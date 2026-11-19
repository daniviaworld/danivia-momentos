package com.daniviaworld.momentos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.image.ImageProcessor
import com.daniviaworld.momentos.share.Sharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Opciones del GIF animado. */
data class GifOptions(
    /** "lento", "normal", "rapido" */
    val speed: String = "normal",
    /** Ancho en píxeles. */
    val size: Int = 480,
    /** "1:1", "4:5", "16:9" */
    val format: String = "1:1",
    /** "fundido" o "corte" */
    val transition: String = "fundido",
    /** Ida y vuelta (como un boomerang). */
    val boomerang: Boolean = false,
)

/** Crea un GIF animado con las fotos elegidas (en el propio móvil). */
object GifMaker {

    const val MAX_PHOTOS = 15

    val speeds = listOf("lento" to "🐢 Lento", "normal" to "🚶 Normal", "rapido" to "🐇 Rápido")
    val sizes = listOf(360 to "Pequeño", 480 to "Mediano", 640 to "Grande")
    val formats = listOf("1:1" to "⬛ Cuadrado", "4:5" to "📱 Vertical", "16:9" to "📺 Apaisado")
    val transitions = listOf("fundido" to "🌫️ Fundido", "corte" to "✂️ Corte seco")

    /** Cuánto se ve cada foto, en centésimas de segundo. */
    fun holdCs(speed: String) = when (speed) {
        "lento" -> 160
        "rapido" -> 45
        else -> 90
    }

    fun height(o: GifOptions) = when (o.format) {
        "4:5" -> o.size * 5 / 4
        "16:9" -> o.size * 9 / 16
        else -> o.size
    }

    /** Las que se proponen al empezar: las más queridas (hasta 8), en orden de fecha. */
    fun suggested(photos: List<Photo>): List<Photo> =
        photos.filter { !it.isVideo }
            .sortedByDescending { it.likes.size * 2 + it.reactions.size * 2 + it.commentCount }
            .take(8)
            .sortedBy { it.createdAt }

    suspend fun make(
        context: Context,
        photos: List<Photo>,
        title: String,
        o: GifOptions,
        onProgress: (String, Float) -> Unit,
    ): File {
        val chosen = photos.filter { !it.isVideo }.take(MAX_PHOTOS)
        if (chosen.size < 2) throw IllegalArgumentException("Elige al menos 2 fotos para el GIF.")
        val app = context.applicationContext
        val work = File(app.cacheDir, "gif_tmp").apply { deleteRecursively(); mkdirs() }
        val w = o.size
        val h = height(o)

        // 1. Descargar y recortar cada foto al tamaño del GIF
        val frames = mutableListOf<IntArray>()
        chosen.forEachIndexed { i, p ->
            withContext(Dispatchers.Main) { onProgress("Preparando fotos (${i + 1} de ${chosen.size})…", 0.4f * i / chosen.size) }
            val px = withContext(Dispatchers.IO) {
                try {
                    val f = File(work, "f_$i.jpg")
                    Sharer.download(p.url, f)
                    val bmp = ImageProcessor.decodeFile(f, maxOf(w, h) * 2)
                    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    AlbumVideoMaker.drawCrop(Canvas(out), bmp, RectF(0f, 0f, w.toFloat(), h.toFloat()))
                    bmp.recycle()
                    f.delete()
                    IntArray(w * h).also { out.getPixels(it, 0, w, 0, 0, w, h); out.recycle() }
                } catch (_: Exception) {
                    null
                }
            }
            if (px != null) frames += px
        }
        if (frames.size < 2) throw IllegalArgumentException("No se pudieron descargar las fotos.")

        // 2. Orden de los fotogramas (con ida y vuelta si se pide)
        val order = frames.indices.toMutableList()
        if (o.boomerang && frames.size > 2) order += (frames.size - 2 downTo 1)

        // 3. Codificar
        val file = File(Sharer.creationsDir(app), "GIF_${AlbumVideoMaker.safeName(title)}.gif")
        withContext(Dispatchers.Default) {
            file.outputStream().buffered().use { os ->
                val enc = GifEncoder(os, w, h)
                val hold = holdCs(o.speed)
                val steps = if (o.transition == "fundido") 3 else 0
                val blend = IntArray(w * h)
                order.forEachIndexed { k, idx ->
                    ensureActive()
                    withContext(Dispatchers.Main) { onProgress("Creando el GIF…", 0.4f + 0.6f * k / order.size) }
                    val a = frames[idx]
                    enc.addFrame(a, hold)
                    if (steps > 0) {
                        val b = frames[order[(k + 1) % order.size]]
                        for (s in 1..steps) {
                            val t = s / (steps + 1f)
                            for (i in a.indices) {
                                val pa = a[i]
                                val pb = b[i]
                                val r = (((pa shr 16) and 0xFF) * (1 - t) + ((pb shr 16) and 0xFF) * t).toInt()
                                val g = (((pa shr 8) and 0xFF) * (1 - t) + ((pb shr 8) and 0xFF) * t).toInt()
                                val bl = ((pa and 0xFF) * (1 - t) + (pb and 0xFF) * t).toInt()
                                blend[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                            }
                            enc.addFrame(blend, 7)
                        }
                    }
                }
                enc.finish()
            }
        }
        work.deleteRecursively()
        return file
    }
}
