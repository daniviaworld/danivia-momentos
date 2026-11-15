package com.daniviaworld.momentos.data

data class Photo(
    val id: String,
    val url: String,
    val thumbUrl: String,
    val storagePath: String,
    val thumbPath: String,
    val uploaderUid: String,
    val uploaderName: String,
    val createdAt: Long,
    val width: Int,
    val height: Int,
    val enhanced: Boolean,
    /** En los vídeos, [url] es el fotograma de portada y [videoUrl] el vídeo. */
    val isVideo: Boolean = false,
    val videoUrl: String = "",
    val videoPath: String = "",
    val durationMs: Long = 0L,
    /** uids de quien ha dado me gusta */
    val likes: List<String> = emptyList(),
    val commentCount: Int = 0,
    /** Álbum al que pertenece (null = sin álbum). */
    val albumId: String? = null,
    /** Reacciones con emoji: uid -> emoji (el ❤️ va aparte, en [likes]). */
    val reactions: Map<String, String> = emptyMap(),
    /** Nota de voz (opcional). */
    val voiceUrl: String = "",
    val voicePath: String = "",
    val voiceBy: String = "",
)

data class Album(
    val id: String,
    val name: String,
    val emoji: String,
    val createdAt: Long,
    /** Clave de la música del álbum (ver MusicLibrary); vacío = ninguna. */
    val music: String = "",
) {
    val title: String get() = if (emoji.isBlank()) name else "$emoji $name"
}

data class Comment(
    val id: String,
    val uid: String,
    val name: String,
    val text: String,
    val createdAt: Long,
)

data class Family(
    val id: String,
    val name: String,
    val code: String,
    val admins: List<String>,
)

data class Member(
    val uid: String,
    val name: String,
    val device: String,
    val joinedAt: Long,
)

enum class ViewMode(val key: String, val label: String, val icon: String) {
    GRID("grid", "Cuadrícula", "▦"),
    CAROUSEL("carousel", "Carrusel", "🎠"),
    CINEMA("cinema", "Estilo cine", "🎬"),
    CUBE("cube", "Cubo 3D", "🧊"),
    SLIDESHOW("slideshow", "Diapositivas", "🖼️");

    companion object {
        fun fromKey(key: String?): ViewMode? = entries.firstOrNull { it.key == key }
    }
}
