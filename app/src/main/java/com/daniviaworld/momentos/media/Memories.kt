package com.daniviaworld.momentos.media

import android.content.Context
import com.daniviaworld.momentos.data.Photo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Recuerdos automáticos, al estilo de Google Fotos: la app busca sola "momentos especiales"
 * entre las fotos de la familia y prepara un vídeo con música. Se guardan en este aparato.
 */
object Memories {

    private val spanish = Locale("es", "ES")

    /** Un momento especial encontrado (todavía sin vídeo). */
    class Moment(
        /** Identifica el momento, para no repetirlo. */
        val key: String,
        val title: String,
        val subtitle: String,
        val photos: List<Photo>,
        /** Pista de MusicLibrary. */
        val music: String,
    )

    /** Un recuerdo ya preparado (vídeo en el aparato). */
    data class Memory(
        val key: String,
        val title: String,
        val subtitle: String,
        val videoPath: String,
        val coverUrl: String,
        val createdAt: Long,
        val seen: Boolean,
    )

    // ---------- Buscar momentos ----------

    private fun score(p: Photo) = p.likes.size * 2 + p.reactions.size * 2 + p.commentCount + 1

    /**
     * Sólo fotos "decentes": con algún me gusta o reacción, con buena resolución
     * y que no parezcan capturas de pantalla (muy alargadas).
     */
    private fun decent(p: Photo): Boolean {
        if (p.isVideo) return false
        if (p.likes.isEmpty() && p.reactions.isEmpty()) return false
        if (p.width > 0 && p.height > 0) {
            if (minOf(p.width, p.height) < 600) return false
            val r = p.width.toFloat() / p.height
            if (r < 0.5f || r > 2.1f) return false
        }
        return true
    }

    /** Quita ráfagas: de varias fotos de la misma persona con segundos de diferencia, deja la mejor. */
    private fun noBursts(list: List<Photo>): List<Photo> {
        val out = mutableListOf<Photo>()
        for (p in list.sortedBy { it.createdAt }) {
            val last = out.lastOrNull()
            if (last != null && last.uploaderUid == p.uploaderUid && p.createdAt - last.createdAt < 8_000) {
                if (score(p) > score(last)) out[out.size - 1] = p
            } else out += p
        }
        return out
    }

    private fun cal(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }

    /** Momentos especiales, del más interesante al menos. Sólo fotos (no vídeos). */
    fun findMoments(all: List<Photo>, now: Long = System.currentTimeMillis()): List<Moment> {
        val photos = noBursts(all.filter { decent(it) })
        if (photos.size < 3) return emptyList()
        val out = mutableListOf<Moment>()
        val today = cal(now)
        val year = today.get(Calendar.YEAR)

        // 1. Tal día como hoy (±3 días, años anteriores)
        val doy = today.get(Calendar.DAY_OF_YEAR)
        val past = photos.filter {
            val c = cal(it.createdAt)
            c.get(Calendar.YEAR) < year && kotlin.math.abs(c.get(Calendar.DAY_OF_YEAR) - doy) <= 3
        }
        if (past.size >= 3) {
            val years = past.map { cal(it.createdAt).get(Calendar.YEAR) }.distinct().sorted()
            val ago = year - years.last()
            out += Moment(
                key = "taldia_${year}_$doy",
                title = "Tal día como hoy",
                subtitle = if (ago == 1) "Hace un año" else "Hace $ago años",
                photos = past.sortedBy { it.createdAt },
                music = "emotiva",
            )
        }

        // 2. Lo mejor del mes pasado (a partir del día 1 del mes siguiente)
        val lastMonth = cal(now).apply { add(Calendar.MONTH, -1) }
        val lm = lastMonth.get(Calendar.MONTH)
        val ly = lastMonth.get(Calendar.YEAR)
        val monthPhotos = photos.filter { val c = cal(it.createdAt); c.get(Calendar.MONTH) == lm && c.get(Calendar.YEAR) == ly }
        if (monthPhotos.size >= 5) {
            val name = SimpleDateFormat("MMMM", spanish).format(lastMonth.time)
            out += Moment(
                key = "mes_${ly}_$lm",
                title = "Lo mejor de $name",
                subtitle = "Las ${minOf(monthPhotos.size, 20)} fotos más queridas del mes",
                photos = monthPhotos.sortedByDescending { score(it) }.take(20).sortedBy { it.createdAt },
                music = if (lm == Calendar.DECEMBER) "navidad" else "alegre",
            )
        }

        // 3. Un día especial: un día de las últimas 3 semanas con muchas fotos
        val recent = photos.filter { now - it.createdAt in 0..(21L * 24 * 3600 * 1000) }
        val byDay = recent.groupBy { val c = cal(it.createdAt); c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR) }
        byDay.entries.filter { it.value.size >= 5 }.sortedByDescending { it.value.size }.firstOrNull()?.let { (dayKey, list) ->
            val first = list.minByOrNull { it.createdAt }!!
            val dayName = SimpleDateFormat("EEEE d 'de' MMMM", spanish).format(first.createdAt)
            val who = list.map { it.uploaderName }.distinct()
            out += Moment(
                key = "dia_$dayKey",
                title = "Un día para recordar",
                subtitle = dayName.replaceFirstChar { it.uppercase() } + if (who.size > 1) " · ${who.size} fotógrafos" else "",
                photos = list.sortedBy { it.createdAt },
                music = "alegre",
            )
        }

        // 4. Los favoritos de la familia: las fotos más queridas de siempre (si hay bastantes con cariño)
        val loved = photos
        if (loved.size >= 8) {
            out += Moment(
                key = "favoritas_${year}_${today.get(Calendar.WEEK_OF_YEAR) / 8}", // como mucho cada 2 meses
                title = "Las favoritas de la familia",
                subtitle = "Las fotos que más os han gustado",
                photos = loved.sortedByDescending { score(it) }.take(15),
                music = "hogar",
            )
        }
        return out
    }

    // ---------- Recuerdos guardados ----------

    fun dir(context: Context): File = File(context.filesDir, "recuerdos").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "recuerdos.json")

    @Synchronized
    fun list(context: Context): List<Memory> = try {
        val f = index(context)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Memory(
                    key = o.getString("key"),
                    title = o.optString("title"),
                    subtitle = o.optString("subtitle"),
                    videoPath = o.optString("video"),
                    coverUrl = o.optString("cover"),
                    createdAt = o.optLong("createdAt"),
                    seen = o.optBoolean("seen"),
                )
            }.filter { File(it.videoPath).exists() }.sortedByDescending { it.createdAt }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Claves ya preparadas (o descartadas), para no repetir. */
    fun doneKeys(context: Context): Set<String> =
        context.getSharedPreferences("recuerdos", Context.MODE_PRIVATE).getStringSet("done", emptySet()) ?: emptySet()

    private fun markDone(context: Context, key: String) {
        val sp = context.getSharedPreferences("recuerdos", Context.MODE_PRIVATE)
        val set = (sp.getStringSet("done", emptySet()) ?: emptySet()) + key
        sp.edit().putStringSet("done", set).apply()
    }

    @Synchronized
    private fun save(context: Context, list: List<Memory>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject().put("key", m.key).put("title", m.title).put("subtitle", m.subtitle)
                    .put("video", m.videoPath).put("cover", m.coverUrl).put("createdAt", m.createdAt).put("seen", m.seen)
            )
        }
        index(context).writeText(arr.toString())
    }

    fun add(context: Context, moment: Moment, video: File) {
        val m = Memory(
            key = moment.key,
            title = moment.title,
            subtitle = moment.subtitle,
            videoPath = video.absolutePath,
            coverUrl = moment.photos.maxByOrNull { score(it) }?.thumbUrl ?: moment.photos.first().thumbUrl,
            createdAt = System.currentTimeMillis(),
            seen = false,
        )
        save(context, listOf(m) + list(context).filter { it.key != m.key })
        markDone(context, moment.key)
    }

    fun markSeen(context: Context, key: String) {
        save(context, list(context).map { if (it.key == key) it.copy(seen = true) else it })
    }

    /** Borra el recuerdo; ese momento puede volver a prepararse (con las fotos y reglas de ahora). */
    fun delete(context: Context, key: String) {
        val all = list(context)
        all.firstOrNull { it.key == key }?.let { File(it.videoPath).delete() }
        save(context, all.filter { it.key != key })
        val sp = context.getSharedPreferences("recuerdos", Context.MODE_PRIVATE)
        val set = (sp.getStringSet("done", emptySet()) ?: emptySet()) - key
        sp.edit().putStringSet("done", set).apply()
    }

    /** Sustituye el vídeo de un recuerdo (p. ej. con otra música). */
    fun replaceVideo(context: Context, key: String, newVideo: File) {
        val all = list(context)
        val m = all.firstOrNull { it.key == key } ?: return
        newVideo.copyTo(File(m.videoPath), overwrite = true)
        newVideo.delete()
    }
}
