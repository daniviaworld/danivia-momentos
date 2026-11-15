package com.daniviaworld.momentos.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.daniviaworld.momentos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Autoactualización desde las Releases de GitHub (como en DANIVIA Home):
 * 1. Mira la última release del repositorio.
 * 2. Si su número es mayor que el de esta versión, pregunta al usuario.
 * 3. Descarga el APK y abre el instalador de Android encima de la app actual.
 */
object Updater {

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String,
    )

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val isConfigured: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    /** Devuelve la nueva versión si la hay, o null si ya está al día (o no se pudo comprobar). */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        try {
            val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "DaniviaMomentos")
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()

            val tag = json.optString("tag_name")
            // Las etiquetas son v1.0.<número de compilación>; ese número es el versionCode
            val code = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull()
                ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isNullOrBlank()) return@withContext null
            Release(
                versionCode = code,
                versionName = tag.removePrefix("v"),
                apkUrl = apkUrl,
                notes = json.optString("body"),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Descarga el APK a la caché. [onProgress] va de 0 a 1. */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "DaniviaMomentos-${release.versionCode}.apk")
            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "DaniviaMomentos")
            if (conn.responseCode !in 200..299) error("Error ${conn.responseCode} al descargar")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                withContext(Dispatchers.Main) { onProgress(pct / 100f) }
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            file
        }

    /** ¿Tiene la app permiso para instalar? (Android 8+ lo pide una vez). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre el ajuste "Instalar apps desconocidas" de esta app. */
    fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Lanza el instalador de Android con el APK descargado. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
