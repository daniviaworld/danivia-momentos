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
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.data.Prefs
import com.daniviaworld.momentos.data.Repo
import com.daniviaworld.momentos.ui.MomentosTheme
import com.daniviaworld.momentos.ui.views.CubeView
import com.daniviaworld.momentos.ui.views.SlideshowView
import com.google.firebase.firestore.ListenerRegistration

/**
 * Salvapantallas de Android TV: cuando la tele está encendida sin usarse,
 * muestra las fotos de la familia en diapositivas (o en el cubo, si es el modo elegido en la app).
 */
class MomentosDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var photos by mutableStateOf<List<Photo>>(emptyList())
    private var listener: ListenerRegistration? = null

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
            listener = Repo.listenPhotos(fid) { list -> photos = list }
        }
        val cube = prefs.viewMode == "cube"

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MomentosDream)
            setViewTreeSavedStateRegistryOwner(this@MomentosDream)
            setContent {
                MomentosTheme {
                    val list = photos
                    if (list.isNotEmpty()) {
                        if (cube) CubeView(list, isTv = true, onOpen = {})
                        else SlideshowView(list, isTv = true, onOpen = {})
                    }
                }
            }
        }
        setContentView(view)
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
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
