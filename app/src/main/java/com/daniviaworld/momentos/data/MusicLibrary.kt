package com.daniviaworld.momentos.data

import android.content.Context
import android.net.Uri
import com.daniviaworld.momentos.R

/** Música original de DANIVIA Momentos (compuesta para la app, libre de derechos). */
object MusicLibrary {

    data class Track(val key: String, val name: String, val emoji: String, val resId: Int) {
        val label: String get() = "$emoji $name"
    }

    val tracks = listOf(
        Track("alegre", "Alegre", "☀️", R.raw.musica_alegre),
        Track("tranquila", "Tranquila", "🌙", R.raw.musica_tranquila),
        Track("aventura", "Aventura", "🗺️", R.raw.musica_aventura),
        Track("fiesta", "Fiesta", "🎉", R.raw.musica_fiesta),
        Track("emotiva", "Emotiva", "💖", R.raw.musica_emotiva),
        Track("navidad", "Navidad", "🎄", R.raw.musica_navidad),
    )

    fun byKey(key: String?): Track? = tracks.firstOrNull { it.key == key }

    fun uri(context: Context, track: Track): Uri =
        Uri.parse("android.resource://${context.packageName}/${track.resId}")
}
