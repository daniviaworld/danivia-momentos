package com.daniviaworld.momentos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.daniviaworld.momentos.work.NewPhotosWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MomentosApp : Application(), ImageLoaderFactory {

    companion object {
        /** Ámbito que sobrevive a las pantallas: las subidas siguen aunque cambies de pantalla. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        const val CHANNEL_NEW_PHOTOS = "fotos_nuevas"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_NEW_PHOTOS, "Fotos nuevas", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Aviso cuando alguien de la familia sube fotos" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        if (!isTelevision(this)) NewPhotosWorker.schedule(this)
        // recuerdos automáticos (también en la tele, que siempre está enchufada)
        com.daniviaworld.momentos.work.MemoriesWorker.schedule(this)
    }

    /** Coil con soporte para miniaturas de vídeo (pantalla de subir). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}

fun isTelevision(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
