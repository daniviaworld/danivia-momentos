package com.daniviaworld.momentos.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daniviaworld.momentos.MainActivity
import com.daniviaworld.momentos.MomentosApp
import com.daniviaworld.momentos.R
import com.daniviaworld.momentos.data.Prefs
import com.daniviaworld.momentos.data.Repo
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/** Avisos al instante (los envía la Cloud Function cuando alguien sube algo o reacciona). */
class MomentosMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val prefs = Prefs(this)
        val fid = prefs.familyId ?: return
        val code = prefs.familyCode
        if (code.isBlank()) return
        MomentosApp.scope.launch {
            try {
                Repo.saveFcmToken(fid, token, prefs.myName, "phone", code)
            } catch (_: Exception) {
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val prefs = Prefs(this)
        // a partir de ahora el aviso periódico no repite estos avisos
        prefs.pushActive = true
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "DANIVIA Momentos"
        val body = data["body"] ?: message.notification?.body ?: return
        val tag = data["tag"] ?: "momentos"

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, MomentosApp.CHANNEL_NEW_PHOTOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            // misma etiqueta = se sustituye (si suben 20 fotos, sale un solo aviso)
            NotificationManagerCompat.from(this).notify(tag, 2001, notification)
        } catch (_: SecurityException) {
        }
    }
}
