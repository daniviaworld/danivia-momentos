package com.daniviaworld.momentos.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Lee la foto, la endereza, la reduce a un tamaño razonable, la mejora y genera miniatura. */
object ImageProcessor {

    class Result(val full: ByteArray, val thumb: ByteArray, val width: Int, val height: Int)

    private const val MAX_SIDE = 2560
    private const val THUMB_SIDE = 520

    fun process(context: Context, uri: Uri, enhance: Boolean): Result {
        val source = decode(context, uri, MAX_SIDE)
        val finalBmp = if (enhance) Enhancer.enhance(source) else source
        if (finalBmp !== source) source.recycle()
        val full = compress(finalBmp, 88)
        val thumbBmp = scaleToMax(finalBmp, THUMB_SIDE)
        val thumb = compress(thumbBmp, 80)
        val w = finalBmp.width
        val h = finalBmp.height
        if (thumbBmp !== finalBmp) thumbBmp.recycle()
        finalBmp.recycle()
        return Result(full, thumb, w, h)
    }

    /** Versión pequeña para enseñar el "antes y después" en pantalla. */
    fun preview(context: Context, uri: Uri, enhance: Boolean, maxSide: Int = 900): Bitmap {
        val source = decode(context, uri, maxSide)
        if (!enhance) return source
        val out = Enhancer.enhance(source)
        if (out !== source) source.recycle()
        return out
    }

    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) error("No se pudo leer la foto")

        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("No se pudo leer la foto")

        bmp = scaleToMax(bmp, maxSide, recycleSource = true)

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return applyOrientation(bmp, orientation)
    }

    private fun applyOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f); m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f); m.postScale(-1f, 1f)
            }
            else -> return bmp
        }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    private fun scaleToMax(bmp: Bitmap, maxSide: Int, recycleSource: Boolean = false): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (longest <= maxSide) return bmp
        val f = maxSide.toFloat() / longest
        val out = Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * f).roundToInt().coerceAtLeast(1),
            (bmp.height * f).roundToInt().coerceAtLeast(1),
            true
        )
        if (recycleSource && out !== bmp) bmp.recycle()
        return out
    }

    private fun compress(bmp: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }
}
