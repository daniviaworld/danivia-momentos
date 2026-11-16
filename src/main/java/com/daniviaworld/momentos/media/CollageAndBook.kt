package com.daniviaworld.momentos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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

/** Opciones del collage elegidas por el usuario. */
data class CollageOptions(
    /** "grid", "featured", "strip", "polaroid", "heart", "circles" */
    val layout: String = "grid",
    /** "4:5", "1:1", "9:16" */
    val format: String = "4:5",
    /** "noche", "blanco", "rosa", "aurora", "papel", "foto" */
    val background: String = "noche",
    val showTitle: Boolean = true,
    /** Marco de [com.daniviaworld.momentos.image.Frames] alrededor de todo el collage ("" = ninguno). */
    val frame: String = "",
)

/** Collage de hasta 9 fotos elegidas, con varias formas, formatos y fondos. */
object CollageMaker {

    const val MAX_PHOTOS = 9

    val layouts = listOf(
        "grid" to "▦ Cuadrícula",
        "featured" to "🌟 Una destacada",
        "strip" to "🎞️ Fotomatón",
        "polaroid" to "📷 Polaroids",
        "heart" to "❤️ Corazón",
        "circles" to "⭕ Círculos",
    )
    val formats = listOf("4:5" to "📱 4:5 (WhatsApp)", "1:1" to "⬛ Cuadrado", "9:16" to "📲 Estado / historia")
    val backgrounds = listOf(
        "noche" to "🌌 Noche",
        "blanco" to "⬜ Blanco",
        "papel" to "📜 Papel",
        "rosa" to "🌸 Rosa",
        "aurora" to "🌈 Aurora",
        "foto" to "🔮 Foto difuminada",
    )

    /** Las fotos que se proponen al empezar: las que más gustan (hasta 6). */
    fun suggested(photos: List<Photo>): List<Photo> = best(photos, 6)

    fun size(format: String, width: Int = 1080): Pair<Int, Int> = when (format) {
        "1:1" -> width to width
        "9:16" -> width to (width * 16 / 9)
        else -> width to (width * 5 / 4)
    }

    /** Collage final, a buena resolución. */
    suspend fun make(context: Context, photos: List<Photo>, title: String, options: CollageOptions): File = withContext(Dispatchers.IO) {
        val chosen = photos.filter { !it.isVideo }.take(MAX_PHOTOS)
        if (chosen.isEmpty()) throw IllegalArgumentException("Elige al menos una foto para el collage.")
        val work = File(Sharer.creationsDir(context), "collage_tmp").apply { mkdirs() }
        val bitmaps = chosen.mapNotNull { loadPhoto(work, it, 1000) }
        if (bitmaps.isEmpty()) throw IllegalArgumentException("No se pudieron descargar las fotos.")
        val (w, h) = size(options.format)
        val collage = render(bitmaps, options, w, h, title, AlbumVideoMaker.dateRange(chosen))
        val out = com.daniviaworld.momentos.image.Frames.apply(collage, options.frame)
        if (out !== collage) collage.recycle()
        val file = File(Sharer.creationsDir(context), "Collage_${AlbumVideoMaker.safeName(title)}.jpg")
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        out.recycle()
        bitmaps.forEach { it.recycle() }
        work.deleteRecursively()
        file
    }

    /** Dibuja el collage (se usa también para la vista previa, con fotos pequeñas). */
    fun render(bitmaps: List<Bitmap>, o: CollageOptions, w: Int, h: Int, title: String, dates: String): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val k = w / 1080f // escala (la vista previa es más pequeña)
        val dark = background(c, o.background, w, h, bitmaps.firstOrNull())

        val header = if (o.showTitle) 170f * k else 40f * k
        val footer = 70f * k
        val gap = 14f * k
        val area = RectF(gap * 2, header, w - gap * 2, h - footer)
        val rnd = kotlin.random.Random(bitmaps.size * 131 + w)

        when (o.layout) {
            "polaroid" -> polaroids(c, bitmaps, area, k, rnd)
            "heart" -> heart(c, bitmaps, area, gap, dark)
            "circles" -> circles(c, bitmaps, area, gap, dark)
            else -> {
                val rects = when (o.layout) {
                    "featured" -> featured(bitmaps.size, area, gap)
                    "strip" -> strip(bitmaps.size, area, gap)
                    else -> grid(bitmaps.size, area, gap)
                }
                rects.forEachIndexed { i, r -> roundPhoto(c, bitmaps[i], r, 22f * k, if (dark) 0 else Color.WHITE, 6f * k) }
            }
        }

        val ink = if (dark) Color.WHITE else Color.rgb(40, 34, 60)
        if (o.showTitle) {
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                textSize = 62f * k
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val line = AlbumVideoMaker.wrap(title, t, w - 80f * k).firstOrNull() ?: title
            c.drawText(line, w / 2f, 92f * k, t)
            val d = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (dark) Color.rgb(255, 176, 61) else Color.rgb(224, 69, 123)
                textSize = 30f * k
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c.drawText(dates.uppercase(spanish), w / 2f, 140f * k, d)
        }
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.argb(150, 255, 255, 255) else Color.argb(150, 40, 34, 60)
            textSize = 26f * k
            textAlign = Paint.Align.CENTER
        }
        c.drawText("DANIVIA Momentos", w / 2f, h - 26f * k, f)
        return out
    }

    /** Pinta el fondo. Devuelve true si es oscuro (para el color del texto). */
    private fun background(c: Canvas, key: String, w: Int, h: Int, first: Bitmap?): Boolean {
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        fun grad(vararg colors: Int) {
            c.drawRect(full, Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP) })
        }
        return when (key) {
            "blanco" -> { c.drawColor(Color.rgb(250, 250, 248)); false }
            "papel" -> {
                c.drawColor(Color.rgb(246, 239, 225))
                val p = Paint()
                val r = kotlin.random.Random(7)
                repeat(2500) {
                    p.color = if (r.nextBoolean()) Color.argb(14, 0, 0, 0) else Color.argb(22, 255, 255, 255)
                    val x = r.nextFloat() * w
                    val y = r.nextFloat() * h
                    c.drawRect(x, y, x + 3f, y + 3f, p)
                }
                false
            }
            "rosa" -> { grad(Color.rgb(255, 214, 231), Color.rgb(255, 236, 214), Color.rgb(232, 214, 255)); false }
            "aurora" -> { grad(Color.rgb(75, 35, 232), Color.rgb(214, 64, 159), Color.rgb(255, 154, 61)); true }
            "foto" -> {
                if (first != null) {
                    val tiny = Bitmap.createScaledBitmap(first, max(1, first.width / 30), max(1, first.height / 30), true)
                    AlbumVideoMaker.drawCrop(c, tiny, full, Paint(Paint.FILTER_BITMAP_FLAG))
                    tiny.recycle()
                    c.drawColor(Color.argb(90, 0, 0, 0))
                } else c.drawColor(Color.rgb(17, 19, 37))
                true
            }
            else -> { grad(Color.rgb(17, 19, 37), Color.rgb(36, 22, 70)); true }
        }
    }

    private fun roundPhoto(c: Canvas, bmp: Bitmap, r: RectF, radius: Float, border: Int, borderW: Float) {
        if (border != 0) {
            val b = RectF(r.left - borderW, r.top - borderW, r.right + borderW, r.bottom + borderW)
            c.drawRoundRect(b, radius + borderW, radius + borderW, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = border
                setShadowLayer(borderW * 2, 0f, borderW, Color.argb(60, 0, 0, 0))
            })
        }
        c.save()
        c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
        AlbumVideoMaker.drawCrop(c, bmp, r)
        c.restore()
    }

    /** Cuadrícula que se adapta al número de fotos (hasta 9). */
    private fun grid(n: Int, a: RectF, g: Float): List<RectF> {
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
            6 -> (0 until 6).map { i ->
                val col = i % 2; val row = i / 2
                val top = t + row * (h3 + g)
                if (col == 0) RectF(l, top, mx - g / 2, top + h3) else RectF(mx + g / 2, top, r, top + h3)
            }
            else -> rows(n, 3, a, g)
        }
    }

    /** Filas de [perRow] fotos; la última fila se ensancha si tiene menos. */
    private fun rows(n: Int, perRow: Int, a: RectF, g: Float): List<RectF> {
        val rowsCount = (n + perRow - 1) / perRow
        val rh = (a.height() - g * (rowsCount - 1)) / rowsCount
        val out = mutableListOf<RectF>()
        var i = 0
        for (row in 0 until rowsCount) {
            val inRow = min(perRow, n - i)
            val cw = (a.width() - g * (inRow - 1)) / inRow
            val top = a.top + row * (rh + g)
            for (col in 0 until inRow) {
                val left = a.left + col * (cw + g)
                out += RectF(left, top, left + cw, top + rh)
            }
            i += inRow
        }
        return out
    }

    /** Una foto grande arriba y el resto en pequeño debajo. */
    private fun featured(n: Int, a: RectF, g: Float): List<RectF> {
        if (n == 1) return listOf(RectF(a))
        val bigH = a.height() * (if (n - 1 > 4) 0.5f else 0.6f)
        val big = RectF(a.left, a.top, a.right, a.top + bigH)
        val rest = RectF(a.left, a.top + bigH + g, a.right, a.bottom)
        val perRow = if (n - 1 <= 4) n - 1 else 4
        return listOf(big) + rows(n - 1, perRow, rest, g)
    }

    /** Tira de fotomatón: una o dos columnas de fotos apiladas. */
    private fun strip(n: Int, a: RectF, g: Float): List<RectF> {
        val cols = if (n <= 4) 1 else 2
        val colW = if (cols == 1) a.width() * 0.62f else (a.width() - g * 3) / 2
        val perCol = (n + cols - 1) / cols
        val out = mutableListOf<RectF>()
        for (i in 0 until n) {
            val col = i / perCol
            val row = i % perCol
            val countInCol = if (col == cols - 1) n - perCol * col else perCol
            val ch = (a.height() - g * (countInCol - 1)) / countInCol
            val left = if (cols == 1) a.centerX() - colW / 2 else a.left + g + col * (colW + g)
            val top = a.top + row * (ch + g)
            out += RectF(left, top, left + colW, top + ch)
        }
        return out
    }

    /** Polaroids esparcidas y algo giradas, con sombra. */
    private fun polaroids(c: Canvas, bitmaps: List<Bitmap>, a: RectF, k: Float, rnd: kotlin.random.Random) {
        val n = bitmaps.size
        val cols = when { n <= 1 -> 1; n <= 4 -> 2; else -> 3 }
        val rowsCount = (n + cols - 1) / cols
        val cellW = a.width() / cols
        val cellH = a.height() / rowsCount
        val side = min(cellW, cellH / 1.18f) * 1.05f
        bitmaps.forEachIndexed { i, bmp ->
            val col = i % cols
            val row = i / cols
            val cx = a.left + cellW * (col + 0.5f) + (rnd.nextFloat() - 0.5f) * cellW * 0.15f
            val cy = a.top + cellH * (row + 0.5f) + (rnd.nextFloat() - 0.5f) * cellH * 0.12f
            val pad = side * 0.06f
            val photoSide = side - 2 * pad
            val card = RectF(cx - side / 2, cy - (side + side * 0.18f) / 2, cx + side / 2, cy + (side + side * 0.18f) / 2)
            c.save()
            c.rotate((rnd.nextFloat() - 0.5f) * 22f, cx, cy)
            c.drawRect(card, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(252, 251, 247)
                setShadowLayer(14f * k, 0f, 6f * k, Color.argb(110, 0, 0, 0))
            })
            val pr = RectF(card.left + pad, card.top + pad, card.left + pad + photoSide, card.top + pad + photoSide)
            AlbumVideoMaker.drawCrop(c, bmp, pr)
            c.restore()
        }
    }

    /** Las fotos rellenan un corazón grande. */
    private fun heart(c: Canvas, bitmaps: List<Bitmap>, a: RectF, g: Float, dark: Boolean) {
        val s = min(a.width(), a.height() * 1.05f)
        val cx = a.centerX()
        val cy = a.centerY() + s * 0.04f
        val path = Path().apply {
            moveTo(cx, cy + s * 0.42f)
            cubicTo(cx - s * 0.15f, cy + s * 0.28f, cx - s * 0.5f, cy + s * 0.08f, cx - s * 0.5f, cy - s * 0.14f)
            cubicTo(cx - s * 0.5f, cy - s * 0.46f, cx - s * 0.08f, cy - s * 0.5f, cx, cy - s * 0.27f)
            cubicTo(cx + s * 0.08f, cy - s * 0.5f, cx + s * 0.5f, cy - s * 0.46f, cx + s * 0.5f, cy - s * 0.14f)
            cubicTo(cx + s * 0.5f, cy + s * 0.08f, cx + s * 0.15f, cy + s * 0.28f, cx, cy + s * 0.42f)
            close()
        }
        val box = RectF(cx - s / 2, cy - s * 0.5f, cx + s / 2, cy + s * 0.42f)
        val cols = when { bitmaps.size <= 2 -> 2; bitmaps.size <= 6 -> 3; else -> 4 }
        val rowsCount = cols
        val cw = box.width() / cols
        val ch = box.height() / rowsCount
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(g * 2, 0f, g, Color.argb(90, 0, 0, 0))
        })
        c.save()
        c.clipPath(path)
        var i = 0
        for (row in 0 until rowsCount) for (col in 0 until cols) {
            val r = RectF(box.left + col * cw + g / 3, box.top + row * ch + g / 3, box.left + (col + 1) * cw - g / 3, box.top + (row + 1) * ch - g / 3)
            AlbumVideoMaker.drawCrop(c, bitmaps[i % bitmaps.size], r)
            i++
        }
        c.restore()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = g * 0.8f
            color = if (dark) Color.WHITE else Color.rgb(232, 23, 79)
        })
    }

    /** Una foto redonda grande en el centro y las demás alrededor. */
    private fun circles(c: Canvas, bitmaps: List<Bitmap>, a: RectF, g: Float, dark: Boolean) {
        val ring = if (dark) Color.WHITE else Color.rgb(255, 255, 255)
        fun disc(bmp: Bitmap, x: Float, y: Float, r: Float) {
            c.drawCircle(x, y, r + g * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ring
                setShadowLayer(g * 1.5f, 0f, g * 0.6f, Color.argb(90, 0, 0, 0))
            })
            c.save()
            c.clipPath(Path().apply { addCircle(x, y, r, Path.Direction.CW) })
            AlbumVideoMaker.drawCrop(c, bmp, RectF(x - r, y - r, x + r, y + r))
            c.restore()
        }
        val cx = a.centerX()
        val cy = a.centerY()
        val minSide = min(a.width(), a.height())
        if (bitmaps.size == 1) {
            disc(bitmaps[0], cx, cy, minSide * 0.45f)
            return
        }
        val others = bitmaps.size - 1
        val bigR = minSide * (if (others <= 4) 0.27f else 0.23f)
        val smallR = minSide * (if (others <= 4) 0.17f else 0.13f)
        val rx = a.width() / 2 - smallR - g
        val ry = a.height() / 2 - smallR - g
        for (i in 0 until others) {
            val ang = -PI / 2 + i * 2 * PI / others
            disc(bitmaps[i + 1], cx + (rx * cos(ang)).toFloat(), cy + (ry * sin(ang)).toFloat(), smallR)
        }
        disc(bitmaps[0], cx, cy, bigR)
    }
}

/**
 * Libro de fotos en PDF (A4): portada y una foto por página con quién la subió y cuándo.
 * [theme] = diseño de las páginas (uno de los marcos de Frames; "" = página blanca clásica).
 */
object PhotoBookMaker {

    suspend fun make(context: Context, photos: List<Photo>, title: String, theme: String = "", onProgress: (Float) -> Unit): File =
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
                if (theme.isNotBlank()) {
                    loadPhoto(work, chosen.first(), 1100)?.let { bmp ->
                        cover.canvas.drawColor(Color.WHITE)
                        com.daniviaworld.momentos.image.Frames.drawPage(cover.canvas, pw.toFloat(), ph.toFloat(), bmp, theme, chosen.first().createdAt)
                        bmp.recycle()
                    }
                    titleBand(cover.canvas, pw.toFloat(), title, AlbumVideoMaker.dateRange(chosen))
                } else cover.canvas.apply {
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
                chosen.forEachIndexed { i, photo ->
                    withContext(Dispatchers.Main) { onProgress((i + 1f) / chosen.size) }
                    val bmp = loadPhoto(work, photo, 1100) ?: return@forEachIndexed
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 2).create())
                    drawPhotoPage(page.canvas, bmp, photo.uploaderName, photo.likes.size, photo.createdAt, i + 1, theme)
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

    /** Banda con el título en la portada decorada. */
    private fun titleBand(c: Canvas, pw: Float, title: String, dates: String) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lines = AlbumVideoMaker.wrap(title, tp, pw - 110f).take(2)
        val h = 24f + lines.size * 36f + 22f
        val band = RectF(36f, 34f, pw - 36f, 34f + h)
        c.drawRoundRect(band, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 17, 19, 37)
            setShadowLayer(8f, 0f, 3f, Color.argb(80, 0, 0, 0))
        })
        var y = band.top + 44f
        lines.forEach { c.drawText(it, pw / 2f, y, tp); y += 36f }
        val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 176, 61); textSize = 12f; textAlign = Paint.Align.CENTER; letterSpacing = 0.12f
        }
        c.drawText(dates.uppercase(spanish), pw / 2f, band.bottom - 14f, dp)
    }

    /** Quién y cuándo, en una pastilla legible sobre cualquier diseño. */
    private fun captionPill(c: Canvas, pw: Float, ph: Float, text: String, number: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 10.5f; textAlign = Paint.Align.CENTER }
        val label = "$text   ·   $number"
        val tw = min(p.measureText(label), pw - 80f)
        val r = RectF(pw / 2 - tw / 2 - 14f, ph - 30f, pw / 2 + tw / 2 + 14f, ph - 10f)
        c.drawRoundRect(r, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 20, 18, 34) })
        c.drawText(label, pw / 2, r.bottom - 6.5f, p)
    }

    private const val PW = 595f
    private const val PH = 842f

    /** Una página con una foto (la del PDF y la de la vista previa usan esto mismo). */
    fun drawPhotoPage(c: Canvas, bmp: Bitmap, uploader: String, likes: Int, date: Long, number: Int, theme: String) {
        val pw = PW
        val ph = PH
        val day = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", spanish)
        c.drawColor(Color.WHITE)
        if (theme.isNotBlank()) {
            com.daniviaworld.momentos.image.Frames.drawPage(c, pw, ph, bmp, theme, date)
            val heart = if (likes > 0) "  ♥ $likes" else ""
            captionPill(c, pw, ph, "$uploader$heart  ·  ${day.format(date)}", number)
            return
        }
        val margin = 36f
        val maxW = pw - 2 * margin
        val maxH = ph - 2 * margin - 70f
        val s = min(maxW / bmp.width, maxH / bmp.height)
        val dw = bmp.width * s
        val dh = bmp.height * s
        val left = (pw - dw) / 2
        val top = margin + (maxH - dh) / 2
        c.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 40); textSize = 14f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 110, 125); textSize = 11f; textAlign = Paint.Align.CENTER
        }
        val heart = if (likes > 0) "   ♥ $likes" else ""
        c.drawText(uploader + heart, pw / 2f, ph - margin - 30f, cp)
        c.drawText(day.format(date), pw / 2f, ph - margin - 12f, sp)
        c.drawText("$number", pw - margin, ph - 16f, sp)
    }

    /** Vista previa de una página del libro, a [width] píxeles de ancho. */
    fun previewPage(sample: Bitmap, theme: String, width: Int, uploader: String, date: Long): Bitmap {
        val height = (width * PH / PW).toInt()
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(width / PW, height / PH)
        drawPhotoPage(c, sample, uploader, 0, date, 1, theme)
        return out
    }
}
