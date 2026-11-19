package com.daniviaworld.momentos.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daniviaworld.momentos.MainActivity
import com.daniviaworld.momentos.MomentosApp
import com.daniviaworld.momentos.R
import com.daniviaworld.momentos.data.MusicLibrary
import com.daniviaworld.momentos.data.Prefs
import com.daniviaworld.momentos.data.Repo
import com.daniviaworld.momentos.media.AlbumVideoMaker
import com.daniviaworld.momentos.media.Memories
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Una vez al día, mientras el móvil carga y con wifi, busca un momento especial nuevo
 * y prepara su vídeo con música. Avisa con una notificación cuando está listo.
 */
class MemoriesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val UNIQUE = "recuerdos"
        private const val UNIQUE_NOW = "recuerdos_ya"
        const val KEY_NOW = "ahora"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MemoriesWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** "Prepárame un recuerdo ya" (botón en la pantalla de Recuerdos). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<MemoriesWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_NOW to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
        }

        fun workName() = UNIQUE_NOW
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val fid = Prefs(ctx).familyId ?: return Result.success()
        return try {
            Repo.ensureSignedIn()
            val photos = Repo.fetchPhotos(fid)
            val done = Memories.doneKeys(ctx)
            val moment = Memories.findMoments(photos).firstOrNull { it.key !in done }
            if (moment == null) {
                return Result.success(workDataOf("found" to false))
            }
            val track = MusicLibrary.byKey(moment.music) ?: MusicLibrary.tracks.first()
            val video = AlbumVideoMaker.make(
                ctx,
                moment.photos,
                AlbumVideoMaker.Options(
                    title = moment.title,
                    musicUri = MusicLibrary.uri(ctx, track),
                    secondsPerPhoto = 3,
                    maxPhotos = 20,
                ),
            ) { _, _ -> }
            val dest = File(Memories.dir(ctx), "recuerdo_${System.currentTimeMillis()}.mp4")
            video.copyTo(dest, overwrite = true)
            video.delete()
            Memories.add(ctx, moment, dest)
            notifyReady(moment.title, moment.subtitle)
            Result.success(workDataOf("found" to true))
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun notifyReady(title: String, subtitle: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("abrir", "recuerdos")
        val pending = PendingIntent.getActivity(ctx, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(ctx, MomentosApp.CHANNEL_NEW_PHOTOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🎬 Nuevo recuerdo: $title")
            .setContentText("$subtitle · Te hemos preparado un vídeo")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(3001, notification)
        } catch (_: SecurityException) {
        }
    }
}
