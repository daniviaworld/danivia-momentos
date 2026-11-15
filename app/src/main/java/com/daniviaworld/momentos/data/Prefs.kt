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
