package com.daniviaworld.momentos.media

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Graba notas de voz cortas (máx. 30 s) en AAC/M4A. */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    @Suppress("DEPRECATION")
    fun start() {
        val f = File(context.cacheDir, "voz_${System.currentTimeMillis()}.m4a")
        val r = MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setMaxDuration(MAX_MS)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
    }

    /** Para la grabación y devuelve el archivo (o null si salió mal). */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        return try {
            r.stop()
            r.release()
            file
        } catch (_: Exception) {
            try {
                r.release()
            } catch (_: Exception) {
            }
            file?.delete()
            null
        }
    }

    fun cancel() {
        stop()?.delete()
    }

    companion object {
        const val MAX_MS = 30_000
    }
}
