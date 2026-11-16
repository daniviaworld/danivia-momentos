package com.daniviaworld.momentos.image

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Marcos para las fotos. Todo se dibuja a mano (vectores), así se ve nítido a cualquier tamaño:
 * filigranas, art déco, acebo, flores, globos, banderines, washi tape, sellos de correos…
 * Las medidas van en proporción al lado corto de la foto ([L.u]), así la vista previa
 * pequeña y la foto final grande salen idénticas.
 */
object Frames {

    data class Frame(val key: String, val name: String, val emoji: String, val category: String) {
        val label: String get() = "$emoji $name"
    }

    const val MODERNOS = "Modernos"
    const val CLASICOS = "Clásicos"
    const val FIESTAS = "Fiestas"
    const val ESTACIONES = "Estaciones"
    const val PEQUES = "Viajes y peques"
    val categories = listOf(MODERNOS, CLASICOS, FIESTAS, ESTACIONES, PEQUES)

    val all = listOf(
        Frame("", "Sin marco", "⬜", ""),
        Frame("cristal", "Cristal", "🔮", MODERNOS),
        Frame("neon", "Neón", "💜", MODERNOS),
        Frame("aurora", "Aurora", "🌈", MODERNOS),
        Frame("minimal", "Minimal", "◻️", MODERNOS),
        Frame("revista", "Portada", "📰", MODERNOS),
        Frame("washi", "Scrapbook", "🎀", MODERNOS),
        Frame("polaroid", "Polaroid", "📷", CLASICOS),
        Frame("galeria", "Galería", "🖼️", CLASICOS),
        Frame("dorado", "Art déco", "✨", CLASICOS),
        Frame("vintage", "Vintage", "📜", CLASICOS),
        Frame("pelicula", "Película", "🎞️", CLASICOS),
        Frame("cumple", "Cumpleaños", "🎂", FIESTAS),
        Frame("corazones", "Amor", "💕", FIESTAS),
        Frame("boda", "Boda", "💍", FIESTAS),
        Frame("fiesta", "Fiesta", "🎉", FIESTAS),
        Frame("navidad", "Navidad", "🎄", FIESTAS),
        Frame("halloween", "Halloween", "🎃", FIESTAS),
        Frame("primavera", "Primavera", "🌸", ESTACIONES),
        Frame("verano", "Verano", "☀️", ESTACIONES),
        Frame("otono", "Otoño", "🍂", ESTACIONES),
        Frame("invierno", "Invierno", "❄️", ESTACIONES),
        Frame("postal", "Postal", "✈️", PEQUES),
        Frame("cole", "Cole", "✏️", PEQUES),
        Frame("bebe", "Bebé", "🍼", PEQUES),
        Frame("noche", "Noche mágica", "🌙", PEQUES),
    )

    fun apply(src: Bitmap, key: String): Bitmap {
        if (key.isBlank()) return src
        return try {
            when (key) {
                "cristal" -> cristal(src)
                "neon" -> neon(src)
                "aurora" -> aurora(src)
                "minimal" -> minimal(src)
                "revista" -> revista(src)
                "washi" -> washi(src)
                "polaroid" -> polaroid(src)
                "galeria" -> galeria(src)
                "dorado" -> dorado(src)
                "vintage" -> vintage(src)
                "pelicula" -> pelicula(src)
                "cumple" -> cumple(src)
                "corazones" -> corazones(src)
                "boda" -> boda(src)
                "fiesta" -> fiesta(src)
                "navidad" -> navidad(src)
                "halloween" -> halloween(src)
                "primavera" -> primavera(src)
                "verano" -> verano(src)
                "otono" -> otono(src)
                "invierno" -> invierno(src)
                "postal" -> postal(src)
                "cole" -> cole(src)
                "bebe" -> bebe(src)
                "noche" -> noche(src)
                else -> src
            }
        } catch (_: Throwable) {
            src // sin memoria o lo que sea: mejor la foto sin marco que nada
        }
    }

    // ───────────────────────────── MODERNOS ─────────────────────────────

    private fun cristal(src: Bitmap): Bitmap {
        val l = layout(src, "cristal", 0.09f, 0.09f, 0.2f)
        blurredBackground(l, src)
        l.c.drawColor(Color.argb(60, 0, 0, 0))
        val sheen = Paint().apply {
            shader = LinearGradient(0f, 0f, l.w * 0.7f, l.h * 0.7f, Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        l.c.drawRect(0f, 0f, l.w, l.h, sheen)
        photo(l, src, radius = l.u * 0.04f, shadow = l.u * 0.05f, border = Color.argb(150, 255, 255, 255), borderW = l.u * 0.007f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.52f
        text(l, today("d 'de' MMMM yyyy"), l.w / 2, y, l.u * 0.055f, Color.WHITE, "sans-serif-light", shadow = true)
        text(l, "♡  momentos", l.w / 2, y + l.u * 0.06f, l.u * 0.032f, Color.argb(200, 255, 255, 255), "sans-serif-medium", spacing = 0.25f)
        return l.out
    }

    private fun neon(src: Bitmap): Bitmap {
        val l = layout(src, "neon", 0.11f, 0.11f, 0.22f)
        gradient(l, col(0xFF0B0820), col(0xFF1B0B33), vertical = true)
        // rejilla tipo synthwave
        val grid = stroke(Color.argb(28, 255, 80, 200), l.u * 0.002f)
        var x = 0f
        while (x < l.w) { l.c.drawLine(x, 0f, x, l.h, grid); x += l.u * 0.06f }
        var y = 0f
        while (y < l.h) { l.c.drawLine(0f, y, l.w, y, grid); y += l.u * 0.06f }

        val pink = col(0xFFFF3DB8)
        val cyan = col(0xFF3DF2FF)
        neonRect(l, inflate(l.photo, l.u * 0.05f), l.u * 0.06f, cyan, l.u * 0.006f)
        neonRect(l, inflate(l.photo, l.u * 0.022f), l.u * 0.04f, pink, l.u * 0.01f)
        photo(l, src, radius = l.u * 0.025f)

        val yy = l.photo.bottom + (l.h - l.photo.bottom) * 0.58f
        text(l, "momentos", l.w / 2, yy, l.u * 0.09f, Color.WHITE, "cursive", Typeface.BOLD, glow = pink)
        text(l, today("dd · MM · yyyy"), l.w / 2, yy + l.u * 0.055f, l.u * 0.03f, cyan, "monospace", glow = cyan)
        return l.out
    }

    private fun aurora(src: Bitmap): Bitmap {
        val l = layout(src, "aurora", 0.11f, 0.11f, 0.11f)
        gradient(l, col(0xFF4B23E8), col(0xFFD6409F), col(0xFFFF9A3D))
        glow(l, l.w * 0.1f, l.h * 0.85f, l.u * 0.8f, Color.argb(170, 46, 230, 214))
        glow(l, l.w * 0.9f, l.h * 0.1f, l.u * 0.7f, Color.argb(150, 255, 230, 90))
        glow(l, l.w * 0.85f, l.h * 0.9f, l.u * 0.6f, Color.argb(140, 80, 120, 255))
        scatter(l, 26) { x, yy, r -> l.c.drawPath(starPath(x, yy, l.u * (0.012f + r * 0.02f), 0.22f, 4), fill(Color.argb(220, 255, 255, 255))) }
        photo(l, src, radius = l.u * 0.05f, shadow = l.u * 0.05f, border = Color.WHITE, borderW = l.u * 0.014f)
        return l.out
    }

    private fun minimal(src: Bitmap): Bitmap {
        val l = layout(src, "minimal", 0.07f, 0.07f, 0.2f)
        l.c.drawColor(col(0xFFFBFBF9))
        l.c.drawRect(inflate(l.photo, l.u * 0.004f), stroke(col(0xFF1A1A1A), l.u * 0.002f))
        photo(l, src)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.56f
        text(l, "MOMENTOS", l.photo.left, y, l.u * 0.04f, col(0xFF1A1A1A), "sans-serif-medium", align = Paint.Align.LEFT, spacing = 0.35f)
        text(l, today("d.MM.yyyy"), l.photo.right, y, l.u * 0.036f, col(0xFF8A8A8A), "sans-serif-light", align = Paint.Align.RIGHT)
        l.c.drawLine(l.photo.left, y + l.u * 0.03f, l.photo.left + l.u * 0.08f, y + l.u * 0.03f, stroke(col(0xFFE0457B), l.u * 0.005f))
        return l.out
    }

    private fun revista(src: Bitmap): Bitmap {
        val l = layout(src, "revista", 0.03f)
        l.c.drawColor(Color.WHITE)
        photo(l, src)
        val p = l.photo
        val top = Paint().apply {
            shader = LinearGradient(0f, p.top, 0f, p.top + p.height() * 0.35f, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        l.c.drawRect(p.left, p.top, p.right, p.top + p.height() * 0.35f, top)
        val bottom = Paint().apply {
            shader = LinearGradient(0f, p.bottom - p.height() * 0.3f, 0f, p.bottom, Color.TRANSPARENT, Color.argb(170, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        l.c.drawRect(p.left, p.bottom - p.height() * 0.3f, p.right, p.bottom, bottom)

        text(l, "MOMENTOS", p.centerX(), p.top + l.u * 0.17f, l.u * 0.16f, Color.WHITE, "serif", Typeface.BOLD, shadow = true, spacing = 0.06f, maxWidth = p.width() * 0.92f)
        text(l, "EDICIÓN FAMILIAR  ·  " + today("MMMM yyyy").uppercase(Locale("es", "ES")), p.centerX(), p.top + l.u * 0.225f, l.u * 0.028f, Color.WHITE, "sans-serif-medium", spacing = 0.2f)
        text(l, "La foto", p.left + l.u * 0.05f, p.bottom - l.u * 0.13f, l.u * 0.07f, Color.WHITE, "sans-serif-black", align = Paint.Align.LEFT, shadow = true)
        text(l, "del día", p.left + l.u * 0.05f, p.bottom - l.u * 0.06f, l.u * 0.07f, col(0xFFFFD23F), "sans-serif-black", align = Paint.Align.LEFT, shadow = true)
        // código de barras
        val bw = l.u * 0.16f
        val bh = l.u * 0.08f
        val br = RectF(p.right - bw - l.u * 0.04f, p.bottom - bh - l.u * 0.04f, p.right - l.u * 0.04f, p.bottom - l.u * 0.04f)
        l.c.drawRect(br, fill(Color.WHITE))
        var x = br.left + bw * 0.08f
        val bar = fill(Color.BLACK)
        while (x < br.right - bw * 0.08f) {
            val w = bw * (0.012f + l.rnd.nextFloat() * 0.03f)
            l.c.drawRect(x, br.top + bh * 0.12f, min(x + w, br.right - bw * 0.08f), br.bottom - bh * 0.28f, bar)
            x += w + bw * (0.01f + l.rnd.nextFloat() * 0.025f)
        }
        val day = Calendar.getInstance().apply { timeInMillis = dateOverride.get() ?: System.currentTimeMillis() }.get(Calendar.DAY_OF_YEAR)
        text(l, "Nº $day", br.centerX(), br.bottom - bh * 0.06f, bh * 0.2f, Color.BLACK, "monospace")
        return l.out
    }

    private fun washi(src: Bitmap): Bitmap {
        val l = layout(src, "washi", 0.12f, 0.15f, 0.22f)
        paper(l, col(0xFFF6EFE1))
        // garabatos alrededor
        val inks = intArrayOf(col(0xFFE58FA8), col(0xFF7FC8B6), col(0xFFF2B65A), col(0xFF8FA7E5))
        scatter(l, 22) { x, y, r ->
            val ink = inks[l.rnd.nextInt(inks.size)]
            val s = l.u * (0.02f + r * 0.025f)
            if (l.rnd.nextBoolean()) l.c.drawPath(starPath(x, y, s, 0.45f), stroke(ink, l.u * 0.004f))
            else l.c.drawPath(heartPath(x, y, s * 1.6f), stroke(ink, l.u * 0.004f))
        }
        photo(l, src, shadow = l.u * 0.035f, border = Color.WHITE, borderW = l.u * 0.03f)
        val w = l.u * 0.34f
        val h = l.u * 0.09f
        tape(l.c, l.photo.left + l.u * 0.03f, l.photo.top + l.u * 0.01f, w, h, -38f, col(0xFFF6A9C0), Color.argb(120, 255, 255, 255))
        tape(l.c, l.photo.right - l.u * 0.03f, l.photo.top + l.u * 0.01f, w, h, 38f, col(0xFF9ADBC8), null)
        tape(l.c, l.photo.right - l.u * 0.04f, l.photo.bottom - l.u * 0.01f, w * 0.8f, h, -32f, col(0xFFFFD27A), Color.argb(110, 255, 255, 255))
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.55f
        text(l, "nuestros momentos ♡", l.w / 2, y, l.u * 0.06f, col(0xFF5B4A6B), "casual")
        text(l, today(), l.w / 2, y + l.u * 0.05f, l.u * 0.03f, col(0xFF9A8AA8), "casual")
        return l.out
    }

    // ───────────────────────────── CLÁSICOS ─────────────────────────────

    private fun polaroid(src: Bitmap): Bitmap {
        val l = layout(src, "polaroid", 0.06f, 0.06f, 0.26f)
        paper(l, col(0xFFFAF8F2), grain = 1500)
        val sheen = Paint().apply {
            shader = LinearGradient(0f, 0f, l.w, l.h, Color.argb(30, 255, 255, 255), Color.argb(20, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        l.c.drawRect(0f, 0f, l.w, l.h, sheen)
        l.c.drawRect(inflate(l.photo, l.u * 0.002f), fill(Color.argb(40, 0, 0, 0)))
        photo(l, src)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.55f
        text(l, today("d 'de' MMMM, yyyy"), l.w / 2, y, l.u * 0.07f, col(0xFF2B3A67), "casual")
        l.c.drawPath(heartPath(l.w / 2 + l.u * 0.36f, y - l.u * 0.06f, l.u * 0.06f), fill(col(0xFFE0457B)))
        return l.out
    }

    private fun galeria(src: Bitmap): Bitmap {
        val l = layout(src, "galeria", 0.2f)
        val wood = l.u * 0.075f
        gradient(l, col(0xFF6B4528), col(0xFF3B2414), col(0xFF5A3A22))
        val grain = stroke(Color.argb(40, 20, 10, 0), l.u * 0.002f)
        repeat(90) {
            val y0 = l.rnd.nextFloat() * l.h
            val path = Path().apply { moveTo(0f, y0); quadTo(l.w / 2, y0 + (l.rnd.nextFloat() - 0.5f) * l.u * 0.05f, l.w, y0) }
            l.c.drawPath(path, grain)
        }
        // biselado de la madera: luz arriba/izquierda, sombra abajo/derecha
        val light = fill(Color.argb(45, 255, 230, 200))
        val dark = fill(Color.argb(70, 0, 0, 0))
        poly(l.c, light, 0f, 0f, l.w, 0f, l.w - wood, wood, wood, wood)
        poly(l.c, light, 0f, 0f, wood, wood, wood, l.h - wood, 0f, l.h)
        poly(l.c, dark, 0f, l.h, wood, l.h - wood, l.w - wood, l.h - wood, l.w, l.h)
        poly(l.c, dark, l.w, 0f, l.w, l.h, l.w - wood, l.h - wood, l.w - wood, wood)
        // paspartú
        val mat = RectF(wood, wood, l.w - wood, l.h - wood)
        l.c.drawRect(mat, fill(col(0xFFF3EEE4)))
        val inner = Paint().apply {
            shader = LinearGradient(0f, mat.top, 0f, mat.top + l.u * 0.03f, Color.argb(60, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        l.c.drawRect(mat.left, mat.top, mat.right, mat.top + l.u * 0.03f, inner)
        // corte en bisel del paspartú
        l.c.drawRect(inflate(l.photo, l.u * 0.01f), fill(Color.WHITE))
        l.c.drawRect(inflate(l.photo, l.u * 0.01f), stroke(Color.argb(50, 0, 0, 0), l.u * 0.002f))
        photo(l, src)
        // plaquita dorada
        val pw = l.u * 0.22f
        val ph = l.u * 0.04f
        val plate = RectF(l.w / 2 - pw / 2, l.photo.bottom + l.u * 0.035f, l.w / 2 + pw / 2, l.photo.bottom + l.u * 0.035f + ph)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(plate.left, 0f, plate.right, 0f, intArrayOf(col(0xFFB8862B), col(0xFFF6E27A), col(0xFFB8862B)), null, Shader.TileMode.CLAMP)
        }
        l.c.drawRoundRect(plate, ph * 0.15f, ph * 0.15f, gold)
        text(l, today("MMMM yyyy").uppercase(Locale("es", "ES")), plate.centerX(), plate.centerY() + ph * 0.18f, ph * 0.45f, col(0xFF4A3510), "serif", Typeface.BOLD, spacing = 0.15f)
        return l.out
    }

    private fun dorado(src: Bitmap): Bitmap {
        val l = layout(src, "dorado", 0.12f, 0.12f, 0.12f)
        gradient(l, col(0xFF101012), col(0xFF1E1B16), col(0xFF101012))
        val goldShader = LinearGradient(0f, 0f, l.w, l.h, intArrayOf(col(0xFFB8862B), col(0xFFF6E27A), col(0xFFB8862B), col(0xFFEAC86A)), null, Shader.TileMode.MIRROR)
        val line1 = stroke(Color.WHITE, l.u * 0.006f).apply { shader = goldShader }
        val line2 = stroke(Color.WHITE, l.u * 0.002f).apply { shader = goldShader }
        l.c.drawRect(inset(l, l.u * 0.03f), line1)
        l.c.drawRect(inset(l, l.u * 0.045f), line2)
        val gold = col(0xFFE2BE62)
        corners(l, inset(l, l.u * 0.03f), l.u * 0.16f) { c -> decoFan(c, gold) }
        // rombos en medio de cada lado
        val d = l.u * 0.025f
        listOf(l.w / 2 to l.u * 0.0375f, l.w / 2 to l.h - l.u * 0.0375f, l.u * 0.0375f to l.h / 2, l.w - l.u * 0.0375f to l.h / 2).forEach { (x, y) ->
            poly(l.c, fill(gold), x, y - d, x + d, y, x, y + d, x - d, y)
        }
        photo(l, src, border = gold, borderW = l.u * 0.006f)
        return l.out
    }

    private fun vintage(src: Bitmap): Bitmap {
        val l = layout(src, "vintage", 0.13f, 0.13f, 0.22f)
        paper(l, col(0xFFEBDDBF), grain = 3500)
        vignette(l, Color.argb(120, 90, 50, 10))
        val ink = col(0xFF6E4A2A)
        corners(l, inset(l, l.u * 0.035f), l.u * 0.2f) { c -> filigree(c, ink) }
        l.c.drawRect(inset(l, l.u * 0.06f), stroke(Color.argb(110, 110, 74, 42), l.u * 0.002f))
        photo(l, src, shadow = l.u * 0.02f, border = col(0xFFFBF6EA), borderW = l.u * 0.018f)
        // esquinas de álbum antiguo sujetando la foto
        val mount = fill(col(0xFF3A2A1E)).apply { setShadowLayer(l.u * 0.006f, 0f, l.u * 0.003f, Color.argb(120, 0, 0, 0)) }
        corners(l, inflate(l.photo, l.u * 0.018f), l.u * 0.08f) { c ->
            c.drawPath(Path().apply { moveTo(-0.1f, -0.1f); lineTo(1f, -0.1f); lineTo(-0.1f, 1f); close() }, mount)
        }
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.55f
        text(l, "~ " + today("d 'de' MMMM 'de' yyyy") + " ~", l.w / 2, y, l.u * 0.05f, ink, "serif", Typeface.ITALIC)
        return l.out
    }

    private fun pelicula(src: Bitmap): Bitmap {
        val l = layout(src, "pelicula", 0.035f, 0.19f, 0.19f)
        l.c.drawColor(col(0xFF121212))
        val band = l.photo.top
        val hw = l.u * 0.05f
        val hh = l.u * 0.065f
        val gap = hw * 0.85f
        val hole = fill(col(0xFFEFE9DC))
        var x = gap / 2
        while (x + hw < l.w) {
            val yTop = band * 0.38f - hh / 2
            l.c.drawRoundRect(RectF(x, yTop, x + hw, yTop + hh), hw * 0.22f, hw * 0.22f, hole)
            val yBot = l.h - band * 0.38f - hh / 2
            l.c.drawRoundRect(RectF(x, yBot, x + hw, yBot + hh), hw * 0.22f, hw * 0.22f, hole)
            x += hw + gap
        }
        val orange = col(0xFFF2A33A)
        val n = 10 + l.rnd.nextInt(26)
        text(l, "MOMENTOS 400", l.u * 0.06f, band * 0.86f, l.u * 0.028f, orange, "monospace", Typeface.BOLD, align = Paint.Align.LEFT, spacing = 0.15f)
        text(l, "▸ $n", l.w - l.u * 0.06f, band * 0.86f, l.u * 0.028f, orange, "monospace", Typeface.BOLD, align = Paint.Align.RIGHT)
        text(l, "${n}A", l.u * 0.06f, l.h - band * 0.7f, l.u * 0.028f, orange, "monospace", Typeface.BOLD, align = Paint.Align.LEFT)
        text(l, today("dd MM yy"), l.w - l.u * 0.06f, l.h - band * 0.7f, l.u * 0.028f, orange, "monospace", Typeface.BOLD, align = Paint.Align.RIGHT)
        photo(l, src)
        return l.out
    }

    // ───────────────────────────── FIESTAS ─────────────────────────────

    private val party = intArrayOf(
        Color.WHITE, col(0xFFFFE14D), col(0xFF4DE1FF), col(0xFFFF7AC8), col(0xFF8CFF8C), col(0xFFB18CFF),
    )

    private fun cumple(src: Bitmap): Bitmap {
        val l = layout(src, "cumple", 0.11f, 0.22f, 0.22f)
        gradient(l, col(0xFFFF4D8D), col(0xFFFF8A3D), col(0xFFFFC93D))
        confetti(l, 170, party)
        bunting(l, l.u * 0.035f, l.u * 0.1f, intArrayOf(col(0xFF3D7BFF), Color.WHITE, col(0xFF35C98B), col(0xFFFFE14D), col(0xFFB06BFF)))
        photo(l, src, radius = l.u * 0.02f, shadow = l.u * 0.04f, border = Color.WHITE, borderW = l.u * 0.014f)
        val r = l.u * 0.055f
        val bx = l.photo.left + l.u * 0.01f
        val by = l.photo.bottom - l.u * 0.16f
        balloon(l.c, bx - r * 0.9f, by + r * 0.4f, r, col(0xFF3D7BFF))
        balloon(l.c, bx + r * 0.9f, by + r * 0.6f, r, col(0xFF35C98B))
        balloon(l.c, bx, by - r * 0.6f, r * 1.1f, col(0xFFFFE14D))
        val bx2 = l.photo.right - l.u * 0.01f
        balloon(l.c, bx2 + r * 0.9f, by + r * 0.4f, r, col(0xFFB06BFF))
        balloon(l.c, bx2 - r * 0.9f, by + r * 0.6f, r, col(0xFFFF3D6E))
        balloon(l.c, bx2, by - r * 0.6f, r * 1.1f, Color.WHITE)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.62f
        text(l, "¡Feliz cumple!", l.w / 2, y, l.u * 0.1f, Color.WHITE, "sans-serif-black", shadow = true)
        return l.out
    }

    private fun corazones(src: Bitmap): Bitmap {
        val l = layout(src, "corazones", 0.13f, 0.13f, 0.2f)
        gradient(l, col(0xFFFF6FA8), col(0xFFE45BC4), col(0xFFB06BFF))
        scatter(l, 50) { x, y, r -> l.c.drawPath(heartPath(x, y, l.u * (0.03f + r * 0.04f)), fill(Color.argb(55, 255, 255, 255))) }
        photo(l, src, radius = l.u * 0.03f, shadow = l.u * 0.04f, border = Color.WHITE, borderW = l.u * 0.014f)
        corners(l, inset(l, l.u * 0.02f), l.u * 0.26f) { c ->
            c.drawPath(heartPath(0.22f, 0.22f, 0.4f), fill(col(0xFFE8174F)))
            c.drawPath(heartPath(0.55f, 0.13f, 0.22f), fill(Color.WHITE))
            c.drawPath(heartPath(0.13f, 0.55f, 0.22f), fill(col(0xFFFFB3CF)))
            c.drawCircle(0.42f, 0.42f, 0.03f, fill(Color.WHITE))
        }
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.6f
        text(l, "Con todo nuestro amor", l.w / 2, y, l.u * 0.075f, Color.WHITE, "cursive", Typeface.BOLD, shadow = true)
        return l.out
    }

    private fun boda(src: Bitmap): Bitmap {
        val l = layout(src, "boda", 0.14f, 0.14f, 0.24f)
        paper(l, col(0xFFFBF8F1), grain = 900)
        val gold = col(0xFFC9A45C)
        l.c.drawRect(inset(l, l.u * 0.03f), stroke(gold, l.u * 0.004f))
        l.c.drawRect(inset(l, l.u * 0.042f), stroke(gold, l.u * 0.0015f))
        corners(l, inset(l, l.u * 0.03f), l.u * 0.22f) { c -> filigree(c, gold) }
        photo(l, src, shadow = l.u * 0.02f, border = gold, borderW = l.u * 0.005f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.48f
        text(l, "Para siempre", l.w / 2, y, l.u * 0.09f, gold, "cursive")
        text(l, today("d · MMMM · yyyy").uppercase(Locale("es", "ES")), l.w / 2, y + l.u * 0.06f, l.u * 0.026f, col(0xFF8C7650), "serif", spacing = 0.3f)
        return l.out
    }

    private fun fiesta(src: Bitmap): Bitmap {
        val l = layout(src, "fiesta", 0.12f, 0.12f, 0.22f)
        gradient(l, col(0xFF0F1B3D), col(0xFF2A0F45), col(0xFF0F1B3D))
        val streamColors = intArrayOf(col(0xFFFFD23F), col(0xFFFF4D8D), col(0xFF3DF2FF), col(0xFF8CFF8C))
        repeat(14) {
            val p = stroke(streamColors[it % streamColors.size], l.u * 0.009f)
            val x0 = l.rnd.nextFloat() * l.w
            val y0 = l.rnd.nextFloat() * l.h
            val len = l.u * (0.25f + l.rnd.nextFloat() * 0.3f)
            val path = Path().apply {
                moveTo(x0, y0)
                cubicTo(x0 + len * 0.3f, y0 - len * 0.4f, x0 + len * 0.6f, y0 + len * 0.4f, x0 + len, y0)
            }
            l.c.drawPath(path, p)
        }
        confetti(l, 120, intArrayOf(col(0xFFFFD23F), col(0xFFF6E27A), col(0xFFFF4D8D), col(0xFF3DF2FF)))
        scatter(l, 14) { x, y, r -> l.c.drawPath(starPath(x, y, l.u * (0.015f + r * 0.02f), 0.22f, 4), fill(col(0xFFFFF3B0))) }
        photo(l, src, radius = l.u * 0.015f, shadow = l.u * 0.05f, border = Color.WHITE, borderW = l.u * 0.01f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.62f
        text(l, "¡A celebrar!", l.w / 2, y, l.u * 0.1f, col(0xFFFFD23F), "sans-serif-black", glow = col(0xFFFF8A3D))
        return l.out
    }

    private fun navidad(src: Bitmap): Bitmap {
        val l = layout(src, "navidad", 0.13f, 0.13f, 0.22f)
        gradient(l, col(0xFFC0182F), col(0xFF7A0A1C), vertical = true)
        scatter(l, 40) { x, y, r -> snowflake(l.c, x, y, l.u * (0.015f + r * 0.03f), stroke(Color.argb(90 + (r * 90).toInt(), 255, 255, 255), l.u * 0.003f)) }
        val gold = col(0xFFE8C66A)
        l.c.drawRect(inset(l, l.u * 0.03f), stroke(gold, l.u * 0.004f))
        corners(l, inset(l, l.u * 0.015f), l.u * 0.26f) { c -> holly(c) }
        photo(l, src, shadow = l.u * 0.04f, border = Color.WHITE, borderW = l.u * 0.012f)
        l.c.drawRect(inflate(l.photo, l.u * 0.02f), stroke(gold, l.u * 0.003f))
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.6f
        text(l, "¡Feliz Navidad!", l.w / 2, y, l.u * 0.1f, Color.WHITE, "cursive", Typeface.BOLD, shadow = true)
        return l.out
    }

    private fun halloween(src: Bitmap): Bitmap {
        val l = layout(src, "halloween", 0.13f, 0.16f, 0.22f)
        gradient(l, col(0xFF3A0F52), col(0xFF120A1C), vertical = true)
        val mx = l.w - l.u * 0.1f
        val my = l.u * 0.09f
        glow(l, mx, my, l.u * 0.2f, Color.argb(120, 255, 240, 180))
        l.c.drawCircle(mx, my, l.u * 0.06f, fill(col(0xFFF6EDC0)))
        corners(l, RectF(0f, 0f, l.w, l.h), l.u * 0.28f, which = listOf(0)) { c -> web(c) }
        repeat(5) {
            val x = l.u * 0.25f + l.rnd.nextFloat() * (l.w - l.u * 0.5f)
            val y = l.u * (0.03f + l.rnd.nextFloat() * 0.1f)
            bat(l.c, x, y, l.u * (0.06f + l.rnd.nextFloat() * 0.05f))
        }
        photo(l, src, radius = l.u * 0.015f, shadow = l.u * 0.04f, border = col(0xFFFF8A1F), borderW = l.u * 0.008f)
        pumpkin(l.c, l.photo.left + l.u * 0.02f, l.photo.bottom + l.u * 0.02f, l.u * 0.13f)
        pumpkin(l.c, l.photo.right - l.u * 0.02f, l.photo.bottom + l.u * 0.03f, l.u * 0.1f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.68f
        text(l, "¡Feliz Halloween!", l.w / 2, y, l.u * 0.085f, col(0xFFFF8A1F), "sans-serif-black", glow = col(0xFFFF4D00))
        return l.out
    }

    // ───────────────────────────── ESTACIONES ─────────────────────────────

    private fun primavera(src: Bitmap): Bitmap {
        val l = layout(src, "primavera", 0.14f, 0.14f, 0.22f)
        gradient(l, col(0xFFE6FBE0), col(0xFFFFF4D6), col(0xFFFFE0EE))
        scatter(l, 40) { x, y, r ->
            l.c.save(); l.c.rotate(l.rnd.nextFloat() * 360f, x, y)
            val s = l.u * (0.012f + r * 0.015f)
            l.c.drawOval(RectF(x - s, y - s * 0.55f, x + s, y + s * 0.55f), fill(Color.argb(170, 255, 150, 190)))
            l.c.restore()
        }
        photo(l, src, radius = l.u * 0.03f, shadow = l.u * 0.035f, border = Color.WHITE, borderW = l.u * 0.014f)
        corners(l, inset(l, l.u * 0.02f), l.u * 0.3f) { c -> flowerCorner(c) }
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.62f
        text(l, "¡Hola, primavera!", l.w / 2, y, l.u * 0.085f, col(0xFFD94F8A), "cursive", Typeface.BOLD)
        return l.out
    }

    private fun verano(src: Bitmap): Bitmap {
        val l = layout(src, "verano", 0.11f, 0.15f, 0.24f)
        gradient(l, col(0xFF3FB6F2), col(0xFF8AD8FF), col(0xFFFFE6A8), vertical = true)
        // sol
        val sx = l.w - l.u * 0.11f
        val sy = l.u * 0.11f
        glow(l, sx, sy, l.u * 0.25f, Color.argb(160, 255, 230, 120))
        val ray = stroke(col(0xFFFFC233), l.u * 0.012f)
        for (i in 0 until 12) {
            val a = i * PI / 6
            l.c.drawLine(sx + (l.u * 0.085f * cos(a)).toFloat(), sy + (l.u * 0.085f * sin(a)).toFloat(), sx + (l.u * 0.13f * cos(a)).toFloat(), sy + (l.u * 0.13f * sin(a)).toFloat(), ray)
        }
        l.c.drawCircle(sx, sy, l.u * 0.07f, fill(col(0xFFFFD23F)))
        // gaviotas
        val gull = stroke(Color.argb(200, 255, 255, 255), l.u * 0.006f)
        repeat(3) {
            val gx = l.u * (0.15f + it * 0.13f)
            val gy = l.u * (0.05f + (it % 2) * 0.04f)
            val s = l.u * 0.03f
            l.c.drawPath(Path().apply { moveTo(gx - s, gy); quadTo(gx - s / 2, gy - s * 0.6f, gx, gy); quadTo(gx + s / 2, gy - s * 0.6f, gx + s, gy) }, gull)
        }
        // olas
        wave(l, l.h - l.u * 0.13f, l.u * 0.025f, l.u * 0.12f, Color.argb(150, 64, 170, 230))
        wave(l, l.h - l.u * 0.09f, l.u * 0.02f, l.u * 0.09f, Color.argb(180, 30, 130, 210))
        wave(l, l.h - l.u * 0.05f, l.u * 0.015f, l.u * 0.07f, Color.argb(210, 20, 100, 190))
        photo(l, src, radius = l.u * 0.02f, shadow = l.u * 0.04f, border = Color.WHITE, borderW = l.u * 0.014f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.42f
        text(l, "¡Verano!", l.w / 2, y, l.u * 0.09f, Color.WHITE, "sans-serif-black", shadow = true)
        return l.out
    }

    private fun otono(src: Bitmap): Bitmap {
        val l = layout(src, "otono", 0.13f, 0.13f, 0.22f)
        gradient(l, col(0xFFF6B25E), col(0xFFD9692E), col(0xFF9C3A1E))
        val leaves = intArrayOf(col(0xFFFFC94D), col(0xFFE8572A), col(0xFFB8321A), col(0xFF8A5A2B), col(0xFFF08A24))
        scatter(l, 46) { x, y, r ->
            val color = leaves[l.rnd.nextInt(leaves.size)]
            val s = l.u * (0.035f + r * 0.05f)
            if (l.rnd.nextInt(3) == 0) {
                l.c.save(); l.c.rotate(l.rnd.nextFloat() * 360f, x, y)
                l.c.drawPath(starPath(x, y, s * 0.6f, 0.5f), fill(color))
                l.c.drawLine(x, y, x, y + s * 0.7f, stroke(color, s * 0.06f))
                l.c.restore()
            } else {
                leaf(l.c, x, y, s, l.rnd.nextFloat() * 360f, color)
            }
        }
        photo(l, src, radius = l.u * 0.02f, shadow = l.u * 0.045f, border = col(0xFFFFF4E0), borderW = l.u * 0.014f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.6f
        text(l, "Otoño", l.w / 2, y, l.u * 0.1f, col(0xFFFFF4E0), "cursive", Typeface.BOLD, shadow = true)
        return l.out
    }

    private fun invierno(src: Bitmap): Bitmap {
        val l = layout(src, "invierno", 0.12f, 0.12f, 0.24f)
        gradient(l, col(0xFF9FCBFF), col(0xFFDCEEFF), vertical = true)
        scatter(l, 46) { x, y, r -> snowflake(l.c, x, y, l.u * (0.012f + r * 0.035f), stroke(Color.argb(150 + (r * 100).toInt(), 255, 255, 255), l.u * 0.004f)) }
        // montón de nieve
        val snow = Path().apply {
            moveTo(0f, l.h)
            lineTo(0f, l.h - l.u * 0.1f)
            var x = 0f
            var up = true
            while (x < l.w) {
                val nx = x + l.u * 0.25f
                quadTo(x + l.u * 0.125f, l.h - l.u * (if (up) 0.17f else 0.07f), nx, l.h - l.u * 0.1f)
                x = nx
                up = !up
            }
            lineTo(l.w, l.h)
            close()
        }
        l.c.drawPath(snow, fill(Color.WHITE).apply { setShadowLayer(l.u * 0.02f, 0f, -l.u * 0.005f, Color.argb(60, 60, 110, 170)) })
        photo(l, src, radius = l.u * 0.03f, shadow = l.u * 0.04f, border = Color.WHITE, borderW = l.u * 0.014f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.42f
        text(l, "Invierno", l.w / 2, y, l.u * 0.08f, col(0xFF2B5A8A), "sans-serif-light", Typeface.BOLD, spacing = 0.15f)
        return l.out
    }

    // ───────────────────────────── VIAJES Y PEQUES ─────────────────────────────

    private fun postal(src: Bitmap): Bitmap {
        val l = layout(src, "postal", 0.1f, 0.12f, 0.22f)
        // ribete de correo aéreo
        l.c.drawColor(Color.WHITE)
        val stripe = l.u * 0.05f
        val red = fill(col(0xFFD9363E))
        val blue = fill(col(0xFF2350A8))
        var x = -l.h
        var i = 0
        while (x < l.w + l.h) {
            poly(l.c, if (i % 2 == 0) red else blue, x, 0f, x + stripe, 0f, x + stripe - l.h, l.h, x - l.h, l.h)
            x += stripe * 2
            i++
        }
        l.c.drawRect(inset(l, l.u * 0.035f), fill(col(0xFFFFFCF4)))
        photo(l, src, shadow = l.u * 0.015f)
        // matasellos
        val pcx = l.photo.right - l.u * 0.3f
        val pcy = l.photo.top + l.u * 0.02f
        val ink = stroke(Color.argb(150, 40, 40, 60), l.u * 0.004f)
        l.c.drawCircle(pcx, pcy, l.u * 0.075f, ink)
        l.c.drawCircle(pcx, pcy, l.u * 0.058f, ink)
        for (k in 0 until 4) {
            val wy = pcy - l.u * 0.04f + k * l.u * 0.027f
            val path = Path().apply {
                moveTo(pcx - l.u * 0.36f, wy)
                var xx = pcx - l.u * 0.36f
                var up = true
                while (xx < pcx - l.u * 0.09f) {
                    quadTo(xx + l.u * 0.02f, wy + (if (up) -1 else 1) * l.u * 0.012f, xx + l.u * 0.04f, wy)
                    xx += l.u * 0.04f
                    up = !up
                }
            }
            l.c.drawPath(path, ink)
        }
        text(l, today("dd.MM.yy"), pcx, pcy + l.u * 0.012f, l.u * 0.028f, Color.argb(170, 40, 40, 60), "monospace", Typeface.BOLD)
        // sello
        stamp(l.c, l.photo.right - l.u * 0.1f, l.photo.top + l.u * 0.06f, l.u * 0.15f, l.u * 0.19f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.45f
        text(l, "Un recuerdo de viaje ✈", l.photo.left + l.u * 0.02f, y, l.u * 0.065f, col(0xFF23407A), "casual", align = Paint.Align.LEFT)
        val lineP = stroke(Color.argb(60, 35, 64, 122), l.u * 0.002f)
        l.c.drawLine(l.photo.left, y + l.u * 0.035f, l.photo.right, y + l.u * 0.035f, lineP)
        l.c.drawLine(l.photo.left, y + l.u * 0.075f, l.photo.right, y + l.u * 0.075f, lineP)
        return l.out
    }

    private fun cole(src: Bitmap): Bitmap {
        val l = layout(src, "cole", 0.13f, 0.13f, 0.22f)
        l.c.drawColor(col(0xFFFFFEF7))
        val ruled = stroke(Color.argb(90, 90, 150, 220), l.u * 0.002f)
        var y = l.u * 0.06f
        while (y < l.h) { l.c.drawLine(0f, y, l.w, y, ruled); y += l.u * 0.045f }
        l.c.drawLine(l.u * 0.09f, 0f, l.u * 0.09f, l.h, stroke(Color.argb(150, 230, 70, 80), l.u * 0.003f))
        // agujeros de anillas
        var hy = l.u * 0.08f
        while (hy < l.h) {
            l.c.drawCircle(l.u * 0.04f, hy, l.u * 0.014f, fill(col(0xFFD5D2C8)))
            hy += l.u * 0.09f
        }
        val crayons = intArrayOf(col(0xFFFF6B6B), col(0xFF4D96FF), col(0xFF6BCB77), col(0xFFFFB84D), col(0xFFB06BFF))
        scatter(l, 18) { x, yy, r ->
            val ink = crayons[l.rnd.nextInt(crayons.size)]
            val s = l.u * (0.025f + r * 0.03f)
            when (l.rnd.nextInt(3)) {
                0 -> l.c.drawPath(starPath(x, yy, s, 0.45f), stroke(ink, l.u * 0.006f))
                1 -> l.c.drawPath(heartPath(x, yy, s * 1.6f), stroke(ink, l.u * 0.006f))
                else -> sunDoodle(l.c, x, yy, s, ink, l.u * 0.006f)
            }
        }
        l.c.save()
        l.c.rotate(-2f, l.photo.centerX(), l.photo.centerY())
        photo(l, src, shadow = l.u * 0.03f, border = Color.WHITE, borderW = l.u * 0.015f)
        l.c.restore()
        tape(l.c, l.photo.centerX(), l.photo.top - l.u * 0.005f, l.u * 0.3f, l.u * 0.08f, 3f, Color.argb(255, 255, 240, 170), null)
        val ty = l.photo.bottom + (l.h - l.photo.bottom) * 0.58f
        text(l, "¡Qué bien lo pasamos!", l.w / 2, ty, l.u * 0.075f, col(0xFF3D6FD6), "casual", Typeface.BOLD)
        return l.out
    }

    private fun bebe(src: Bitmap): Bitmap {
        val l = layout(src, "bebe", 0.14f, 0.14f, 0.22f)
        gradient(l, col(0xFFFFE3EC), col(0xFFFFF6E0), col(0xFFE0F2FF))
        scatter(l, 24) { x, y, r -> l.c.drawPath(starPath(x, y, l.u * (0.012f + r * 0.02f), 0.5f), fill(Color.argb(200, 255, 214, 110))) }
        cloud(l.c, l.u * 0.13f, l.u * 0.08f, l.u * 0.18f, Color.WHITE)
        cloud(l.c, l.w - l.u * 0.15f, l.h - l.u * 0.08f, l.u * 0.2f, Color.WHITE)
        crescent(l.c, l.w - l.u * 0.09f, l.u * 0.08f, l.u * 0.05f, col(0xFFFFD86E))
        photo(l, src, radius = l.u * 0.06f, shadow = l.u * 0.03f, border = Color.WHITE, borderW = l.u * 0.018f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.55f
        text(l, "Pequeño tesoro", l.w / 2, y, l.u * 0.08f, col(0xFF7A8CC2), "casual", Typeface.BOLD)
        return l.out
    }

    private fun noche(src: Bitmap): Bitmap {
        val l = layout(src, "noche", 0.12f, 0.12f, 0.22f)
        gradient(l, col(0xFF0A0F33), col(0xFF261A5C), col(0xFF4A2A7E), vertical = true)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(260) {
            dot.color = Color.argb(60 + l.rnd.nextInt(190), 255, 255, 255)
            l.c.drawCircle(l.rnd.nextFloat() * l.w, l.rnd.nextFloat() * l.h, l.u * (0.001f + l.rnd.nextFloat() * 0.004f), dot)
        }
        scatter(l, 10) { x, y, r -> l.c.drawPath(starPath(x, y, l.u * (0.015f + r * 0.02f), 0.2f, 4), fill(col(0xFFFFF3C4))) }
        val mx = l.w - l.u * 0.1f
        val my = l.u * 0.1f
        glow(l, mx, my, l.u * 0.18f, Color.argb(110, 255, 230, 150))
        crescent(l.c, mx, my, l.u * 0.06f, col(0xFFFFE9A8))
        // estrella fugaz
        val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = l.u * 0.006f
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(l.u * 0.08f, l.u * 0.12f, l.u * 0.3f, l.u * 0.03f, Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
        }
        l.c.drawLine(l.u * 0.08f, l.u * 0.12f, l.u * 0.3f, l.u * 0.03f, tail)
        photo(l, src, radius = l.u * 0.03f, shadow = l.u * 0.05f, border = Color.argb(210, 255, 255, 255), borderW = l.u * 0.008f)
        val y = l.photo.bottom + (l.h - l.photo.bottom) * 0.6f
        text(l, "Noche mágica", l.w / 2, y, l.u * 0.09f, col(0xFFF6D776), "cursive", Typeface.BOLD, glow = col(0xFFB07CFF))
        return l.out
    }

    // ───────────────────────────── MAQUETACIÓN ─────────────────────────────

    private class L(val out: Bitmap, val c: Canvas, val photo: RectF, val u: Float, val rnd: Random, val w: Float, val h: Float)

    /** Modo página: en vez de crear una imagen nueva, se dibuja en un lienzo dado (p. ej. una página del PDF). */
    private class PageTarget(val canvas: Canvas, val w: Float, val h: Float)

    private val pageTarget = ThreadLocal<PageTarget?>()
    private val dateOverride = ThreadLocal<Long?>()
    private val dummy: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    /**
     * Dibuja una página entera con el diseño del marco [key] y la foto encajada dentro
     * (para el libro de fotos). Todo lo decorativo queda en vectorial en el PDF.
     * [dateMillis] = fecha que se escribe en el marco (la de la foto).
     */
    fun drawPage(canvas: Canvas, w: Float, h: Float, src: Bitmap, key: String, dateMillis: Long) {
        pageTarget.set(PageTarget(canvas, w, h))
        dateOverride.set(dateMillis)
        try {
            apply(src, key)
        } finally {
            pageTarget.set(null)
            dateOverride.set(null)
        }
    }

    /** Lienzo con márgenes (en proporción al lado corto de la foto). */
    private fun layout(src: Bitmap, key: String, side: Float, top: Float = side, bottom: Float = side): L {
        val target = pageTarget.get()
        if (target != null) {
            // encajar foto + márgenes del marco dentro de la página, centrado
            val m = min(src.width, src.height).toFloat()
            val sc = min(target.w / (src.width + 2 * side * m), target.h / (src.height + (top + bottom) * m))
            val pw = src.width * sc
            val ph = src.height * sc
            val u = min(pw, ph)
            val blockH = ph + (top + bottom) * u
            val left = (target.w - pw) / 2
            val topY = (target.h - blockH) / 2 + top * u
            val seed = key.hashCode() * 31 + src.width * 7 + src.height
            return L(dummy, target.canvas, RectF(left, topY, left + pw, topY + ph), u, Random(seed), target.w, target.h)
        }
        val u = min(src.width, src.height).toFloat()
        val s = (u * side).toInt()
        val t = (u * top).toInt()
        val b = (u * bottom).toInt()
        val out = Bitmap.createBitmap(src.width + 2 * s, src.height + t + b, Bitmap.Config.ARGB_8888)
        val photo = RectF(s.toFloat(), t.toFloat(), (s + src.width).toFloat(), (t + src.height).toFloat())
        val seed = key.hashCode() * 31 + src.width * 7 + src.height
        return L(out, Canvas(out), photo, u, Random(seed), out.width.toFloat(), out.height.toFloat())
    }

    private fun photo(
        l: L,
        src: Bitmap,
        radius: Float = 0f,
        shadow: Float = 0f,
        border: Int = 0,
        borderW: Float = 0f,
    ) {
        val r = l.photo
        val c = l.c
        if (borderW > 0f) {
            val p = fill(border)
            if (shadow > 0f) p.setShadowLayer(shadow, 0f, shadow * 0.35f, Color.argb(110, 0, 0, 0))
            c.drawRoundRect(inflate(r, borderW), radius + borderW * 0.6f, radius + borderW * 0.6f, p)
        } else if (shadow > 0f) {
            val p = fill(Color.BLACK).apply { setShadowLayer(shadow, 0f, shadow * 0.35f, Color.argb(130, 0, 0, 0)) }
            c.drawRoundRect(r, radius, radius, p)
        }
        val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (radius > 0f) {
            c.save()
            c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
            c.drawBitmap(src, null, r, bp)
            c.restore()
        } else {
            c.drawBitmap(src, null, r, bp)
        }
    }

    /** Fondo con la propia foto muy desenfocada (efecto cristal). */
    private fun blurredBackground(l: L, src: Bitmap) {
        val tiny = Bitmap.createScaledBitmap(src, max(1, src.width / 32), max(1, src.height / 32), true)
        val soft = Bitmap.createScaledBitmap(tiny, max(1, src.width / 8), max(1, src.height / 8), true)
        if (tiny !== soft) tiny.recycle()
        val ar = l.w / l.h
        val sw = soft.width.toFloat()
        val sh = soft.height.toFloat()
        val crop = if (sw / sh > ar) {
            val cw = sh * ar
            Rect(((sw - cw) / 2).toInt(), 0, ((sw + cw) / 2).toInt(), sh.toInt())
        } else {
            val ch = sw / ar
            Rect(0, ((sh - ch) / 2).toInt(), sw.toInt(), ((sh + ch) / 2).toInt())
        }
        l.c.drawBitmap(soft, crop, RectF(0f, 0f, l.w, l.h), Paint(Paint.FILTER_BITMAP_FLAG))
        soft.recycle()
    }

    // ───────────────────────────── FONDOS ─────────────────────────────

    private fun gradient(l: L, vararg colors: Int, vertical: Boolean = false) {
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, if (vertical) 0f else l.w, l.h, colors, null, Shader.TileMode.CLAMP)
        }
        l.c.drawRect(0f, 0f, l.w, l.h, p)
    }

    private fun glow(l: L, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r, intArrayOf(color, alpha(color, 0)), null, Shader.TileMode.CLAMP)
        }
        l.c.drawCircle(cx, cy, r, p)
    }

    /** Papel con grano. */
    private fun paper(l: L, base: Int, grain: Int = 2600) {
        l.c.drawColor(base)
        val p = Paint()
        repeat(grain) {
            p.color = if (l.rnd.nextBoolean()) Color.argb(14, 0, 0, 0) else Color.argb(22, 255, 255, 255)
            val x = l.rnd.nextFloat() * l.w
            val y = l.rnd.nextFloat() * l.h
            val s = l.u * (0.002f + l.rnd.nextFloat() * 0.004f)
            l.c.drawRect(x, y, x + s, y + s, p)
        }
    }

    private fun vignette(l: L, edge: Int) {
        val p = Paint().apply {
            shader = RadialGradient(
                l.w / 2, l.h / 2, max(l.w, l.h) * 0.72f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, edge), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        l.c.drawRect(0f, 0f, l.w, l.h, p)
    }

    /** Reparte adornos por el borde, sin tapar la foto. [draw] recibe x, y y un tamaño aleatorio 0..1. */
    private fun scatter(l: L, n: Int, draw: (Float, Float, Float) -> Unit) {
        val avoid = inflate(l.photo, l.u * 0.015f)
        var placed = 0
        var tries = 0
        while (placed < n && tries < n * 12) {
            tries++
            val x = l.rnd.nextFloat() * l.w
            val y = l.rnd.nextFloat() * l.h
            if (avoid.contains(x, y)) continue
            draw(x, y, l.rnd.nextFloat())
            placed++
        }
    }

    private fun confetti(l: L, n: Int, colors: IntArray) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        scatter(l, n) { x, y, r ->
            p.color = colors[l.rnd.nextInt(colors.size)]
            val s = l.u * (0.006f + r * 0.012f)
            when (l.rnd.nextInt(3)) {
                0 -> l.c.drawCircle(x, y, s, p)
                else -> {
                    l.c.save()
                    l.c.rotate(l.rnd.nextFloat() * 180f, x, y)
                    l.c.drawRect(x - s * 1.4f, y - s * 0.5f, x + s * 1.4f, y + s * 0.5f, p)
                    l.c.restore()
                }
            }
        }
    }

    private fun wave(l: L, y: Float, amp: Float, len: Float, color: Int) {
        val path = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = true
            while (x < l.w) {
                quadTo(x + len / 2, y + if (up) -amp * 2 else amp * 2, x + len, y)
                x += len
                up = !up
            }
            lineTo(l.w, l.h)
            lineTo(0f, l.h)
            close()
        }
        l.c.drawPath(path, fill(color))
    }

    private fun neonRect(l: L, r: RectF, radius: Float, color: Int, width: Float) {
        val halo = stroke(color, width * 3f).apply { maskFilter = BlurMaskFilter(width * 3f, BlurMaskFilter.Blur.NORMAL) }
        l.c.drawRoundRect(r, radius, radius, halo)
        l.c.drawRoundRect(r, radius, radius, stroke(color, width))
        l.c.drawRoundRect(r, radius, radius, stroke(Color.argb(220, 255, 255, 255), width * 0.35f))
    }

    // ───────────────────────────── ESQUINAS ─────────────────────────────

    /**
     * Dibuja el mismo adorno en las esquinas de [r], reflejado hacia dentro.
     * El adorno se dibuja en unidades 0..1 (x hacia dentro, y hacia dentro).
     */
    private fun corners(l: L, r: RectF, size: Float, which: List<Int> = listOf(0, 1, 2, 3), draw: (Canvas) -> Unit) {
        val pts = listOf(
            Triple(r.left, r.top, 1f to 1f),
            Triple(r.right, r.top, -1f to 1f),
            Triple(r.left, r.bottom, 1f to -1f),
            Triple(r.right, r.bottom, -1f to -1f),
        )
        which.forEach { i ->
            val (x, y, d) = pts[i]
            l.c.save()
            l.c.translate(x, y)
            l.c.scale(d.first * size, d.second * size)
            draw(l.c)
            l.c.restore()
        }
    }

    /** Abanico art déco. */
    private fun decoFan(c: Canvas, color: Int) {
        val p = stroke(color, 0.022f)
        for (k in 1..3) {
            val r = 0.18f * k
            c.drawArc(RectF(-r, -r, r, r), 0f, 90f, false, p)
        }
        for (i in 0..4) {
            val a = (i * 22.5) * PI / 180
            c.drawLine((0.18f * cos(a)).toFloat(), (0.18f * sin(a)).toFloat(), (0.54f * cos(a)).toFloat(), (0.54f * sin(a)).toFloat(), p)
        }
        c.drawLine(0f, 0f, 1f, 0f, p)
        c.drawLine(0f, 0f, 0f, 1f, p)
        c.drawLine(0.12f, 0.6f, 0.12f, 0.85f, p)
        c.drawLine(0.6f, 0.12f, 0.85f, 0.12f, p)
        c.drawCircle(0.12f, 0.9f, 0.025f, fill(color))
        c.drawCircle(0.9f, 0.12f, 0.025f, fill(color))
    }

    /** Filigrana con volutas. */
    private fun filigree(c: Canvas, color: Int) {
        val p = stroke(color, 0.018f)
        c.drawPath(Path().apply { moveTo(1f, 0.05f); cubicTo(0.5f, 0.05f, 0.05f, 0.5f, 0.05f, 1f) }, p)
        // voluta central y su reflejo
        c.drawPath(Path().apply { moveTo(0.45f, 0.1f); cubicTo(0.15f, 0.1f, 0.12f, 0.36f, 0.3f, 0.36f); cubicTo(0.42f, 0.36f, 0.42f, 0.22f, 0.31f, 0.22f) }, p)
        c.drawPath(Path().apply { moveTo(0.1f, 0.45f); cubicTo(0.1f, 0.15f, 0.36f, 0.12f, 0.36f, 0.3f); cubicTo(0.36f, 0.42f, 0.22f, 0.42f, 0.22f, 0.31f) }, p)
        // zarcillos
        c.drawPath(Path().apply { moveTo(0.7f, 0.05f); cubicTo(0.75f, 0.2f, 0.58f, 0.24f, 0.55f, 0.15f) }, p)
        c.drawPath(Path().apply { moveTo(0.05f, 0.7f); cubicTo(0.2f, 0.75f, 0.24f, 0.58f, 0.15f, 0.55f) }, p)
        val dot = fill(color)
        c.drawCircle(0.1f, 0.1f, 0.04f, dot)
        c.drawCircle(0.88f, 0.14f, 0.022f, dot)
        c.drawCircle(0.14f, 0.88f, 0.022f, dot)
        c.drawCircle(0.5f, 0.5f, 0.018f, dot)
    }

    private fun holly(c: Canvas) {
        leaf(c, 0.38f, 0.17f, 0.42f, 70f, Color.rgb(30, 125, 69))
        leaf(c, 0.17f, 0.38f, 0.42f, 20f, Color.rgb(24, 105, 58))
        leaf(c, 0.36f, 0.36f, 0.32f, 45f, Color.rgb(40, 145, 80))
        val berry = fill(Color.rgb(220, 30, 50))
        c.drawCircle(0.14f, 0.14f, 0.065f, berry)
        c.drawCircle(0.24f, 0.1f, 0.055f, berry)
        c.drawCircle(0.1f, 0.24f, 0.055f, berry)
        c.drawCircle(0.12f, 0.12f, 0.02f, fill(Color.argb(170, 255, 255, 255)))
    }

    private fun flowerCorner(c: Canvas) {
        leaf(c, 0.42f, 0.2f, 0.32f, 60f, Color.rgb(110, 190, 110))
        leaf(c, 0.2f, 0.42f, 0.32f, 30f, Color.rgb(90, 170, 95))
        flower(c, 0.18f, 0.18f, 0.2f, Color.rgb(255, 140, 180), Color.rgb(255, 214, 90))
        flower(c, 0.45f, 0.09f, 0.11f, Color.rgb(255, 214, 90), Color.rgb(255, 150, 60), 6)
        flower(c, 0.09f, 0.45f, 0.11f, Color.rgb(180, 150, 255), Color.WHITE, 5)
        flower(c, 0.62f, 0.05f, 0.06f, Color.WHITE, Color.rgb(255, 200, 90), 5)
    }

    private fun web(c: Canvas) {
        val p = stroke(Color.argb(130, 230, 230, 255), 0.01f)
        val angles = (0..4).map { it * 22.5 * PI / 180 }
        angles.forEach { a -> c.drawLine(0f, 0f, cos(a).toFloat(), sin(a).toFloat(), p) }
        for (k in 1..4) {
            val r = k * 0.22f
            val path = Path()
            for (i in 0 until angles.size - 1) {
                val a0 = angles[i]
                val a1 = angles[i + 1]
                val mid = (a0 + a1) / 2
                if (i == 0) path.moveTo((r * cos(a0)).toFloat(), (r * sin(a0)).toFloat())
                path.quadTo((r * 0.82f * cos(mid)).toFloat(), (r * 0.82f * sin(mid)).toFloat(), (r * cos(a1)).toFloat(), (r * sin(a1)).toFloat())
            }
            c.drawPath(path, p)
        }
    }

    // ───────────────────────────── FIGURAS ─────────────────────────────

    private fun heartPath(cx: Float, cy: Float, s: Float) = Path().apply {
        moveTo(cx, cy + s * 0.4f)
        cubicTo(cx - s * 0.15f, cy + s * 0.25f, cx - s * 0.5f, cy + s * 0.05f, cx - s * 0.5f, cy - s * 0.15f)
        cubicTo(cx - s * 0.5f, cy - s * 0.48f, cx - s * 0.08f, cy - s * 0.52f, cx, cy - s * 0.28f)
        cubicTo(cx + s * 0.08f, cy - s * 0.52f, cx + s * 0.5f, cy - s * 0.48f, cx + s * 0.5f, cy - s * 0.15f)
        cubicTo(cx + s * 0.5f, cy + s * 0.05f, cx + s * 0.15f, cy + s * 0.25f, cx, cy + s * 0.4f)
        close()
    }

    private fun starPath(cx: Float, cy: Float, r: Float, inner: Float = 0.45f, points: Int = 5) = Path().apply {
        for (i in 0 until points * 2) {
            val rr = if (i % 2 == 0) r else r * inner
            val a = (-90.0 + i * 180.0 / points) * PI / 180
            val x = cx + (rr * cos(a)).toFloat()
            val y = cy + (rr * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun snowflake(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        for (i in 0 until 6) {
            val a = i * PI / 3
            c.drawLine(cx, cy, cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat(), p)
            val bx = cx + (r * 0.55f * cos(a)).toFloat()
            val by = cy + (r * 0.55f * sin(a)).toFloat()
            for (s in listOf(-1, 1)) {
                val b = a + s * PI / 4
                c.drawLine(bx, by, bx + (r * 0.3f * cos(b)).toFloat(), by + (r * 0.3f * sin(b)).toFloat(), p)
            }
        }
    }

    private fun leaf(c: Canvas, cx: Float, cy: Float, len: Float, angle: Float, color: Int) {
        c.save()
        c.rotate(angle, cx, cy)
        val path = Path().apply {
            moveTo(cx, cy - len / 2)
            quadTo(cx + len * 0.42f, cy, cx, cy + len / 2)
            quadTo(cx - len * 0.42f, cy, cx, cy - len / 2)
            close()
        }
        c.drawPath(path, fill(color))
        c.drawLine(cx, cy - len * 0.4f, cx, cy + len * 0.62f, stroke(Color.argb(70, 0, 0, 0), len * 0.035f))
        c.restore()
    }

    private fun flower(c: Canvas, cx: Float, cy: Float, r: Float, petal: Int, center: Int, petals: Int = 5) {
        val p = fill(petal)
        val shade = fill(Color.argb(40, 0, 0, 0))
        for (i in 0 until petals) {
            c.save()
            c.rotate(i * 360f / petals, cx, cy)
            c.drawOval(RectF(cx - r * 0.3f, cy - r, cx + r * 0.3f, cy), p)
            c.drawOval(RectF(cx - r * 0.08f, cy - r * 0.8f, cx + r * 0.08f, cy - r * 0.25f), shade)
            c.restore()
        }
        c.drawCircle(cx, cy, r * 0.28f, fill(center))
    }

    private fun balloon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val string = stroke(Color.argb(170, 255, 255, 255), r * 0.04f)
        c.drawPath(Path().apply { moveTo(cx, cy + r * 1.2f); cubicTo(cx - r * 0.3f, cy + r * 1.7f, cx + r * 0.3f, cy + r * 2.1f, cx, cy + r * 2.6f) }, string)
        val body = fill(color).apply { setShadowLayer(r * 0.15f, 0f, r * 0.08f, Color.argb(80, 0, 0, 0)) }
        c.drawOval(RectF(cx - r, cy - r * 1.2f, cx + r, cy + r * 1.1f), body)
        poly(c, fill(color), cx, cy + r * 1.05f, cx - r * 0.16f, cy + r * 1.27f, cx + r * 0.16f, cy + r * 1.27f)
        c.drawOval(RectF(cx - r * 0.62f, cy - r * 0.85f, cx - r * 0.25f, cy - r * 0.25f), fill(Color.argb(100, 255, 255, 255)))
    }

    private fun bunting(l: L, y: Float, size: Float, colors: IntArray) {
        val sag = size * 0.7f
        l.c.drawPath(Path().apply { moveTo(0f, y); quadTo(l.w / 2, y + sag * 2, l.w, y) }, stroke(Color.argb(220, 255, 255, 255), size * 0.04f))
        val n = max(6, (l.w / (size * 1.15f)).toInt())
        fun yAt(t: Float) = y + 4 * sag * t * (1 - t)
        for (i in 0 until n) {
            val t0 = (i + 0.08f) / n
            val t1 = (i + 0.92f) / n
            val tm = (t0 + t1) / 2
            val p = fill(colors[i % colors.size]).apply { setShadowLayer(size * 0.06f, 0f, size * 0.04f, Color.argb(70, 0, 0, 0)) }
            poly(l.c, p, l.w * t0, yAt(t0), l.w * t1, yAt(t1), l.w * tm, yAt(tm) + size)
        }
    }

    /** Cinta adhesiva decorativa (washi tape) con bordes rasgados. */
    private fun tape(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, angle: Float, color: Int, stripe: Int?) {
        c.save()
        c.rotate(angle, cx, cy)
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val path = Path().apply {
            moveTo(r.left, r.top)
            lineTo(r.right, r.top)
            val steps = 6
            for (i in 1..steps) lineTo(r.right - (if (i % 2 == 1) h * 0.07f else 0f), r.top + h * i / steps)
            lineTo(r.left, r.bottom)
            for (i in 1..steps) lineTo(r.left + (if (i % 2 == 1) h * 0.07f else 0f), r.bottom - h * i / steps)
            close()
        }
        c.drawPath(path, fill(alpha(color, 215)).apply { setShadowLayer(h * 0.08f, 0f, h * 0.04f, Color.argb(50, 0, 0, 0)) })
        if (stripe != null) {
            c.save()
            c.clipPath(path)
            val sp = stroke(stripe, h * 0.12f)
            var x = r.left - h
            while (x < r.right + h) {
                c.drawLine(x, r.bottom, x + h, r.top, sp)
                x += h * 0.35f
            }
            c.restore()
        }
        c.restore()
    }

    private fun stamp(c: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        c.save()
        c.rotate(6f, cx, cy)
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val white = fill(Color.WHITE).apply { setShadowLayer(w * 0.05f, 0f, w * 0.03f, Color.argb(90, 0, 0, 0)) }
        c.drawRect(r, white)
        // dentado
        val dot = fill(Color.WHITE)
        val step = w / 8
        var x = r.left
        while (x <= r.right) { c.drawCircle(x, r.top, step * 0.32f, dot); c.drawCircle(x, r.bottom, step * 0.32f, dot); x += step }
        var y = r.top
        while (y <= r.bottom) { c.drawCircle(r.left, y, step * 0.32f, dot); c.drawCircle(r.right, y, step * 0.32f, dot); y += step }
        val inner = RectF(r.left + w * 0.1f, r.top + w * 0.1f, r.right - w * 0.1f, r.bottom - w * 0.1f)
        c.drawRect(inner, Paint().apply {
            shader = LinearGradient(0f, inner.top, 0f, inner.bottom, Color.rgb(120, 200, 255), Color.rgb(255, 210, 150), Shader.TileMode.CLAMP)
        })
        c.drawCircle(inner.right - inner.width() * 0.28f, inner.top + inner.height() * 0.28f, inner.width() * 0.14f, fill(Color.rgb(255, 210, 60)))
        poly(c, fill(Color.rgb(60, 130, 110)), inner.left, inner.bottom, inner.left + inner.width() * 0.4f, inner.top + inner.height() * 0.45f, inner.left + inner.width() * 0.75f, inner.bottom)
        poly(c, fill(Color.rgb(40, 100, 90)), inner.left + inner.width() * 0.35f, inner.bottom, inner.left + inner.width() * 0.7f, inner.top + inner.height() * 0.55f, inner.right, inner.bottom)
        c.restore()
    }

    private fun bat(c: Canvas, cx: Float, cy: Float, w: Float) {
        c.save()
        c.translate(cx, cy)
        c.scale(w, w)
        val p = fill(Color.rgb(10, 6, 16))
        c.drawPath(Path().apply {
            moveTo(0f, -0.08f)
            quadTo(0.15f, -0.25f, 0.5f, -0.2f)
            quadTo(0.4f, -0.05f, 0.45f, 0.08f)
            quadTo(0.3f, 0f, 0.22f, 0.1f)
            quadTo(0.1f, 0.02f, 0f, 0.14f)
            quadTo(-0.1f, 0.02f, -0.22f, 0.1f)
            quadTo(-0.3f, 0f, -0.45f, 0.08f)
            quadTo(-0.4f, -0.05f, -0.5f, -0.2f)
            quadTo(-0.15f, -0.25f, 0f, -0.08f)
            close()
        }, p)
        c.drawCircle(0f, -0.06f, 0.07f, p)
        poly(c, p, -0.06f, -0.1f, -0.04f, -0.18f, -0.01f, -0.1f)
        poly(c, p, 0.06f, -0.1f, 0.04f, -0.18f, 0.01f, -0.1f)
        c.restore()
    }

    private fun pumpkin(c: Canvas, cx: Float, cy: Float, s: Float) {
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        c.drawRect(-0.06f, -0.55f, 0.06f, -0.3f, fill(Color.rgb(70, 110, 40)))
        val dark = fill(Color.rgb(214, 96, 18)).apply { setShadowLayer(0.06f, 0f, 0.03f, Color.argb(120, 0, 0, 0)) }
        c.drawOval(RectF(-0.55f, -0.38f, 0.0f, 0.4f), dark)
        c.drawOval(RectF(0.0f, -0.38f, 0.55f, 0.4f), dark)
        c.drawOval(RectF(-0.32f, -0.42f, 0.32f, 0.42f), fill(Color.rgb(250, 135, 30)))
        val face = fill(Color.rgb(60, 20, 0))
        poly(c, face, -0.25f, -0.02f, -0.12f, -0.2f, -0.03f, -0.02f)
        poly(c, face, 0.25f, -0.02f, 0.12f, -0.2f, 0.03f, -0.02f)
        poly(c, face, -0.3f, 0.08f, -0.15f, 0.2f, 0f, 0.12f, 0.15f, 0.2f, 0.3f, 0.08f, 0.15f, 0.3f, -0.15f, 0.3f)
        c.restore()
    }

    private fun cloud(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val p = fill(color).apply { setShadowLayer(s * 0.05f, 0f, s * 0.03f, Color.argb(40, 0, 0, 0)) }
        val path = Path().apply {
            addCircle(cx - s * 0.28f, cy, s * 0.22f, Path.Direction.CW)
            addCircle(cx, cy - s * 0.1f, s * 0.3f, Path.Direction.CW)
            addCircle(cx + s * 0.3f, cy + s * 0.02f, s * 0.2f, Path.Direction.CW)
            addRoundRect(RectF(cx - s * 0.5f, cy, cx + s * 0.5f, cy + s * 0.2f), s * 0.1f, s * 0.1f, Path.Direction.CW)
        }
        c.drawPath(path, p)
    }

    private fun crescent(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val a = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val b = Path().apply { addCircle(cx - r * 0.45f, cy - r * 0.25f, r * 0.85f, Path.Direction.CW) }
        a.op(b, Path.Op.DIFFERENCE)
        c.drawPath(a, fill(color))
    }

    private fun sunDoodle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, w: Float) {
        val p = stroke(color, w)
        c.drawCircle(cx, cy, r * 0.5f, p)
        for (i in 0 until 8) {
            val a = i * PI / 4
            c.drawLine(cx + (r * 0.7f * cos(a)).toFloat(), cy + (r * 0.7f * sin(a)).toFloat(), cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat(), p)
        }
    }

    // ───────────────────────────── UTILIDADES ─────────────────────────────

    private fun text(
        l: L,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        family: String = "sans-serif",
        style: Int = Typeface.NORMAL,
        align: Paint.Align = Paint.Align.CENTER,
        shadow: Boolean = false,
        glow: Int? = null,
        spacing: Float = 0f,
        maxWidth: Float = l.w * 0.9f,
    ) {
        val p = fill(color).apply {
            textSize = size
            textAlign = align
            typeface = Typeface.create(family, style)
            letterSpacing = spacing
            if (shadow) setShadowLayer(size * 0.12f, 0f, size * 0.05f, Color.argb(130, 0, 0, 0))
        }
        val width = p.measureText(s)
        if (width > maxWidth) p.textSize = size * maxWidth / width
        if (glow != null) {
            val g = Paint(p).apply {
                this.color = glow
                maskFilter = BlurMaskFilter(p.textSize * 0.22f, BlurMaskFilter.Blur.NORMAL)
                clearShadowLayer()
            }
            l.c.drawText(s, x, y, g)
        }
        l.c.drawText(s, x, y, p)
    }

    private fun today(pattern: String = "d MMM yyyy"): String =
        SimpleDateFormat(pattern, Locale("es", "ES")).format(dateOverride.get() ?: System.currentTimeMillis())

    private fun poly(c: Canvas, p: Paint, vararg pts: Float) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        c.drawPath(path, p)
    }

    private fun inflate(r: RectF, d: Float) = RectF(r.left - d, r.top - d, r.right + d, r.bottom + d)
    private fun inset(l: L, d: Float) = RectF(d, d, l.w - d, l.h - d)

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun col(hex: Long): Int = hex.toInt()
}
