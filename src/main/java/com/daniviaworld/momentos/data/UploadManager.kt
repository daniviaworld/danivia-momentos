package com.daniviaworld.momentos.data

import android.content.Context
import android.net.Uri
import com.daniviaworld.momentos.MomentosApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cola de subida: una foto detrás de otra, con progreso visible en la pantalla principal. */
object UploadManager {

    data class Progress(
        val total: Int = 0,
        val done: Int = 0,
        val failed: Int = 0,
        val currentIsVideo: Boolean = false,
        val lastError: String? = null,
    ) {
        val running: Boolean get() = done + failed < total
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress
    private val mutex = Mutex()

    fun start(context: Context, fid: String, uris: List<Uri>, enhance: Boolean, myName: String, albumId: String? = null, frame: String = "") {
        if (uris.isEmpty()) return
        val app = context.applicationContext
        val current = _progress.value
        _progress.value = if (current.running) current.copy(total = current.total + uris.size)
        else Progress(total = uris.size)

        MomentosApp.scope.launch {
            for (uri in uris) {
                mutex.withLock {
                    _progress.value = _progress.value.copy(currentIsVideo = Repo.isVideo(app, uri))
                    val ok = try {
                        Repo.uploadMedia(app, fid, uri, enhance, myName, albumId, frame)
                        true
                    } catch (e: Exception) {
                        _progress.value = _progress.value.copy(
                            lastError = if (e is IllegalArgumentException) e.message else null
                        )
                        false
                    }
                    val p = _progress.value
                    _progress.value = if (ok) p.copy(done = p.done + 1) else p.copy(failed = p.failed + 1)
                }
            }
        }
    }

    fun clear() {
        if (!_progress.value.running) _progress.value = Progress()
    }
}
