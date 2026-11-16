package com.daniviaworld.momentos.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daniviaworld.momentos.MainActivity
import com.daniviaworld.momentos.R
import com.daniviaworld.momentos.data.Prefs
import com.daniviaworld.momentos.data.Repo.toPhoto
import com.daniviaworld.momentos.share.Sharer
import com.daniviaworld.momentos.ui.dayLabel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Calendar
import kotlin.math.max

/** Widget "Foto del día": cada día una foto distinta de la familia. */
class PhotoWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "widget_foto_dia",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PhotoWidgetWorker>().build(),
            )
        }

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, PhotoWidget::class.java)).isNotEmpty()
    }
}

class PhotoWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val manager = AppWidgetManager.getInstance(ctx)
        val ids = manager.getAppWidgetIds(ComponentName(ctx, PhotoWidget::class.java))
        if (ids.isEmpty()) return Result.success()

        val views = RemoteViews(ctx.packageName, R.layout.widget_photo)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        val fid = Prefs(ctx).familyId
        if (fid == null || FirebaseAuth.getInstance().currentUser == null) {
            manager.updateAppWidget(ids, views)
            return Result.success()
        }
        return try {
            val docs = FirebaseFirestore.getInstance()
                .collection("families").document(fid).collection("photos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(150)
                .get().await().documents
            val photos = docs.mapNotNull { it.toPhoto() }
            if (photos.isNotEmpty()) {
                // la misma foto todo el día, y otra distinta al día siguiente
                val c = Calendar.getInstance()
                val seed = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)
                val photo = photos[((seed.toLong() * 7919L) % photos.size).toInt()]
                val file = File(ctx.cacheDir, "widget.jpg")
                Sharer.download(photo.thumbUrl, file)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 600) sample *= 2
                val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                if (bmp != null) views.setImageViewBitmap(R.id.widget_image, bmp)
                views.setTextViewText(R.id.widget_caption, "${photo.uploaderName} · ${dayLabel(photo.createdAt)}")
            }
            manager.updateAppWidget(ids, views)
            Result.success()
        } catch (_: Exception) {
            manager.updateAppWidget(ids, views)
            Result.retry()
        }
    }
}
