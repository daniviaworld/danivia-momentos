package com.daniviaworld.momentos.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.min

/**
 * Pegatina puesta encima de una foto. Posición (x, y) y tamaño en proporción a la foto,
 * así se ve igual en la vista previa pequeña que en la foto grande guardada.
 */
data class Sticker(
    val id: Long,
    val text: String,
    /** Centro, de 0 a 1 sobre el ancho y el alto de la foto. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** Alto de la pegatina en proporción al lado corto de la foto. */
    val size: Float = 0.18f,
    /** true = texto escrito (blanco con borde); false = emoji. */
    val isText: Boolean = false,
)

object Stickers {

    val emojis = listOf(
        "❤️", "😍", "🥰", "😂", "😎", "🥳", "🎉", "🎂", "🎈", "🎁",
        "⭐", "✨", "🌈", "☀️", "🌙", "❄️", "🌸", "🌻", "🍀", "🦋",
        "🎄", "🎅", "🎃", "👻", "🦇", "👑", "💯", "👍", "🙌", "💪",
        "🐶", "🐱", "🦄", "🐻", "🏖️", "🌊", "⚽", "🍕", "🍦", "✈️",
    )

    /** Para guardar en la foto las pegatinas puestas, y poder retocarlas otro día. */
    fun toJson(list: List<Sticker>): String {
        val arr = org.json.JSONArray()
        list.forEach { s ->
            arr.put(
                org.json.JSONObject()
                    .put("t", s.text).put("x", s.x.toDouble()).put("y", s.y.toDouble())
                    .put("s", s.size.toDouble()).put("txt", s.isText)
            )
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<Sticker> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Sticker(
                id = i.toLong() + 1,
                text = o.optString("t"),
                x = o.optDouble("x", 0.5).toFloat(),
                y = o.optDouble("y", 0.5).toFloat(),
                size = o.optDouble("s", 0.18).toFloat(),
                isText = o.optBoolean("txt", false),
            )
        }.filter { it.text.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    /** Dibuja las pegatinas sobre una copia de la foto. */
    fun draw(src: Bitmap, stickers: List<Sticker>): Bitmap {
        if (stickers.isEmpty()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val side = min(out.width, out.height).toFloat()
        for (s in stickers) {
            val size = s.size * side
            val cx = s.x * out.width
            val cy = s.y * out.height
            if (s.isText) {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.12f
                    strokeJoin = Paint.Join.ROUND
                    color = Color.BLACK
                }
                val fill = Paint(stroke).apply {
                    style = Paint.Style.FILL
                    color = Color.WHITE
                    setShadowLayer(size * 0.1f, 0f, size * 0.05f, Color.argb(120, 0, 0, 0))
                }
                val y = cy + size * 0.35f
                c.drawText(s.text, cx, y, stroke)
                c.drawText(s.text, cx, y, fill)
            } else {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size * 0.85f
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(size * 0.06f, 0f, size * 0.03f, Color.argb(90, 0, 0, 0))
                }
                c.drawText(s.text, cx, cy + size * 0.3f, p)
            }
        }
        return out
    }
}
