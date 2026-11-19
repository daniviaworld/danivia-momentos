package com.daniviaworld.momentos.media

import java.io.OutputStream

/**
 * Codificador de GIF animado (sin librerías): paleta de 256 colores por fotograma con
 * "median cut", difuminado Floyd–Steinberg para que los degradados no se vean a bandas,
 * y compresión LZW. Los píxeles llegan como ARGB (como los da Bitmap.getPixels).
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    /** 0 = repetir para siempre. */
    loop: Int = 0,
) {
    private val index = ByteArray(width * height)

    init {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width); short(height)
        out.write(0x70) // sin paleta global, 8 bits de color
        out.write(0); out.write(0)
        // bucle (extensión NETSCAPE2.0)
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1); short(loop); out.write(0)
    }

    /** [delayCs] = cuánto se ve el fotograma, en centésimas de segundo. */
    fun addFrame(argb: IntArray, delayCs: Int, dither: Boolean = true) {
        require(argb.size == width * height)
        val palette = buildPalette(argb)
        mapPixels(argb, palette, dither)
        // control gráfico
        out.write(0x21); out.write(0xF9); out.write(4)
        out.write(0x04) // no borrar
        short(delayCs.coerceIn(2, 65535))
        out.write(0); out.write(0)
        // descriptor de imagen con paleta local de 256 colores
        out.write(0x2C)
        short(0); short(0); short(width); short(height)
        out.write(0x87)
        for (i in 0 until 256) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        Lzw(out).encode(index)
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    private fun short(v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
    }

    // ---------- Paleta (median cut sobre un histograma de 15 bits) ----------

    private fun bin(r: Int, g: Int, b: Int) = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)

    private fun buildPalette(argb: IntArray): IntArray {
        val hist = IntArray(32768)
        for (p in argb) hist[bin((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)]++
        var bins = IntArray(0)
        run {
            var n = 0
            for (h in hist) if (h > 0) n++
            bins = IntArray(n)
            var k = 0
            for (i in hist.indices) if (hist[i] > 0) bins[k++] = i
        }
        // cajas: [inicio, fin) dentro de "bins"
        val boxes = ArrayList<IntArray>()
        boxes.add(intArrayOf(0, bins.size))
        fun ch(b: Int, c: Int) = when (c) { 0 -> (b shr 10) and 31; 1 -> (b shr 5) and 31; else -> b and 31 }
        while (boxes.size < 256) {
            // la caja con más "volumen" (rango × píxeles) que se pueda partir
            var best = -1
            var bestScore = -1L
            var bestCh = 0
            for ((bi, box) in boxes.withIndex()) {
                if (box[1] - box[0] < 2) continue
                var count = 0L
                val lo = IntArray(3) { 31 }
                val hi = IntArray(3)
                for (k in box[0] until box[1]) {
                    val b = bins[k]
                    count += hist[b]
                    for (c in 0..2) { val v = ch(b, c); if (v < lo[c]) lo[c] = v; if (v > hi[c]) hi[c] = v }
                }
                var c = 0
                var range = -1
                for (cc in 0..2) if (hi[cc] - lo[cc] > range) { range = hi[cc] - lo[cc]; c = cc }
                if (range <= 0) continue
                val score = range.toLong() * count
                if (score > bestScore) { bestScore = score; best = bi; bestCh = c }
            }
            if (best < 0) break
            val box = boxes[best]
            val sub = bins.copyOfRange(box[0], box[1]).sortedBy { ch(it, bestCh) }
            for (k in sub.indices) bins[box[0] + k] = sub[k]
            var total = 0L
            for (k in box[0] until box[1]) total += hist[bins[k]]
            var acc = 0L
            var cut = box[0] + 1
            for (k in box[0] until box[1] - 1) {
                acc += hist[bins[k]]
                if (acc * 2 >= total) { cut = k + 1; break }
                cut = k + 2
            }
            cut = cut.coerceIn(box[0] + 1, box[1] - 1)
            boxes[best] = intArrayOf(box[0], cut)
            boxes.add(intArrayOf(cut, box[1]))
        }
        return IntArray(boxes.size) { i ->
            val box = boxes[i]
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (k in box[0] until box[1]) {
                val bb = bins[k]
                val h = hist[bb].toLong()
                r += (((bb shr 10) and 31) * 8 + 4) * h
                g += (((bb shr 5) and 31) * 8 + 4) * h
                b += ((bb and 31) * 8 + 4) * h
                n += h
            }
            if (n == 0L) 0 else (((r / n).toInt()) shl 16) or (((g / n).toInt()) shl 8) or (b / n).toInt()
        }
    }

    private fun mapPixels(argb: IntArray, palette: IntArray, dither: Boolean) {
        val lut = IntArray(32768) { -1 }
        fun nearest(r: Int, g: Int, b: Int): Int {
            val key = bin(r, g, b)
            val cached = lut[key]
            if (cached >= 0) return cached
            var best = 0
            var bestD = Int.MAX_VALUE
            for (i in palette.indices) {
                val c = palette[i]
                val dr = ((c shr 16) and 0xFF) - r
                val dg = ((c shr 8) and 0xFF) - g
                val db = (c and 0xFF) - b
                val d = dr * dr * 2 + dg * dg * 4 + db * db * 3
                if (d < bestD) { bestD = d; best = i }
            }
            lut[key] = best
            return best
        }
        if (!dither) {
            for (i in argb.indices) {
                val p = argb[i]
                index[i] = nearest((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF).toByte()
            }
            return
        }
        // Floyd–Steinberg con errores de dos filas
        var errCur = IntArray((width + 2) * 3)
        var errNext = IntArray((width + 2) * 3)
        for (y in 0 until height) {
            java.util.Arrays.fill(errNext, 0)
            for (x in 0 until width) {
                val i = y * width + x
                val p = argb[i]
                val e = (x + 1) * 3
                val r = (((p shr 16) and 0xFF) + errCur[e] / 16).coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) + errCur[e + 1] / 16).coerceIn(0, 255)
                val b = ((p and 0xFF) + errCur[e + 2] / 16).coerceIn(0, 255)
                val idx = nearest(r, g, b)
                index[i] = idx.toByte()
                val c = palette[idx]
                val er = r - ((c shr 16) and 0xFF)
                val eg = g - ((c shr 8) and 0xFF)
                val eb = b - (c and 0xFF)
                // derecha 7/16, abajo-izq 3/16, abajo 5/16, abajo-der 1/16
                errCur[e + 3] += er * 7; errCur[e + 4] += eg * 7; errCur[e + 5] += eb * 7
                errNext[e - 3] += er * 3; errNext[e - 2] += eg * 3; errNext[e - 1] += eb * 3
                errNext[e] += er * 5; errNext[e + 1] += eg * 5; errNext[e + 2] += eb * 5
                errNext[e + 3] += er; errNext[e + 4] += eg; errNext[e + 5] += eb
            }
            val t = errCur; errCur = errNext; errNext = t
        }
    }

    // ---------- Compresión LZW (igual que el clásico "compress" de los GIF) ----------

    private class Lzw(private val out: OutputStream) {
        private val clear = 256
        private val eoi = 257
        private var nBits = 9
        private var maxCode = 511
        private var freeEnt = 258
        private var clearFlag = false
        private val dictCode = IntArray(4096 * 256)
        private val dictStamp = IntArray(4096 * 256)
        private var stamp = 1

        private var acc = 0
        private var accBits = 0
        private val block = ByteArray(255)
        private var blockLen = 0

        fun encode(pixels: ByteArray) {
            out.write(8) // tamaño mínimo de código
            output(clear)
            var ent = pixels[0].toInt() and 0xFF
            for (i in 1 until pixels.size) {
                val c = pixels[i].toInt() and 0xFF
                val key = (ent shl 8) or c
                if (dictStamp[key] == stamp) {
                    ent = dictCode[key]
                    continue
                }
                output(ent)
                ent = c
                if (freeEnt < 4096) {
                    dictStamp[key] = stamp
                    dictCode[key] = freeEnt++
                } else {
                    stamp++
                    freeEnt = 258
                    clearFlag = true
                    output(clear)
                }
            }
            output(ent)
            output(eoi)
            // vaciar
            while (accBits > 0) {
                byte(acc and 0xFF)
                acc = acc ushr 8
                accBits = (accBits - 8).coerceAtLeast(0)
            }
            flushBlock()
            out.write(0) // fin de datos de la imagen
        }

        private fun output(code: Int) {
            acc = acc or (code shl accBits)
            accBits += nBits
            while (accBits >= 8) {
                byte(acc and 0xFF)
                acc = acc ushr 8
                accBits -= 8
            }
            if (freeEnt > maxCode || clearFlag) {
                if (clearFlag) {
                    nBits = 9
                    maxCode = 511
                    clearFlag = false
                } else {
                    nBits++
                    maxCode = if (nBits == 12) 4096 else (1 shl nBits) - 1
                }
            }
        }

        private fun byte(b: Int) {
            block[blockLen++] = b.toByte()
            if (blockLen == 255) flushBlock()
        }

        private fun flushBlock() {
            if (blockLen > 0) {
                out.write(blockLen)
                out.write(block, 0, blockLen)
                blockLen = 0
            }
        }
    }
}
