package com.daniviaworld.momentos.data

import android.content.Context
import android.net.Uri
import com.daniviaworld.momentos.image.ImageProcessor
import com.daniviaworld.momentos.image.VideoProcessor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * Todo el acceso a Firebase.
 *
 * Estructura:
 *  codes/{CODIGO}                      -> { familyId }
 *  families/{fid}                      -> { name, code, admins[], createdAt }
 *  families/{fid}/members/{uid}        -> { name, device, code, joinedAt }
 *  families/{fid}/photos/{pid}         -> { url, thumbUrl, storagePath, thumbPath, uploaderUid, uploaderName, createdAt, width, height, enhanced }
 *  pairings/{123456}                   -> { tvUid, createdAt, familyId?, familyCode? }
 *  Storage: families/{fid}/photos/{pid}.jpg y {pid}_t.jpg
 */
object Repo {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    private val random = SecureRandom()

    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    val uid: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("No se pudo iniciar sesión")
    }

    private fun randomCode(length: Int, alphabet: String): String =
        buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }

    private fun families() = db.collection("families")

    // ---------- Familia ----------

    suspend fun createFamily(familyName: String, myName: String, device: String): String {
        val me = ensureSignedIn()
        repeat(6) {
            val code = randomCode(6, CODE_ALPHABET)
            val codeRef = db.collection("codes").document(code)
            if (codeRef.get().await().exists()) return@repeat
            val famRef = families().document()
            val batch = db.batch()
            batch.set(
                famRef, mapOf(
                    "name" to familyName,
                    "code" to code,
                    "admins" to listOf(me),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.set(codeRef, mapOf("familyId" to famRef.id))
            batch.set(
                famRef.collection("members").document(me), mapOf(
                    "name" to myName,
                    "device" to device,
                    "code" to code,
                    "joinedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            return famRef.id
        }
        error("No se pudo crear el código de familia, inténtalo otra vez")
    }

    /** Devuelve el id de la familia o lanza IllegalArgumentException si el código no existe. */
    suspend fun joinFamily(rawCode: String, myName: String, device: String): String {
        val me = ensureSignedIn()
        val code = rawCode.trim().uppercase().replace(" ", "")
        if (code.length != 6) throw IllegalArgumentException("El código tiene 6 letras o números")
        val snap = db.collection("codes").document(code).get().await()
        val fid = snap.getString("familyId") ?: throw IllegalArgumentException("Ese código no existe")
        families().document(fid).collection("members").document(me).set(
            mapOf(
                "name" to myName,
                "device" to device,
                "code" to code,
                "joinedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        return fid
    }

    suspend fun removeMember(fid: String, memberUid: String) {
        families().document(fid).collection("members").document(memberUid).delete().await()
    }

    suspend fun makeAdmin(fid: String, memberUid: String) {
        families().document(fid).update("admins", FieldValue.arrayUnion(memberUid)).await()
    }

    suspend fun leaveFamily(fid: String) {
        val me = uid ?: return
        try {
            families().document(fid).update("admins", FieldValue.arrayRemove(me)).await()
        } catch (_: Exception) {
            // si no era admin, no pasa nada
        }
        families().document(fid).collection("members").document(me).delete().await()
    }

    fun listenFamily(fid: String, onChange: (Family?) -> Unit): ListenerRegistration =
        families().document(fid).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                onChange(null); return@addSnapshotListener
            }
            onChange(
                Family(
                    id = snap.id,
                    name = snap.getString("name") ?: "Mi familia",
                    code = snap.getString("code") ?: "",
                    admins = (snap.get("admins") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                )
            )
        }

    fun listenMembers(fid: String, onChange: (List<Member>) -> Unit): ListenerRegistration =
        families().document(fid).collection("members").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            onChange(snap.documents.map {
                Member(
                    uid = it.id,
                    name = it.getString("name") ?: "Sin nombre",
                    device = it.getString("device") ?: "phone",
                    joinedAt = it.getTimestamp("joinedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDate()?.time ?: 0L,
                )
            }.sortedBy { it.joinedAt })
        }

    /** Avisa con false si me han quitado de la familia. Ignora datos de caché para no dar falsos avisos. */
    fun listenMyMembership(fid: String, onChange: (Boolean) -> Unit): ListenerRegistration? {
        val me = uid ?: return null
        return families().document(fid).collection("members").document(me).addSnapshotListener { snap, _ ->
            if (snap == null || snap.metadata.isFromCache) return@addSnapshotListener
            onChange(snap.exists())
        }
    }

    // ---------- Fotos ----------

    fun listenPhotos(fid: String, onChange: (List<Photo>) -> Unit): ListenerRegistration =
        families().document(fid).collection("photos")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(3000)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                onChange(snap.documents.mapNotNull { it.toPhoto() })
            }

    /** Todas las fotos de una vez (para trabajos en segundo plano, como los recuerdos). */
    suspend fun fetchPhotos(fid: String): List<Photo> =
        families().document(fid).collection("photos")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(3000)
            .get().await()
            .documents.mapNotNull { it.toPhoto() }

    fun DocumentSnapshot.toPhoto(): Photo? {
        val url = getString("url") ?: return null
        val created = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return Photo(
            id = id,
            url = url,
            thumbUrl = getString("thumbUrl") ?: url,
            storagePath = getString("storagePath") ?: "",
            thumbPath = getString("thumbPath") ?: "",
            uploaderUid = getString("uploaderUid") ?: "",
            uploaderName = getString("uploaderName") ?: "Alguien",
            createdAt = created?.toDate()?.time ?: System.currentTimeMillis(),
            width = (getLong("width") ?: 0L).toInt(),
            height = (getLong("height") ?: 0L).toInt(),
            enhanced = getBoolean("enhanced") ?: false,
            isVideo = getString("type") == "video",
            videoUrl = getString("videoUrl") ?: "",
            videoPath = getString("videoPath") ?: "",
            durationMs = getLong("durationMs") ?: 0L,
            likes = (get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            commentCount = (getLong("commentCount") ?: 0L).toInt().coerceAtLeast(0),
            albumId = getString("albumId")?.takeIf { it.isNotBlank() },
            reactions = (get("reactions") as? Map<*, *>)
                ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                ?.toMap() ?: emptyMap(),
            voiceUrl = getString("voiceUrl") ?: "",
            voicePath = getString("voicePath") ?: "",
            voiceBy = getString("voiceBy") ?: "",
            originalUrl = getString("originalUrl") ?: "",
            originalPath = getString("originalPath") ?: "",
            originalThumbPath = getString("originalThumbPath") ?: "",
            editFrame = getString("editFrame") ?: "",
            editStickers = getString("editStickers") ?: "",
        )
    }

    // ---------- Biblioteca musical de la familia ----------

    fun listenSongs(fid: String, onChange: (List<FamilySong>) -> Unit): ListenerRegistration =
        families().document(fid).collection("music")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                onChange(snap.documents.mapNotNull {
                    val url = it.getString("url") ?: return@mapNotNull null
                    FamilySong(it.id, it.getString("name") ?: "Canción", url, it.getString("path") ?: "")
                })
            }

    /** Sube una canción del móvil a la biblioteca de la familia (máx. 20 MB). */
    suspend fun uploadSong(context: Context, fid: String, uri: Uri) {
        val me = ensureSignedIn()
        val resolver = context.contentResolver
        var name = "Canción"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { c ->
            val n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (n >= 0) name = c.getString(n) ?: name
                if (s >= 0) size = c.getLong(s)
            }
        }
        if (size > 20L * 1024 * 1024) throw IllegalArgumentException("La canción ocupa más de 20 MB. Elige una más corta.")
        val mime = resolver.getType(uri) ?: "audio/mpeg"
        if (!mime.startsWith("audio/")) throw IllegalArgumentException("Ese archivo no es una canción.")
        val ext = name.substringAfterLast('.', "mp3").lowercase().take(4)
        val ref = families().document(fid).collection("music").document()
        val path = "families/$fid/music/${ref.id}.$ext"
        val meta = StorageMetadata.Builder().setContentType(mime).setCustomMetadata("uploaderUid", me).build()
        val sRef = storage.reference.child(path)
        sRef.putFile(uri, meta).await()
        val url = sRef.downloadUrl.await().toString()
        ref.set(
            mapOf(
                "name" to name.substringBeforeLast('.').take(60),
                "url" to url,
                "path" to path,
                "uploaderUid" to me,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun deleteSong(fid: String, song: FamilySong) {
        families().document(fid).collection("music").document(song.id).delete().await()
        if (song.path.isNotBlank()) {
            try {
                storage.reference.child(song.path).delete().await()
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Reacciones ----------

    /** Pone (o quita, con null) mi reacción con emoji. */
    suspend fun setReaction(fid: String, photo: Photo, emoji: String?, myName: String) {
        val me = ensureSignedIn()
        photoRef(fid, photo.id).update("reactions.$me", emoji ?: FieldValue.delete()).await()
        if (emoji != null && photo.uploaderUid != me) addActivity(fid, "reaction", photo, myName, emoji)
    }

    // ---------- Notas de voz ----------

    suspend fun uploadVoice(fid: String, photo: Photo, file: java.io.File, myName: String) {
        val me = ensureSignedIn()
        val path = "families/$fid/photos/${photo.id}_voz.m4a"
        val meta = StorageMetadata.Builder()
            .setContentType("audio/mp4")
            .setCustomMetadata("uploaderUid", me)
            .build()
        val ref = storage.reference.child(path)
        ref.putFile(Uri.fromFile(file), meta).await()
        val url = ref.downloadUrl.await().toString()
        photoRef(fid, photo.id).update(
            mapOf("voiceUrl" to url, "voicePath" to path, "voiceBy" to myName)
        ).await()
    }

    suspend fun deleteVoice(fid: String, photo: Photo) {
        photoRef(fid, photo.id).update(
            mapOf(
                "voiceUrl" to FieldValue.delete(),
                "voicePath" to FieldValue.delete(),
                "voiceBy" to FieldValue.delete(),
            )
        ).await()
        if (photo.voicePath.isNotBlank()) {
            try {
                storage.reference.child(photo.voicePath).delete().await()
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Avisos al instante ----------

    /** Guarda el token de notificaciones de este aparato en mi ficha de miembro. */
    suspend fun saveFcmToken(fid: String, token: String, myName: String, device: String, familyCode: String) {
        val me = ensureSignedIn()
        families().document(fid).collection("members").document(me).set(
            mapOf("fcmToken" to token, "name" to myName, "device" to device, "code" to familyCode),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    // ---------- Álbumes ----------

    private fun albums(fid: String) = families().document(fid).collection("albums")

    fun listenAlbums(fid: String, onChange: (List<Album>) -> Unit): ListenerRegistration =
        albums(fid).orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            onChange(snap.documents.map {
                Album(
                    id = it.id,
                    name = it.getString("name") ?: "Álbum",
                    emoji = it.getString("emoji") ?: "",
                    music = it.getString("music") ?: "",
                    createdAt = it.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDate()?.time ?: System.currentTimeMillis(),
                )
            })
        }

    suspend fun createAlbum(fid: String, name: String, emoji: String, music: String = ""): String {
        val ref = albums(fid).document()
        ref.set(
            mapOf(
                "name" to name.trim().take(40),
                "emoji" to emoji,
                "music" to music,
                "creatorUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        return ref.id
    }

    suspend fun updateAlbum(fid: String, albumId: String, name: String, emoji: String, music: String) {
        albums(fid).document(albumId)
            .update(mapOf("name" to name.trim().take(40), "emoji" to emoji, "music" to music)).await()
    }

    /** Borra el álbum; sus fotos pasan a "Sin álbum" (no se borra ninguna foto). */
    suspend fun deleteAlbum(fid: String, albumId: String, photoIds: List<String>) {
        movePhotos(fid, photoIds, null)
        albums(fid).document(albumId).delete().await()
    }

    /** Cambia de álbum varias fotos a la vez (null = sin álbum). */
    suspend fun movePhotos(fid: String, photoIds: List<String>, albumId: String?) {
        photoIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { pid -> batch.update(photoRef(fid, pid), "albumId", albumId ?: FieldValue.delete()) }
            batch.commit().await()
        }
    }

    // ---------- Me gusta y comentarios ----------

    private fun photoRef(fid: String, pid: String) = families().document(fid).collection("photos").document(pid)

    suspend fun setLike(fid: String, photo: Photo, like: Boolean, myName: String) {
        val me = ensureSignedIn()
        photoRef(fid, photo.id).update(
            "likes", if (like) FieldValue.arrayUnion(me) else FieldValue.arrayRemove(me)
        ).await()
        if (like && photo.uploaderUid != me) addActivity(fid, "like", photo, myName, null)
    }

    fun listenComments(fid: String, pid: String, onChange: (List<Comment>) -> Unit): ListenerRegistration =
        photoRef(fid, pid).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(300)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                onChange(snap.documents.map {
                    Comment(
                        id = it.id,
                        uid = it.getString("uid") ?: "",
                        name = it.getString("name") ?: "Alguien",
                        text = it.getString("text") ?: "",
                        createdAt = it.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.toDate()?.time ?: System.currentTimeMillis(),
                    )
                })
            }

    suspend fun addComment(fid: String, photo: Photo, text: String, myName: String) {
        val me = ensureSignedIn()
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return
        val ref = photoRef(fid, photo.id)
        val batch = db.batch()
        batch.set(
            ref.collection("comments").document(), mapOf(
                "uid" to me,
                "name" to myName,
                "text" to clean,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(ref, "commentCount", FieldValue.increment(1))
        batch.commit().await()
        if (photo.uploaderUid != me) addActivity(fid, "comment", photo, myName, clean.take(100))
    }

    suspend fun deleteComment(fid: String, photoId: String, comment: Comment) {
        val ref = photoRef(fid, photoId)
        val batch = db.batch()
        batch.delete(ref.collection("comments").document(comment.id))
        batch.update(ref, "commentCount", FieldValue.increment(-1))
        batch.commit().await()
    }

    /** Aviso para quien subió la foto ("A Mamá le ha gustado tu foto"). Lo lee el aviso periódico. */
    private suspend fun addActivity(fid: String, type: String, photo: Photo, myName: String, text: String?) {
        try {
            families().document(fid).collection("activity").add(
                mapOf(
                    "type" to type,
                    "toUid" to photo.uploaderUid,
                    "fromUid" to (uid ?: ""),
                    "fromName" to myName,
                    "photoId" to photo.id,
                    "isVideo" to photo.isVideo,
                    "text" to (text ?: ""),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (_: Exception) {
            // el aviso es secundario: si falla, el me gusta/comentario ya está guardado
        }
    }

    private fun albumField(albumId: String?): Map<String, Any> =
        if (albumId.isNullOrBlank()) emptyMap() else mapOf("albumId" to albumId)

    fun isVideo(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("video/") == true ||
            (uri.scheme == "file" && uri.path?.endsWith(".mp4", ignoreCase = true) == true)

    /** Sube una foto o un vídeo (según lo que sea). */
    suspend fun uploadMedia(
        context: Context, fid: String, uri: Uri, enhance: Boolean, myName: String, albumId: String?, frame: String = "",
    ) {
        if (isVideo(context, uri)) uploadVideo(context, fid, uri, enhance, myName, albumId)
        else uploadPhoto(context, fid, uri, enhance, myName, albumId, frame)
    }

    private suspend fun uploadVideo(context: Context, fid: String, uri: Uri, enhance: Boolean, myName: String, albumId: String?) {
        val me = ensureSignedIn()
        val prepared = VideoProcessor.prepare(context, uri, enhance)
        try {
            val docRef = families().document(fid).collection("photos").document()
            val base = "families/$fid/photos/${docRef.id}"
            val posterMeta = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("uploaderUid", me)
                .build()
            val videoMeta = StorageMetadata.Builder()
                .setContentType("video/mp4")
                .setCustomMetadata("uploaderUid", me)
                .build()

            val posterRef = storage.reference.child("$base.jpg")
            posterRef.putBytes(prepared.poster, posterMeta).await()
            val posterUrl = posterRef.downloadUrl.await().toString()

            val thumbRef = storage.reference.child("${base}_t.jpg")
            thumbRef.putBytes(prepared.thumb, posterMeta).await()
            val thumbUrl = thumbRef.downloadUrl.await().toString()

            val videoRef = storage.reference.child("$base.mp4")
            videoRef.putFile(Uri.fromFile(prepared.file), videoMeta).await()
            val videoUrl = videoRef.downloadUrl.await().toString()

            docRef.set(
                mapOf(
                    "type" to "video",
                    "url" to posterUrl,
                    "thumbUrl" to thumbUrl,
                    "videoUrl" to videoUrl,
                    "storagePath" to "$base.jpg",
                    "thumbPath" to "${base}_t.jpg",
                    "videoPath" to "$base.mp4",
                    "uploaderUid" to me,
                    "uploaderName" to myName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "width" to prepared.width,
                    "height" to prepared.height,
                    "durationMs" to prepared.durationMs,
                    "enhanced" to prepared.enhanced,
                ) + albumField(albumId)
            ).await()
        } finally {
            if (prepared.isTemp) prepared.file.delete()
        }
    }

    private suspend fun uploadPhoto(context: Context, fid: String, uri: Uri, enhance: Boolean, myName: String, albumId: String?, frame: String) {
        val me = ensureSignedIn()
        val processed = withContext(Dispatchers.Default) {
            ImageProcessor.process(context, uri, enhance, frame)
        }
        val docRef = families().document(fid).collection("photos").document()
        val path = "families/$fid/photos/${docRef.id}.jpg"
        val thumbPath = "families/$fid/photos/${docRef.id}_t.jpg"
        val meta = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("uploaderUid", me)
            .build()

        val fullRef = storage.reference.child(path)
        fullRef.putBytes(processed.full, meta).await()
        val url = fullRef.downloadUrl.await().toString()

        val thumbRef = storage.reference.child(thumbPath)
        thumbRef.putBytes(processed.thumb, meta).await()
        val thumbUrl = thumbRef.downloadUrl.await().toString()

        docRef.set(
            mapOf(
                "type" to "photo",
                "url" to url,
                "thumbUrl" to thumbUrl,
                "storagePath" to path,
                "thumbPath" to thumbPath,
                "uploaderUid" to me,
                "uploaderName" to myName,
                "createdAt" to FieldValue.serverTimestamp(),
                "width" to processed.width,
                "height" to processed.height,
                "enhanced" to enhance,
            ) + albumField(albumId)
        ).await()
    }

    // ---------- Editar lo ya subido ----------

    private suspend fun deleteFiles(vararg paths: String) {
        for (p in paths) {
            if (p.isBlank()) continue
            try {
                storage.reference.child(p).delete().await()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Sustituye la imagen de una foto ya subida por la versión editada. Me gusta, comentarios,
     * álbum… se conservan. La primera vez se guarda la original para poder volver a ella.
     */
    suspend fun replacePhotoImage(fid: String, photo: Photo, result: ImageProcessor.Result, frame: String, stickersJson: String) {
        val me = ensureSignedIn()
        val stamp = System.currentTimeMillis()
        val path = "families/$fid/photos/${photo.id}_e$stamp.jpg"
        val thumbPath = "families/$fid/photos/${photo.id}_e${stamp}_t.jpg"
        val meta = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("uploaderUid", me)
            .build()
        val fullRef = storage.reference.child(path)
        fullRef.putBytes(result.full, meta).await()
        val url = fullRef.downloadUrl.await().toString()
        val thumbRef = storage.reference.child(thumbPath)
        thumbRef.putBytes(result.thumb, meta).await()
        val thumbUrl = thumbRef.downloadUrl.await().toString()

        val update = mutableMapOf<String, Any>(
            "url" to url,
            "thumbUrl" to thumbUrl,
            "storagePath" to path,
            "thumbPath" to thumbPath,
            "width" to result.width,
            "height" to result.height,
            "editedAt" to FieldValue.serverTimestamp(),
            "editFrame" to frame,
            "editStickers" to stickersJson,
        )
        if (!photo.isEdited) {
            update["originalUrl"] = photo.url
            update["originalPath"] = photo.storagePath
            update["originalThumbPath"] = photo.thumbPath
        }
        photoRef(fid, photo.id).update(update).await()
        // la edición anterior ya no hace falta (la original se guarda siempre)
        if (photo.isEdited) deleteFiles(photo.storagePath, photo.thumbPath)
    }

    /** Quita el marco y las pegatinas: vuelve a la foto tal como se subió. */
    suspend fun restoreOriginal(fid: String, photo: Photo) {
        if (!photo.isEdited) return
        val thumbUrl = if (photo.originalThumbPath.isNotBlank()) {
            try {
                storage.reference.child(photo.originalThumbPath).downloadUrl.await().toString()
            } catch (_: Exception) {
                photo.originalUrl
            }
        } else photo.originalUrl
        photoRef(fid, photo.id).update(
            mapOf(
                "url" to photo.originalUrl,
                "thumbUrl" to thumbUrl,
                "storagePath" to photo.originalPath,
                "thumbPath" to photo.originalThumbPath,
                "editedAt" to FieldValue.serverTimestamp(),
                "originalUrl" to FieldValue.delete(),
                "originalPath" to FieldValue.delete(),
                "originalThumbPath" to FieldValue.delete(),
                "editFrame" to FieldValue.delete(),
                "editStickers" to FieldValue.delete(),
            )
        ).await()
        deleteFiles(photo.storagePath, photo.thumbPath)
    }

    /** Sustituye el vídeo (p. ej. con la música cambiada). La portada se queda igual. */
    suspend fun replaceVideo(fid: String, photo: Photo, file: java.io.File) {
        val me = ensureSignedIn()
        val path = "families/$fid/photos/${photo.id}_v${System.currentTimeMillis()}.mp4"
        val meta = StorageMetadata.Builder()
            .setContentType("video/mp4")
            .setCustomMetadata("uploaderUid", me)
            .build()
        val ref = storage.reference.child(path)
        ref.putFile(Uri.fromFile(file), meta).await()
        val url = ref.downloadUrl.await().toString()
        photoRef(fid, photo.id).update(
            mapOf("videoUrl" to url, "videoPath" to path, "editedAt" to FieldValue.serverTimestamp())
        ).await()
        deleteFiles(photo.videoPath)
    }

    /** Cambia mi nombre en la familia y en las fotos que he subido. */
    suspend fun renameMe(fid: String, name: String) {
        val me = ensureSignedIn()
        val clean = name.trim().take(60)
        if (clean.isEmpty()) throw IllegalArgumentException("Escribe un nombre")
        families().document(fid).collection("members").document(me).update("name", clean).await()
        val mine = families().document(fid).collection("photos").whereEqualTo("uploaderUid", me).get().await()
        // en tandas de 400 (el límite de Firestore es 500 por lote)
        mine.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(it.reference, "uploaderName", clean) }
            batch.commit().await()
        }
    }

    suspend fun deletePhoto(fid: String, photo: Photo) {
        // Primero los comentarios, luego el documento (desaparece al momento para todos) y por último los archivos
        try {
            val comments = photoRef(fid, photo.id).collection("comments").get().await()
            for (c in comments.documents) c.reference.delete().await()
        } catch (_: Exception) {
        }
        families().document(fid).collection("photos").document(photo.id).delete().await()
        for (p in listOf(photo.storagePath, photo.thumbPath, photo.videoPath, photo.voicePath, photo.originalPath, photo.originalThumbPath).distinct()) {
            if (p.isBlank()) continue
            try {
                storage.reference.child(p).delete().await()
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Vincular TV ----------

    /** La TV crea un código de 6 cifras y espera a que un móvil lo rellene. */
    suspend fun createPairing(): String {
        val me = ensureSignedIn()
        repeat(6) {
            val code = randomCode(6, "0123456789")
            try {
                db.collection("pairings").document(code).set(
                    mapOf("tvUid" to me, "createdAt" to FieldValue.serverTimestamp())
                ).await()
                return code
            } catch (_: Exception) {
                // código ocupado por otra TV: probamos otro
            }
        }
        error("No se pudo crear el código de vinculación")
    }

    fun listenPairing(code: String, onFamilyCode: (String) -> Unit): ListenerRegistration =
        db.collection("pairings").document(code).addSnapshotListener { snap, _ ->
            val familyCode = snap?.getString("familyCode")
            if (!familyCode.isNullOrBlank()) onFamilyCode(familyCode)
        }

    suspend fun deletePairing(code: String) {
        try {
            db.collection("pairings").document(code).delete().await()
        } catch (_: Exception) {
        }
    }

    /** El móvil rellena el código que muestra la TV. */
    suspend fun linkTv(pairCode: String, family: Family) {
        val code = pairCode.filter { it.isDigit() }
        if (code.length != 6) throw IllegalArgumentException("El código de la TV tiene 6 números")
        try {
            db.collection("pairings").document(code).update(
                mapOf("familyId" to family.id, "familyCode" to family.code)
            ).await()
        } catch (e: Exception) {
            throw IllegalArgumentException("Código incorrecto. Revisa el que aparece en la TV.")
        }
    }
}
