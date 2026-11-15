package com.daniviaworld.momentos.image

import android.graphics.Bitmap
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Mejora automática de fotos, pensada para fotos de móvil "regulares":
 *  1. Niveles automáticos: estira el contraste si la foto está apagada o lavada.
 *  2. Brillo automático (gamma): aclara fotos oscuras y templa las quemadas.
 *  3. Balance de blancos suave: corrige dominantes amarillas/azules sin matar atardeceres.
 *  4. Un poco más de color (saturación).
 *  5. Enfoque suave (máscara de enfoque) para fotos algo movidas.
 * Si la foto ya está bien, los cambios son mínimos.
 */
object Enhancer {

    fun enhance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // ---------- 1. Estadísticas (sobre una muestra) ----------
        val hist = IntArray(256)
        var neutralR = 0L
        var neutralG = 0L
        var neutralB = 0L
        var neutralCount = 0
        var satSum = 0.0
        var count = 0
        val step = max(1, n / 250_000)
        var i = 0
        while (i < n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            hist[lum]++
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val s = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            satSum += s
            // Píxeles "casi grises" de brillo medio: sirven para detectar dominantes de color
            if (s < 0.2 && lum in 40..235) {
                neutralR += r; neutralG += g; neutralB += b
                neutralCount++
            }
            count++
            i += step
        }
        if (count == 0) return src

        // ---------- Contraste: sólo se estira si de verdad falta rango ----------
        val pLo = min(percentile(hist, count, 0.005), 50)
        val pHi = max(percentile(hist, count, 0.995), 180)
        val lo = if (pLo > 10) pLo * 0.7 else 0.0
        val hi = if (pHi < 245) 255.0 - (255 - pHi) * 0.7 else 255.0

        // ---------- 2. Balance de blancos: sólo con zonas neutras y si hay dominante clara ----------
        var gR = 1.0
        var gG = 1.0
        var gB = 1.0
        if (neutralCount > count / 50) {
            val aR = neutralR.toDouble() / neutralCount
            val aG = neutralG.toDouble() / neutralCount
            val aB = neutralB.toDouble() / neutralCount
            val gray = (aR + aG + aB) / 3.0
            val cast = max(aR, max(aG, aB)) / max(1.0, min(aR, min(aG, aB)))
            if (cast > 1.15) {
                fun gain(avg: Double) = (1.0 + (gray / max(1.0, avg) - 1.0) * 0.6).coerceIn(0.9, 1.12)
                gR = gain(aR); gG = gain(aG); gB = gain(aB)
            }
        }

        // ---------- 3. Brillo: aclara las oscuras y templa las quemadas ----------
        var meanLum = 0.0
        for (v in 0..255) meanLum += v.toDouble() * hist[v]
        meanLum /= count
        val m = ((meanLum - lo) / (hi - lo)).coerceIn(0.05, 0.95)
        val gamma = when {
            m < 0.42 -> (ln(0.46) / ln(m)).coerceIn(0.6, 1.0)
            m > 0.62 -> (ln(0.56) / ln(m)).coerceIn(1.0, 1.25)
            else -> 1.0
        }

        // ---------- Saturación: más color sólo si la foto está apagada ----------
        val avgSat = satSum / count
        val satBoost = if (avgSat < 0.35) (1.0 + (0.35 - avgSat) * 0.8).coerceAtMost(1.2) else 1.03

        // Tablas de conversión por canal
        val lutR = buildLut(gR, lo, hi, gamma)
        val lutG = buildLut(gG, lo, hi, gamma)
        val lutB = buildLut(gB, lo, hi, gamma)

        // ---------- 4. Aplicar tono + saturación ----------
        val sat = satBoost.toFloat()
        val toned = IntArray(n)
        for (k in 0 until n) {
            val c = px[k]
            var r = lutR[(c shr 16) and 0xFF].toFloat()
            var g = lutG[(c shr 8) and 0xFF].toFloat()
            var b = lutB[c and 0xFF].toFloat()
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            r = l + (r - l) * sat
            g = l + (g - l) * sat
            b = l + (b - l) * sat
            toned[k] = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }

        // ---------- 5. Enfoque suave (máscara de enfoque 3x3) ----------
        val amount = 0.35f
        val out = px // reutilizamos el array original para ahorrar memoria
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    out[idx] = toned[idx]
                    continue
                }
                var sr = 0
                var sg = 0
                var sb = 0
                for (dy in -1..1) {
                    val base = idx + dy * w
                    for (dx in -1..1) {
                        val c = toned[base + dx]
                        sr += (c shr 16) and 0xFF
                        sg += (c shr 8) and 0xFF
                        sb += c and 0xFF
                    }
                }
                val c = toned[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val nr = r + amount * (r - sr / 9f)
                val ng = g + amount * (g - sg / 9f)
                val nb = b + amount * (b - sb / 9f)
                out[idx] = (0xFF shl 24) or (clamp(nr) shl 16) or (clamp(ng) shl 8) or clamp(nb)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /** Ajustes para vídeo (brillo, contraste y saturación), calculados a partir de un fotograma. */
    class VideoTuning(val brightness: Float, val contrast: Float, val saturation: Float) {
        val isNeutral: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f
    }

    fun analyze(frame: Bitmap): VideoTuning {
        val w = frame.width
        val h = frame.height
        val n = w * h
        if (n == 0) return VideoTuning(0f, 0f, 0f)
        val px = IntArray(n)
        frame.getPixels(px, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        var satSum = 0.0
        var count = 0
        val step = max(1, n / 100_000)
        var i = 0
        while (i < n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            hist[(r * 299 + g * 587 + b * 114) / 1000]++
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            satSum += if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            count++
            i += step
        }
        var mean = 0.0
        for (v in 0..255) mean += v.toDouble() * hist[v]
        mean /= count * 255.0
        val range = (percentile(hist, count, 0.995) - percentile(hist, count, 0.005)) / 255.0
        val avgSat = satSum / count

        val brightness = when {
            mean < 0.40 -> ((0.47 - mean) * 0.6).coerceIn(0.0, 0.18)
            mean > 0.64 -> ((0.56 - mean) * 0.5).coerceIn(-0.08, 0.0)
            else -> 0.0
        }
        val contrast = if (range < 0.72) ((0.85 - range) * 0.5).coerceIn(0.0, 0.22) else 0.0
        val saturation = if (avgSat < 0.35) ((0.35 - avgSat) * 60).coerceIn(0.0, 15.0) else 0.0
        return VideoTuning(brightness.toFloat(), contrast.toFloat(), saturation.toFloat())
    }

    private fun buildLut(gain: Double, lo: Double, hi: Double, gamma: Double): IntArray {
        val lut = IntArray(256)
        val range = hi - lo
        for (v in 0..255) {
            val x = ((v * gain - lo) / range).coerceIn(0.0, 1.0)
            lut[v] = (x.pow(gamma) * 255.0).roundToInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun percentile(hist: IntArray, total: Int, p: Double): Int {
        val target = (total * p).toLong()
        var acc = 0L
        for (v in 0..255) {
            acc += hist[v]
            if (acc > target) return v
        }
        return 255
    }

    private fun clamp(v: Float): Int = when {
        v < 0f -> 0
        v > 255f -> 255
        else -> v.toInt()
    }
}
