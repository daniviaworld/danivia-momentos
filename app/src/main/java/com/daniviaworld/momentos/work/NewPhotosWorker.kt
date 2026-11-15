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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daniviaworld.momentos.MainActivity
import com.daniviaworld.momentos.MomentosApp
import com.daniviaworld.momentos.R
import com.daniviaworld.momentos.data.Prefs
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

/** Cada ~30 minutos mira si hay fotos nuevas de otros y avisa con una notificación. */
class NewPhotosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewPhotosWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "fotos_nuevas", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val fid = prefs.familyId ?: return Result.success()
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val since = prefs.lastNotifiedMillis
        if (since == 0L) {
            prefs.lastNotifiedMillis = System.currentTimeMillis()
            prefs.lastActivityMillis = System.currentTimeMillis()
            return Result.success()
        }
        checkActivity(fid, me, prefs)
        return try {
            val snap = FirebaseFirestore.getInstance()
                .collection("families").document(fid).collection("photos")
                .whereGreaterThan("createdAt", Timestamp(Date(since)))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()
            val newest = snap.documents.firstOrNull()?.getTimestamp("createdAt")?.toDate()?.time
            if (newest != null && newest > since) prefs.lastNotifiedMillis = newest
            val fromOthers = snap.documents.filter { it.getString("uploaderUid") != me }
            if (fromOthers.isNotEmpty()) {
                val names = fromOthers.mapNotNull { it.getString("uploaderName") }.distinct()
                notify(fromOthers.size, names)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Me gusta y comentarios en mis fotos. */
    private suspend fun checkActivity(fid: String, me: String, prefs: Prefs) {
        val since = prefs.lastActivityMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        try {
            val snap = FirebaseFirestore.getInstance()
                .collection("families").document(fid).collection("activity")
                .whereGreaterThan("createdAt", Timestamp(Date(since)))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()
            val newest = snap.documents.firstOrNull()?.getTimestamp("createdAt")?.toDate()?.time
            prefs.lastActivityMillis = if (newest != null && newest > since) newest else since
            val mine = snap.documents.filter { it.getString("toUid") == me && it.getString("fromUid") != me }
            if (mine.isEmpty()) return
            val likes = mine.filter { it.getString("type") == "like" }
            val comments = mine.filter { it.getString("type") == "comment" }
            val text = when {
                mine.size == 1 && likes.size == 1 -> {
                    val d = likes[0]
                    val what = if (d.getBoolean("isVideo") == true) "tu vídeo" else "tu foto"
                    "A ${d.getString("fromName") ?: "alguien"} le ha gustado $what ❤️"
                }
                mine.size == 1 && comments.size == 1 -> {
                    val d = comments[0]
                    "${d.getString("fromName") ?: "Alguien"} ha comentado: «${d.getString("text") ?: ""}»"
                }
                else -> buildString {
                    if (likes.isNotEmpty()) append("${likes.size} me gusta ❤️")
                    if (likes.isNotEmpty() && comments.isNotEmpty()) append(" y ")
                    if (comments.isNotEmpty()) append("${comments.size} comentario${if (comments.size > 1) "s" else ""} 💬")
                    append(" en tus fotos")
                }
            }
            show(1002, text)
        } catch (_: Exception) {
        }
    }

    private fun notify(count: Int, names: List<String>) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val who = when (names.size) {
            0 -> "Alguien"
            1 -> names[0]
            2 -> "${names[0]} y ${names[1]}"
            else -> "${names[0]}, ${names[1]} y más"
        }
        val text = if (count == 1) "$who ha subido algo nuevo" else "$who: $count fotos y vídeos nuevos"
        show(1001, text)
    }

    private fun show(id: Int, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(ctx, MomentosApp.CHANNEL_NEW_PHOTOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DANIVIA Momentos")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
