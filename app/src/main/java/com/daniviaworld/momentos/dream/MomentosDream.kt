package com.daniviaworld.momentos.dream

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.daniviaworld.momentos.data.FamilySong
import com.daniviaworld.momentos.data.MusicLibrary
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.ui.BackgroundMusic
import com.daniviaworld.momentos.ui.views.CarouselView
import com.daniviaworld.momentos.ui.views.TableView
import com.daniviaworld.momentos.data.Prefs
import com.daniviaworld.momentos.data.Repo
import com.daniviaworld.momentos.ui.MomentosTheme
import com.daniviaworld.momentos.ui.views.CubeView
import com.daniviaworld.momentos.ui.views.SlideshowView
import com.google.firebase.firestore.ListenerRegistration

/**
 * Salvapantallas de Android TV: cuando la tele está encendida sin usarse,
 * muestra las fotos de la familia en el modo elegido en Familia (cubo 3D, diapositivas o carrusel).
 */
class MomentosDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var photos by mutableStateOf<List<Photo>>(emptyList())
    private var songs by mutableStateOf<List<FamilySong>>(emptyList())
    private var listener: ListenerRegistration? = null
    private var songsListener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        val prefs = Prefs(this)
        val fid = prefs.familyId
        if (fid != null && Repo.uid != null) {
            val source = prefs.screensaverSource
            listener = Repo.listenPhotos(fid) { list -> photos = forScreensaver(list, source) }
            if (prefs.screensaverMusic) songsListener = Repo.listenSongs(fid) { songs = it }
        }
        val mode = prefs.screensaverMode
        val musicKey = if (prefs.screensaverMusic) prefs.backgroundMusic else ""

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MomentosDream)
            setViewTreeSavedStateRegistryOwner(this@MomentosDream)
            setContent {
                MomentosTheme {
                    val list = photos
                    val music = MusicLibrary.resolve(this@MomentosDream, musicKey, songs)
                    if (list.isNotEmpty()) {
                        when (mode) {
                            "slideshow" -> SlideshowView(list, isTv = true, onOpen = {}, musicUri = music)
                            "carousel" -> {
                                CarouselView(list, isTv = true, onOpen = {})
                                BackgroundMusic(music)
                            }
                            "table" -> {
                                TableView(list, isTv = true, onOpen = {}, secondsPerPhoto = 7)
                                BackgroundMusic(music)
                            }
                            else -> CubeView(list, isTv = true, onOpen = {}, musicUri = music)
                        }
                    }
                }
            }
        }
        setContentView(view)
    }

    /** Sólo fotos (nunca vídeos), y las elegidas: todas, las más queridas o las de un álbum. */
    private fun forScreensaver(all: List<Photo>, source: String): List<Photo> {
        val onlyPhotos = all.filter { !it.isVideo }
        val chosen = when {
            source == "loved" -> onlyPhotos.filter { it.likes.isNotEmpty() || it.reactions.isNotEmpty() }
            source.startsWith("album:") -> onlyPhotos.filter { it.albumId == source.removePrefix("album:") }
            else -> onlyPhotos
        }
        // si el álbum elegido está vacío, mejor enseñar todas que una pantalla negra
        return chosen.ifEmpty { onlyPhotos }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        listener?.remove()
        listener = null
        songsListener?.remove()
        songsListener = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
