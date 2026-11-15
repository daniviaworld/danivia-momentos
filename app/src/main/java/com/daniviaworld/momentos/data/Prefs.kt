package com.daniviaworld.momentos.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("momentos", Context.MODE_PRIVATE)

    var familyId: String?
        get() = sp.getString("familyId", null)
        set(value) = sp.edit().putString("familyId", value).apply()

    var myName: String
        get() = sp.getString("myName", "") ?: ""
        set(value) = sp.edit().putString("myName", value).apply()

    var viewMode: String?
        get() = sp.getString("viewMode", null)
        set(value) = sp.edit().putString("viewMode", value).apply()

    /** Música de fondo en diapositivas y cubo ("" = sin música). */
    var backgroundMusic: String
        get() = sp.getString("bgMusic", "") ?: ""
        set(value) = sp.edit().putString("bgMusic", value).apply()

    /** Código de la familia (para guardar el token de avisos). */
    var familyCode: String
        get() = sp.getString("familyCode", "") ?: ""
        set(value) = sp.edit().putString("familyCode", value).apply()

    /** Fotos del salvapantallas: "all", "loved" o "album:<id>". Nunca vídeos. */
    var screensaverSource: String
        get() = sp.getString("ssSource", "all") ?: "all"
        set(value) = sp.edit().putString("ssSource", value).apply()

    /** Modo del salvapantallas: "cube", "slideshow" o "carousel". */
    var screensaverMode: String
        get() = sp.getString("ssMode", "cube") ?: "cube"
        set(value) = sp.edit().putString("ssMode", value).apply()

    /** Si el salvapantallas suena con la música de fondo elegida. */
    var screensaverMusic: Boolean
        get() = sp.getBoolean("ssMusic", false)
        set(value) = sp.edit().putBoolean("ssMusic", value).apply()

    var lastWidgetDay: Int
        get() = sp.getInt("lastWidgetDay", 0)
        set(value) = sp.edit().putInt("lastWidgetDay", value).apply()

    /** Último día (yyyyDDD) en que se avisó de "Tal día como hoy". */
    var lastMemoryDay: Int
        get() = sp.getInt("lastMemoryDay", 0)
        set(value) = sp.edit().putInt("lastMemoryDay", value).apply()

    /** true cuando llegan avisos instantáneos (FCM): el aviso periódico deja de duplicarlos. */
    var pushActive: Boolean
        get() = sp.getBoolean("pushActive", false)
        set(value) = sp.edit().putBoolean("pushActive", value).apply()

    var autoEnhance: Boolean
        get() = sp.getBoolean("autoEnhance", true)
        set(value) = sp.edit().putBoolean("autoEnhance", value).apply()

    /** Fecha (ms) del último me gusta/comentario ya avisado. */
    var lastActivityMillis: Long
        get() = sp.getLong("lastActivity", 0L)
        set(value) = sp.edit().putLong("lastActivity", value).apply()

    /** Fecha (ms) de la foto más nueva ya vista o notificada. */
    var lastNotifiedMillis: Long
        get() = sp.getLong("lastNotified", 0L)
        set(value) = sp.edit().putLong("lastNotified", value).apply()
}
