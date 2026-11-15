package com.daniviaworld.momentos.image

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepara un vídeo para subirlo:
 *  - lo reduce a 720p (lado corto) y ~4 Mbps para que no ocupe demasiado,
 *  - le aplica una mejora suave de brillo, contraste y color si hace falta,
 *  - saca un fotograma de portada (mejorado) y su miniatura.
 * Si el móvil no puede convertirlo, se sube el original (si no es enorme).
 */
@OptIn(UnstableApi::class)
object VideoProcessor {

    const val MAX_DURATION_MS = 5 * 60_000L
    private const val MAX_ORIGINAL_BYTES = 290L * 1024 * 1024
    private const val SHORT_SIDE = 720
    private const val BITRATE = 4_000_000

    class Prepared(
        val file: File,
        val isTemp: Boolean,
        val poster: ByteArray,
        val thumb: ByteArray,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val enhanced: Boolean,
    )

    private class Info(val width: Int, val height: Int, val durationMs: Long, val frame: Bitmap?)

    suspend fun prepare(context: Context, uri: Uri, enhance: Boolean): Prepared {
        val app = context.applicationContext
        val info = withContext(Dispatchers.IO) { readInfo(app, uri) }
        if (info.durationMs > MAX_DURATION_MS) {
            throw IllegalArgumentException("Un vídeo dura más de 5 minutos. Recórtalo en la galería y vuelve a subirlo.")
        }
        val tuning = if (enhance && info.frame != null) {
            withContext(Dispatchers.Default) { Enhancer.analyze(info.frame) }
        } else null

        val out = File(app.cacheDir, "momentos_${System.nanoTime()}.mp4")
        val targetHeight = targetHeight(info.width, info.height)
        var converted = runCatching { transcode(app, uri, out, targetHeight, tuning?.takeIf { !it.isNeutral }) }.isSuccess
        if (!converted && tuning != null) {
            out.delete()
            converted = runCatching { transcode(app, uri, out, targetHeight, null) }.isSuccess
        }

        val (file, isTemp) = if (converted && out.length() > 0) {
            out to true
        } else {
            out.delete()
            withContext(Dispatchers.IO) { copyOriginal(app, uri) } to true
        }

        // Portada y miniatura
        val frame = info.frame ?: Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        val (poster, thumb) = withContext(Dispatchers.Default) {
            val base = scaleToMax(frame, 1920)
            val improved = if (enhance) Enhancer.enhance(base) else base
            jpeg(improved, 85) to jpeg(scaleToMax(improved, 520), 80)
        }
        return Prepared(
            file = file,
            isTemp = isTemp,
            poster = poster,
            thumb = thumb,
            width = info.width,
            height = info.height,
            durationMs = info.durationMs,
            enhanced = converted && tuning != null && !tuning.isNeutral,
        )
    }

    /**
     * Altura de salida (en orientación de pantalla) para que el lado corto quede en 720 px.
     * null = el vídeo ya es pequeño, no se cambia el tamaño.
     */
    private fun targetHeight(width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0) return SHORT_SIDE
        val shortSide = minOf(width, height)
        if (shortSide <= SHORT_SIDE) return null
        val h = if (height >= width) height.toDouble() * SHORT_SIDE / width else SHORT_SIDE.toDouble()
        return (h.roundToInt() / 2) * 2
    }

    private fun readInfo(context: Context, uri: Uri): Info {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val t = w; w = h; h = t
            }
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val at = minOf(1_000_000L, duration * 1000 / 2)
            val frame = try {
                r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            }
            return Info(w, h, duration, frame)
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Convierte con Media3 Transformer. Debe arrancar en el hilo principal. */
    private suspend fun transcode(
        context: Context,
        uri: Uri,
        out: File,
        targetHeight: Int?,
        tuning: Enhancer.VideoTuning?,
    ): Unit =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val effects = mutableListOf<Effect>()
                if (targetHeight != null) effects += Presentation.createForHeight(targetHeight)
                if (tuning != null) {
                    if (tuning.brightness != 0f) effects += Brightness(tuning.brightness)
                    if (tuning.contrast != 0f) effects += Contrast(tuning.contrast)
                    if (tuning.saturation != 0f) {
                        effects += HslAdjustment.Builder().adjustSaturation(tuning.saturation).build()
                    }
                }
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(BITRATE).build()
                    )
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
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

                val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(Effects(emptyList(), effects))
                    .build()
                val composition = Composition.Builder(listOf(EditedMediaItemSequence(item)))
                    // Vídeos HDR de móviles modernos -> color normal, para que se vean bien en cualquier tele
                    .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build()
                transformer.start(composition, out.absolutePath)
                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }
            }
        }

    private fun copyOriginal(context: Context, uri: Uri): File {
        val out = File(context.cacheDir, "momentos_orig_${System.nanoTime()}.mp4")
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_ORIGINAL_BYTES) {
                        output.close(); out.delete()
                        throw IllegalArgumentException("Un vídeo es demasiado grande para este móvil. Prueba con uno más corto.")
                    }
                    output.write(buf, 0, n)
                }
            }
        } ?: throw IllegalArgumentException("No se pudo leer el vídeo")
        return out
    }

    private fun scaleToMax(bmp: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (longest <= maxSide) return bmp
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * f).roundToInt().coerceAtLeast(1),
            (bmp.height * f).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun jpeg(bmp: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }
}
