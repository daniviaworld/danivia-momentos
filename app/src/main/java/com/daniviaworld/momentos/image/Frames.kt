package com.daniviaworld.momentos.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

/** Marcos y pegatinas que se pueden poner a una foto al subirla. */
object Frames {

    data class Frame(val key: String, val name: String, val emoji: String) {
        val label: String get() = "$emoji $name"
    }

    val all = listOf(
        Frame("", "Sin marco", "⬜"),
        Frame("polaroid", "Polaroid", "📷"),
        Frame("cumple", "Cumpleaños", "🎂"),
        Frame("corazones", "Corazones", "💕"),
        Frame("verano", "Verano", "☀️"),
        Frame("navidad", "Navidad", "🎄"),
        Frame("cole", "Cole", "🏫"),
    )

    fun apply(src: Bitmap, key: String): Bitmap {
        if (key.isBlank()) return src
        return when (key) {
            "polaroid" -> polaroid(src)
            "cumple" -> themed(src, intArrayOf(0xFFFF4D8D.toInt(), 0xFFFFB03D.toInt()), listOf("🎂", "🎈", "🎉", "🎁"), "¡Feliz cumple!", confetti = true)
            "corazones" -> themed(src, intArrayOf(0xFFFF6FA8.toInt(), 0xFFB06BFF.toInt()), listOf("💕", "❤️", "💖", "💗"), null, confetti = false)
            "verano" -> themed(src, intArrayOf(0xFFFFC23D.toInt(), 0xFF2EC4F2.toInt()), listOf("☀️", "🏖️", "🌊", "🍉"), "¡Verano!", confetti = false)
            "navidad" -> themed(src, intArrayOf(0xFFD62839.toInt(), 0xFF1E7D45.toInt()), listOf("🎄", "❄️", "⭐", "🎅"), "¡Feliz Navidad!", confetti = true)
            "cole" -> themed(src, intArrayOf(0xFF3D7BFF.toInt(), 0xFF35C98B.toInt()), listOf("✏️", "📚", "🎒", "⭐"), null, confetti = false)
            else -> src
        }
    }

    private fun polaroid(src: Bitmap): Bitmap {
        val side = min(src.width, src.height)
        val b = (side * 0.05f).toInt()
        val bottom = (side * 0.17f).toInt()
        val out = Bitmap.createBitmap(src.width + 2 * b, src.height + b + bottom, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(250, 248, 243))
        c.drawBitmap(src, b.toFloat(), b.toFloat(), null)
        val date = SimpleDateFormat("d MMM yyyy", Locale("es", "ES")).format(System.currentTimeMillis())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 55, 70)
            textSize = bottom * 0.32f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        c.drawText(date, out.width / 2f, src.height + b + bottom * 0.58f, p)
        return out
    }

    private fun themed(src: Bitmap, colors: IntArray, emojis: List<String>, caption: String?, confetti: Boolean): Bitmap {
        val side = min(src.width, src.height)
        val b = (side * 0.06f).toInt()
        val bottom = if (caption != null) (side * 0.12f).toInt() else b
        val out = Bitmap.createBitmap(src.width + 2 * b, src.height + b + bottom, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, out.width.toFloat(), out.height.toFloat(), colors, null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), bg)

        if (confetti) {
            val rnd = Random(src.width * 31 + src.height)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            val palette = intArrayOf(Color.WHITE, Color.YELLOW, Color.CYAN, Color.rgb(255, 120, 200), Color.rgb(140, 255, 140))
            repeat(160) {
                dot.color = palette[rnd.nextInt(palette.size)]
                val x = rnd.nextFloat() * out.width
                val y = rnd.nextFloat() * out.height
                val r = side * (0.004f + rnd.nextFloat() * 0.008f)
                c.drawCircle(x, y, r, dot)
            }
        }

        // foto con borde blanco fino y esquinas suaves
        val photoRect = RectF(b.toFloat(), b.toFloat(), (b + src.width).toFloat(), (b + src.height).toFloat())
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val inset = side * 0.008f
        c.drawRoundRect(RectF(photoRect.left - inset, photoRect.top - inset, photoRect.right + inset, photoRect.bottom + inset), inset * 2, inset * 2, white)
        c.drawBitmap(src, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // pegatinas en las esquinas
        val e = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = side * 0.11f; textAlign = Paint.Align.CENTER }
        val off = e.textSize * 0.35f
        val corners = listOf(
            b + off to b + off * 2f,
            out.width - b - off to b + off * 2f,
            b + off to b + src.height - off * 0.2f,
            out.width - b - off to b + src.height - off * 0.2f,
        )
        corners.forEachIndexed { i, (x, y) -> c.drawText(emojis[i % emojis.size], x, y, e) }

        if (caption != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = bottom * 0.5f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(8f, 0f, 3f, Color.argb(120, 0, 0, 0))
            }
            c.drawText(caption, out.width / 2f, b + src.height + bottom * 0.68f, p)
        }
        return out
    }
}
