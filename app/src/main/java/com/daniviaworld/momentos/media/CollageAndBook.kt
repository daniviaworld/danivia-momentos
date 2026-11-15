package com.daniviaworld.momentos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.share.Sharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val spanish = Locale("es", "ES")

/** Descarga y decodifica una foto reducida (para no gastar memoria). */
private fun loadPhoto(work: File, photo: Photo, maxSide: Int): Bitmap? = try {
    val f = File(work, "${photo.id}.jpg")
    if (!f.exists()) Sharer.download(photo.url, f)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (_: Exception) {
    null
}

/** Las mejores fotos: primero las que más gustan, luego las más recientes. */
private fun best(photos: List<Photo>, n: Int): List<Photo> =
    photos.filter { !it.isVideo }
        .sortedWith(compareByDescending<Photo> { it.likes.size + it.reactions.size }.thenByDescending { it.createdAt })
        .take(n)
        .sortedBy { it.createdAt }

/** Collage de hasta 6 fotos en formato 4:5 (ideal para WhatsApp e Instagram). */
object CollageMaker {

    suspend fun make(context: Context, photos: List<Photo>, title: String): File = withContext(Dispatchers.IO) {
        val chosen = best(photos, 6)
        if (chosen.isEmpty()) throw IllegalArgumentException("No hay fotos para el collage.")
        val work = File(Sharer.creationsDir(context), "collage_tmp").apply { mkdirs() }
        val bitmaps = chosen.mapNotNull { loadPhoto(work, it, 900) }
        if (bitmaps.isEmpty()) throw IllegalArgumentException("No se pudieron descargar las fotos.")

        val w = 1080
        val h = 1350
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(17, 19, 37))

        val header = 170f
        val footer = 70f
        val gap = 14f
        val area = RectF(gap, header, w - gap, h - footer)
        cells(bitmaps.size, area, gap).forEachIndexed { i, r ->
            val path = Path().apply { addRoundRect(r, 22f, 22f, Path.Direction.CW) }
            c.save()
            c.clipPath(path)
            AlbumVideoMaker.drawCrop(c, bitmaps[i], r)
            c.restore()
        }

        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val line = AlbumVideoMaker.wrap(title, t, w - 80f).firstOrNull() ?: title
        c.drawText(line, w / 2f, 92f, t)
        val d = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 176, 61)
            textSize = 30f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText(AlbumVideoMaker.dateRange(chosen).uppercase(spanish), w / 2f, 140f, d)
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("DANIVIA Momentos", w / 2f, h - 26f, f)

        val file = File(Sharer.creationsDir(context), "Collage_${AlbumVideoMaker.safeName(title)}.jpg")
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        out.recycle()
        bitmaps.forEach { it.recycle() }
        work.deleteRecursively()
        file
    }

    /** Reparto de huecos según el número de fotos. */
    private fun cells(n: Int, a: RectF, g: Float): List<RectF> {
        val l = a.left; val t = a.top; val r = a.right; val b = a.bottom
        val mx = (l + r) / 2; val my = (t + b) / 2
        val h3 = (b - t - 2 * g) / 3
        return when (n) {
            1 -> listOf(RectF(a))
            2 -> listOf(RectF(l, t, r, my - g / 2), RectF(l, my + g / 2, r, b))
            3 -> listOf(RectF(l, t, r, my - g / 2), RectF(l, my + g / 2, mx - g / 2, b), RectF(mx + g / 2, my + g / 2, r, b))
            4 -> listOf(
                RectF(l, t, mx - g / 2, my - g / 2), RectF(mx + g / 2, t, r, my - g / 2),
                RectF(l, my + g / 2, mx - g / 2, b), RectF(mx + g / 2, my + g / 2, r, b),
            )
            5 -> listOf(
                RectF(l, t, r, t + h3 * 1.4f),
                RectF(l, t + h3 * 1.4f + g, mx - g / 2, t + h3 * 2.2f + g), RectF(mx + g / 2, t + h3 * 1.4f + g, r, t + h3 * 2.2f + g),
                RectF(l, t + h3 * 2.2f + 2 * g, mx - g / 2, b), RectF(mx + g / 2, t + h3 * 2.2f + 2 * g, r, b),
            )
            else -> (0 until 6).map { i ->
                val col = i % 2; val row = i / 2
                val top = t + row * (h3 + g)
                if (col == 0) RectF(l, top, mx - g / 2, top + h3) else RectF(mx + g / 2, top, r, top + h3)
            }
        }
    }
}

/** Libro de fotos en PDF (A4): portada y una foto por página con quién la subió y cuándo. */
object PhotoBookMaker {

    suspend fun make(context: Context, photos: List<Photo>, title: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val chosen = photos.filter { !it.isVideo }.sortedBy { it.createdAt }.take(40)
            if (chosen.isEmpty()) throw IllegalArgumentException("Este álbum no tiene fotos.")
            val work = File(Sharer.creationsDir(context), "book_tmp").apply { mkdirs() }
            val pw = 595
            val ph = 842
            val doc = PdfDocument()
            try {
                // Portada
                val cover = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
                cover.canvas.apply {
                    drawColor(Color.rgb(17, 19, 37))
                    loadPhoto(work, chosen.first(), 1000)?.let {
                        AlbumVideoMaker.drawCrop(this, it, RectF(0f, 0f, pw.toFloat(), ph * 0.62f))
                        it.recycle()
                    }
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    var y = ph * 0.72f
                    AlbumVideoMaker.wrap(title, tp, pw - 60f).forEach { drawText(it, pw / 2f, y, tp); y += 42f }
                    val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(255, 176, 61); textSize = 15f; textAlign = Paint.Align.CENTER
                        letterSpacing = 0.12f
                    }
                    drawText(AlbumVideoMaker.dateRange(chosen).uppercase(spanish), pw / 2f, y + 6f, dp)
                    val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(140, 255, 255, 255); textSize = 12f; textAlign = Paint.Align.CENTER
                    }
                    drawText("DANIVIA Momentos", pw / 2f, ph - 30f, fp)
                }
                doc.finishPage(cover)

                // Una foto por página
                val day = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", spanish)
                chosen.forEachIndexed { i, photo ->
                    withContext(Dispatchers.Main) { onProgress((i + 1f) / chosen.size) }
                    val bmp = loadPhoto(work, photo, 1100) ?: return@forEachIndexed
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 2).create())
                    page.canvas.apply {
                        drawColor(Color.WHITE)
                        val margin = 36f
                        val maxW = pw - 2 * margin
                        val maxH = ph - 2 * margin - 70f
                        val s = min(maxW / bmp.width, maxH / bmp.height)
                        val dw = bmp.width * s
                        val dh = bmp.height * s
                        val left = (pw - dw) / 2
                        val top = margin + (maxH - dh) / 2
                        drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                        val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(30, 30, 40); textSize = 14f; textAlign = Paint.Align.CENTER
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(110, 110, 125); textSize = 11f; textAlign = Paint.Align.CENTER
                        }
                        val likes = if (photo.likes.isNotEmpty()) "   ♥ ${photo.likes.size}" else ""
                        drawText(photo.uploaderName + likes, pw / 2f, ph - margin - 30f, cp)
                        drawText(day.format(photo.createdAt), pw / 2f, ph - margin - 12f, sp)
                        drawText("${i + 1}", pw - margin, ph - 16f, sp)
                    }
                    doc.finishPage(page)
                    bmp.recycle()
                }

                val file = File(Sharer.creationsDir(context), "Libro_${AlbumVideoMaker.safeName(title)}.pdf")
                FileOutputStream(file).use { doc.writeTo(it) }
                file
            } finally {
                doc.close()
                work.deleteRecursively()
            }
        }
}
