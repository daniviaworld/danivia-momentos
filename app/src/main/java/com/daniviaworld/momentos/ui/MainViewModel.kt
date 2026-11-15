package com.daniviaworld.momentos.ui

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daniviaworld.momentos.data.Album
import com.daniviaworld.momentos.data.FamilySong
import com.daniviaworld.momentos.data.MusicLibrary
import com.daniviaworld.momentos.data.Comment
import com.daniviaworld.momentos.data.Family
import com.daniviaworld.momentos.data.Member
import com.daniviaworld.momentos.data.Photo
import com.daniviaworld.momentos.data.Prefs
import com.daniviaworld.momentos.data.Repo
import com.daniviaworld.momentos.data.ViewMode
import com.daniviaworld.momentos.isTelevision
import com.daniviaworld.momentos.share.Sharer
import com.daniviaworld.momentos.media.AlbumVideoMaker
import com.daniviaworld.momentos.media.CollageMaker
import com.daniviaworld.momentos.media.PhotoBookMaker
import com.daniviaworld.momentos.data.UploadManager
import com.daniviaworld.momentos.update.Updater
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AppState {
    data object Loading : AppState
    data class Error(val message: String) : AppState
    data object Onboarding : AppState
    data class Ready(val familyId: String) : AppState
}

/** Estado de lo que la app está creando (vídeo resumen, collage, libro). */
sealed interface Creation {
    data class Working(val text: String, val progress: Float?) : Creation
    data class Done(
        val file: java.io.File,
        val mime: String,
        val kind: String,
        val savedToDevice: Boolean,
        val albumId: String?,
    ) : Creation
}

sealed interface Screen {
    data object Home : Screen
    data object Upload : Screen
    data object FamilyInfo : Screen
    data object Albums : Screen
    data class Detail(val photoId: String) : Screen
}

const val ALL_ALBUMS = "__all__"
const val NO_ALBUM = "__none__"

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    val isTv = isTelevision(app)

    var appState by mutableStateOf<AppState>(AppState.Loading)
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
    var viewMode by mutableStateOf(
        ViewMode.fromKey(prefs.viewMode) ?: if (isTv) ViewMode.CINEMA else ViewMode.GRID
    )
        private set

    var busy by mutableStateOf(false)
        private set
    var notificationsAsked = false
    var message by mutableStateOf<String?>(null)

    /** Código de 6 cifras que enseña la TV mientras espera a ser vinculada. */
    var tvPairingCode by mutableStateOf<String?>(null)
        private set

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos
    private val _family = MutableStateFlow<Family?>(null)
    val family: StateFlow<Family?> = _family
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums
    private val _songs = MutableStateFlow<List<FamilySong>>(emptyList())
    val songs: StateFlow<List<FamilySong>> = _songs

    /** Filtro de álbum en la pantalla principal: ALL_ALBUMS, NO_ALBUM o el id de un álbum. */
    var albumFilter by mutableStateOf(ALL_ALBUMS)

    /** true si el álbum se abrió desde la pantalla Álbumes (para que "atrás" vuelva allí). */
    private var albumFromList = false

    fun openAlbum(albumIdOrNone: String) {
        albumFilter = albumIdOrNone
        albumFromList = true
        screen = Screen.Home
    }

    fun selectAlbumFilter(id: String) {
        albumFilter = id
        albumFromList = false
    }

    /** "Atrás" estando dentro de un álbum: vuelve a la lista de álbumes o a "Todas". */
    fun backFromAlbum() {
        albumFilter = ALL_ALBUMS
        if (albumFromList) {
            albumFromList = false
            screen = Screen.Albums
        }
    }

    /** Selección múltiple (admin) para mover fotos de álbum. */
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    val selecting: Boolean get() = selectedIds.isNotEmpty()

    private val listeners = mutableListOf<ListenerRegistration>()
    private var tokenSaved = false

    /** Guarda el token de avisos instantáneos de este móvil (si Firebase Messaging está disponible). */
    private fun registerPush(fid: String, code: String) {
        viewModelScope.launch {
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                Repo.saveFcmToken(fid, token, prefs.myName, "phone", code)
            } catch (_: Exception) {
                // sin avisos instantáneos: seguirá funcionando el aviso periódico
            }
        }
    }
    private var pairingListener: ListenerRegistration? = null

    val myUid: String? get() = Repo.uid

    // ---------- Actualizaciones ----------
    var availableUpdate by mutableStateOf<Updater.Release?>(null)
        private set
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    private var updateDismissed = false

    init {
        start()
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            val release = Updater.check()
            when {
                release != null -> {
                    if (manual || !updateDismissed) availableUpdate = release
                }
                manual -> message = if (Updater.isConfigured) {
                    "Ya tienes la última versión (${Updater.currentVersionName})."
                } else {
                    "Esta versión no tiene las actualizaciones automáticas configuradas."
                }
            }
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
        availableUpdate = null
    }

    fun startUpdate() {
        val release = availableUpdate ?: return
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            val opened = Updater.openInstallPermissionSettings(ctx)
            message = if (opened) {
                "Activa «Permitir de esta fuente» para DANIVIA Momentos, vuelve atrás y pulsa Actualizar otra vez."
            } else {
                "Activa en Ajustes → Seguridad la instalación de apps desconocidas para DANIVIA Momentos."
            }
            return
        }
        if (updateProgress != null) return
        updateProgress = 0f
        viewModelScope.launch {
            try {
                val apk = Updater.download(ctx, release) { updateProgress = it }
                updateProgress = null
                availableUpdate = null
                Updater.install(ctx, apk)
            } catch (e: Exception) {
                updateProgress = null
                message = "No se pudo descargar la actualización: ${e.message ?: "error de conexión"}"
            }
        }
    }

    fun start() {
        appState = AppState.Loading
        viewModelScope.launch {
            try {
                Repo.ensureSignedIn()
                val fid = prefs.familyId
                if (fid != null) enter(fid) else goToOnboarding()
            } catch (e: Exception) {
                appState = AppState.Error(
                    "No se pudo conectar. Revisa la conexión a internet.\n(${e.message ?: e.javaClass.simpleName})"
                )
            }
        }
    }

    private fun goToOnboarding() {
        appState = AppState.Onboarding
        if (isTv) startTvPairing()
    }

    private fun enter(fid: String) {
        prefs.familyId = fid
        stopListeners()
        listeners += Repo.listenPhotos(fid) { list ->
            _photos.value = list
            val newest = list.firstOrNull()?.createdAt ?: 0L
            if (newest > prefs.lastNotifiedMillis) prefs.lastNotifiedMillis = newest
        }
        listeners += Repo.listenFamily(fid) { fam ->
            _family.value = fam
            if (fam != null && fam.code.isNotBlank() && prefs.familyCode != fam.code) {
                prefs.familyCode = fam.code
            }
            if (fam != null && !isTv && !tokenSaved) {
                tokenSaved = true
                registerPush(fid, fam.code)
            }
        }
        listeners += Repo.listenMembers(fid) { _members.value = it }
        listeners += Repo.listenSongs(fid) { _songs.value = it }
        listeners += Repo.listenAlbums(fid) { list ->
            _albums.value = list
            // si borran el álbum que estaba filtrado, volvemos a "Todas"
            if (albumFilter != ALL_ALBUMS && albumFilter != NO_ALBUM && list.none { it.id == albumFilter }) {
                albumFilter = ALL_ALBUMS
            }
        }
        Repo.listenMyMembership(fid) { stillMember ->
            if (!stillMember) kicked()
        }?.let { listeners += it }
        if (prefs.lastNotifiedMillis == 0L) prefs.lastNotifiedMillis = System.currentTimeMillis()
        screen = Screen.Home
        appState = AppState.Ready(fid)
    }

    private fun kicked() {
        resetLocal()
        message = "Ya no formas parte de esta familia."
        goToOnboarding()
    }

    private fun resetLocal() {
        stopListeners()
        prefs.familyId = null
        _photos.value = emptyList()
        _family.value = null
        _members.value = emptyList()
        _albums.value = emptyList()
        _songs.value = emptyList()
        tokenSaved = false
        albumFilter = ALL_ALBUMS
        selectedIds = emptySet()
        screen = Screen.Home
    }

    private fun stopListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    // ---------- Alta ----------

    fun createFamily(myName: String, familyName: String) = runBusy {
        prefs.myName = myName.trim()
        val fid = Repo.createFamily(familyName.trim(), myName.trim(), "phone")
        enter(fid)
    }

    fun joinFamily(myName: String, code: String) = runBusy {
        val name = if (isTv) tvName() else myName.trim()
        prefs.myName = name
        val fid = Repo.joinFamily(code, name, if (isTv) "tv" else "phone")
        stopTvPairing()
        enter(fid)
    }

    private fun tvName(): String = "TV ${Build.MODEL ?: ""}".trim()

    private fun startTvPairing() {
        if (tvPairingCode != null) return
        viewModelScope.launch {
            try {
                val code = Repo.createPairing()
                tvPairingCode = code
                pairingListener = Repo.listenPairing(code) { familyCode ->
                    viewModelScope.launch {
                        try {
                            prefs.myName = tvName()
                            val fid = Repo.joinFamily(familyCode, tvName(), "tv")
                            stopTvPairing()
                            enter(fid)
                        } catch (e: Exception) {
                            message = "No se pudo vincular: ${e.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                message = "No se pudo crear el código de la TV: ${e.message}"
            }
        }
    }

    private fun stopTvPairing() {
        pairingListener?.remove()
        pairingListener = null
        val code = tvPairingCode
        tvPairingCode = null
        if (code != null) viewModelScope.launch { Repo.deletePairing(code) }
    }

    // ---------- Familia ----------

    fun linkTv(pairCode: String, onDone: () -> Unit) {
        val fam = _family.value ?: return
        runBusy {
            Repo.linkTv(pairCode, fam)
            message = "¡TV vinculada! En unos segundos verá las fotos."
            onDone()
        }
    }

    fun removeMember(uid: String) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.removeMember(fid, uid)
    }

    fun makeAdmin(uid: String) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.makeAdmin(fid, uid)
    }

    fun leaveFamily() = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        stopListeners()
        try {
            Repo.leaveFamily(fid)
        } finally {
            resetLocal()
            goToOnboarding()
        }
    }

    fun isAdmin(): Boolean {
        val me = myUid ?: return false
        return _family.value?.admins?.contains(me) == true
    }

    fun canDelete(photo: Photo): Boolean = photo.uploaderUid == myUid || isAdmin()

    fun deletePhoto(photo: Photo) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.deletePhoto(fid, photo)
    }

    // ---------- Álbumes ----------

    fun filterPhotos(all: List<Photo>): List<Photo> = when (albumFilter) {
        ALL_ALBUMS -> all
        NO_ALBUM -> all.filter { it.albumId == null }
        else -> all.filter { it.albumId == albumFilter }
    }

    fun toggleSelected(photo: Photo) {
        if (!isAdmin()) return
        selectedIds = if (photo.id in selectedIds) selectedIds - photo.id else selectedIds + photo.id
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    /** Crea un álbum y, si se indica, mete en él esas fotos. */
    fun createAlbum(name: String, emoji: String, movePhotoIds: List<String> = emptyList(), music: String = "") = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        val id = Repo.createAlbum(fid, name, emoji, music)
        if (movePhotoIds.isNotEmpty()) {
            Repo.movePhotos(fid, movePhotoIds, id)
            selectedIds = emptySet()
        }
    }

    fun updateAlbum(album: Album, name: String, emoji: String, music: String) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.updateAlbum(fid, album.id, name, emoji, music)
    }

    fun deleteAlbum(album: Album) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        val ids = _photos.value.filter { it.albumId == album.id }.map { it.id }
        Repo.deleteAlbum(fid, album.id, ids)
    }

    /** Mueve fotos a un álbum (null = sin álbum). */
    fun movePhotos(photoIds: List<String>, albumId: String?) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.movePhotos(fid, photoIds, albumId)
        selectedIds = emptySet()
        val name = _albums.value.firstOrNull { it.id == albumId }?.title ?: "Sin álbum"
        message = if (photoIds.size == 1) "Movida a «$name»." else "${photoIds.size} movidas a «$name»."
    }

    /** Música para diapositivas/cubo: la del álbum que se está viendo, o la general. */
    fun musicKey(albums: List<Album>): String {
        val album = albums.firstOrNull { it.id == albumFilter }
        return album?.music?.takeIf { it.isNotBlank() } ?: prefs.backgroundMusic
    }

    /** Dirección de la música para una clave ("alegre", "fam:<id>"…), o null. */
    fun musicUri(key: String?): android.net.Uri? =
        MusicLibrary.resolve(getApplication<Application>(), key, _songs.value)

    fun uploadSong(uri: android.net.Uri) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.uploadSong(getApplication<Application>(), fid, uri)
        message = "🎵 Canción añadida a la música de la familia."
    }

    fun deleteSong(song: FamilySong) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.deleteSong(fid, song)
        if (prefs.backgroundMusic == MusicLibrary.familyKey(song)) setBackgroundMusicKey("")
    }

    var screensaverSource by mutableStateOf(prefs.screensaverSource)
        private set

    fun setScreensaverSourceKey(key: String) {
        prefs.screensaverSource = key
        screensaverSource = key
    }

    var backgroundMusic by mutableStateOf(prefs.backgroundMusic)
        private set

    fun setBackgroundMusicKey(key: String) {
        prefs.backgroundMusic = key
        backgroundMusic = key
    }

    fun albumOf(photo: Photo): Album? = photo.albumId?.let { id -> _albums.value.firstOrNull { it.id == id } }

    // ---------- Creaciones: vídeo resumen, collage y libro ----------

    var creation by mutableStateOf<Creation?>(null)
        private set
    private var creationJob: kotlinx.coroutines.Job? = null

    private fun albumPhotos(album: Album?): List<Photo> =
        if (album == null) _photos.value.filter { it.albumId == null }
        else _photos.value.filter { it.albumId == album.id }

    fun makeVideo(album: Album?, musicUri: android.net.Uri?, secondsPerPhoto: Int) {
        val title = album?.title ?: (_family.value?.name ?: "Momentos")
        runCreation {
            creation = Creation.Working("Preparando…", 0f)
            val file = AlbumVideoMaker.make(
                getApplication<Application>(),
                albumPhotos(album),
                AlbumVideoMaker.Options(title = title, musicUri = musicUri, secondsPerPhoto = secondsPerPhoto),
            ) { text, p -> creation = Creation.Working(text, p) }
            val saved = Sharer.saveToDevice(getApplication<Application>(), file, "video/mp4", "video")
            Creation.Done(file, "video/mp4", "video", saved, album?.id)
        }
    }

    fun makeCollage(album: Album?) {
        val title = album?.title ?: (_family.value?.name ?: "Momentos")
        runCreation {
            creation = Creation.Working("Montando el collage…", null)
            val file = CollageMaker.make(getApplication<Application>(), albumPhotos(album), title)
            val saved = Sharer.saveToDevice(getApplication<Application>(), file, "image/jpeg", "image")
            Creation.Done(file, "image/jpeg", "collage", saved, album?.id)
        }
    }

    fun makeBook(album: Album?) {
        val title = album?.title ?: (_family.value?.name ?: "Momentos")
        runCreation {
            creation = Creation.Working("Creando el libro de fotos…", 0f)
            val file = PhotoBookMaker.make(getApplication<Application>(), albumPhotos(album), title) { p ->
                creation = Creation.Working("Creando el libro de fotos…", p)
            }
            val saved = Sharer.saveToDevice(getApplication<Application>(), file, "application/pdf", "download")
            Creation.Done(file, "application/pdf", "book", saved, album?.id)
        }
    }

    private fun runCreation(block: suspend () -> Creation.Done) {
        if (creation is Creation.Working) return
        creationJob = viewModelScope.launch {
            try {
                creation = block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                creation = null
            } catch (e: IllegalArgumentException) {
                creation = null
                message = e.message
            } catch (e: Exception) {
                creation = null
                message = "No se pudo crear: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun cancelCreation() {
        creationJob?.cancel()
        creation = null
    }

    fun closeCreation() {
        creation = null
    }

    fun shareCreation() {
        val done = creation as? Creation.Done ?: return
        val title = when (done.kind) {
            "video" -> "Compartir vídeo"
            "collage" -> "Compartir collage"
            else -> "Compartir libro"
        }
        Sharer.shareFile(getApplication<Application>(), done.file, done.mime, title)
    }

    /** Sube el vídeo o el collage creado al álbum, para verlo en la tele. */
    fun addCreationToAlbum() {
        val done = creation as? Creation.Done ?: return
        val fid = prefs.familyId ?: return
        UploadManager.start(
            getApplication<Application>(),
            fid,
            listOf(android.net.Uri.fromFile(done.file)),
            enhance = false,
            myName = prefs.myName.ifBlank { "Alguien" },
            albumId = done.albumId,
        )
        creation = null
        screen = Screen.Home
        message = "Subiendo al álbum… lo verás en unos momentos (también en la tele)."
    }

    // ---------- Compartir ----------

    /** id de la foto que se está preparando para compartir (para enseñar el "cargando"). */
    var sharingId by mutableStateOf<String?>(null)
        private set

    fun share(photo: Photo) {
        if (sharingId != null) return
        sharingId = photo.id
        viewModelScope.launch {
            try {
                Sharer.share(getApplication<Application>(), photo)
            } catch (e: Exception) {
                message = "No se pudo preparar para compartir: ${e.message ?: "revisa la conexión"}"
            } finally {
                sharingId = null
            }
        }
    }

    // ---------- Me gusta y comentarios ----------

    // ---------- Notas de voz ----------

    fun canEditVoice(photo: Photo): Boolean = photo.uploaderUid == myUid || isAdmin()

    fun uploadVoice(photo: Photo, file: java.io.File) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        try {
            Repo.uploadVoice(fid, photo, file, prefs.myName.ifBlank { "Alguien" })
        } finally {
            file.delete()
        }
    }

    fun deleteVoice(photo: Photo) = runBusy {
        val fid = prefs.familyId ?: return@runBusy
        Repo.deleteVoice(fid, photo)
    }

    fun myReaction(photo: Photo): String? = myUid?.let { photo.reactions[it] }

    fun setReaction(photo: Photo, emoji: String?) {
        val fid = prefs.familyId ?: return
        viewModelScope.launch {
            try {
                Repo.setReaction(fid, photo, emoji, prefs.myName.ifBlank { "Alguien" })
            } catch (e: Exception) {
                message = "No se pudo guardar la reacción: ${e.message}"
            }
        }
    }

    fun isLikedByMe(photo: Photo): Boolean = myUid?.let { it in photo.likes } == true

    fun toggleLike(photo: Photo) {
        val fid = prefs.familyId ?: return
        val like = !isLikedByMe(photo)
        viewModelScope.launch {
            try {
                Repo.setLike(fid, photo, like, prefs.myName.ifBlank { "Alguien" })
            } catch (e: Exception) {
                message = "No se pudo guardar el me gusta: ${e.message}"
            }
        }
    }

    fun listenComments(photoId: String, onChange: (List<Comment>) -> Unit): ListenerRegistration? {
        val fid = prefs.familyId ?: return null
        return Repo.listenComments(fid, photoId, onChange)
    }

    fun addComment(photo: Photo, text: String) {
        val fid = prefs.familyId ?: return
        viewModelScope.launch {
            try {
                Repo.addComment(fid, photo, text, prefs.myName.ifBlank { "Alguien" })
            } catch (e: Exception) {
                message = "No se pudo enviar el comentario: ${e.message}"
            }
        }
    }

    fun canDeleteComment(photo: Photo, comment: Comment): Boolean =
        comment.uid == myUid || photo.uploaderUid == myUid || isAdmin()

    fun deleteComment(photo: Photo, comment: Comment) {
        val fid = prefs.familyId ?: return
        viewModelScope.launch {
            try {
                Repo.deleteComment(fid, photo.id, comment)
            } catch (e: Exception) {
                message = "No se pudo borrar: ${e.message}"
            }
        }
    }

    /** Nombres de quien ha dado me gusta, para "Les gusta a …". */
    fun likerNames(photo: Photo): List<String> {
        val names = _members.value.associate { it.uid to it.name }
        return photo.likes.map { if (it == myUid) "ti" else names[it] ?: "alguien" }
    }

    fun selectViewMode(mode: ViewMode) {
        viewMode = mode
        prefs.viewMode = mode.key
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: IllegalArgumentException) {
                message = e.message
            } catch (e: Exception) {
                message = "Algo ha fallado: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        stopListeners()
        pairingListener?.remove()
        super.onCleared()
    }
}
