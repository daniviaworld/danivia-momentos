package com.daniviaworld.momentos.data

import android.content.Context
import android.net.Uri
import com.daniviaworld.momentos.R

/**
 * Música que viene con la app: canciones reales de Tanner Helland (tannerhelland.com),
 * con licencia Creative Commons Atribución 4.0 — libres de usar citando al autor (se cita en Familia).
 */
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
        Track("hogar", "Hogar", "🏡", R.raw.musica_hogar),
        Track("viaje", "De viaje", "✈️", R.raw.musica_viaje),
        Track("navidad", "Navidad", "🎄", R.raw.musica_navidad),
    )

    fun byKey(key: String?): Track? = tracks.firstOrNull { it.key == key }

    fun uri(context: Context, track: Track): Uri =
        Uri.parse("android.resource://${context.packageName}/${track.resId}")

    /** Clave de una canción de la familia. */
    fun familyKey(song: FamilySong): String = "fam:${song.id}"

    /** Convierte una clave ("alegre", "fam:<id>", "") en la dirección de la música, o null si no hay. */
    fun resolve(context: Context, key: String?, songs: List<FamilySong>): Uri? {
        if (key.isNullOrBlank()) return null
        if (key.startsWith("fam:")) {
            val id = key.removePrefix("fam:")
            return songs.firstOrNull { it.id == id }?.let { Uri.parse(it.url) }
        }
        return byKey(key)?.let { uri(context, it) }
    }
}
