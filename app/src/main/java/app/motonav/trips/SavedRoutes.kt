package app.motonav.trips

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.motonav.geo.LatLon
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import app.motonav.i18n.tr

/** Μια διαδρομή που έκανες και την κράτησες με όνομα (π.χ. «Σπίτι μέσω Πάρνηθας»). Την ξανακάνεις με «Ακολούθησε». */
data class SavedRoute(val id: String, val name: String, val lengthM: Double, val createdMs: Long)

/** Αποθηκευμένες διαδρομές: ένα GPX η καθεμιά, στο κινητό (files/routes/), και μια λίστα με τα ονόματα. */
object SavedRoutes {
    var list by mutableStateOf<List<SavedRoute>>(emptyList())
        private set
    private var loaded = false

    private fun dir(c: Context) = File(c.filesDir, "routes").apply { mkdirs() }
    private fun index(c: Context) = File(dir(c), "index.json")
    fun file(c: Context, id: String) = File(dir(c), "$id.gpx")

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        list = runCatching {
            val a = JSONArray(index(c).readText())
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { o -> SavedRoute(o.getString("id"), o.optString("name"), o.optDouble("len"), o.optLong("at")) }
            }.filter { file(c, it.id).exists() }
        }.getOrDefault(emptyList())
    }

    private fun write(c: Context) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("len", it.lengthM).put("at", it.createdMs)) }
        runCatching { index(c).writeText(a.toString()) }
    }

    /** Αντιγράφει το GPX μιας καταγραφής και το κρατάει με όνομα. */
    fun saveFromGpx(c: Context, gpx: File, name: String, lengthM: Double): SavedRoute? {
        load(c)
        val id = "r" + System.currentTimeMillis()
        return runCatching {
            gpx.copyTo(file(c, id), overwrite = true)
            val r = SavedRoute(id, name.trim().ifBlank { tr("Διαδρομή") }.take(40), lengthM, System.currentTimeMillis())
            list = listOf(r) + list
            write(c)
            r
        }.getOrNull()
    }

    fun rename(c: Context, id: String, name: String) {
        load(c)
        list = list.map { if (it.id == id) it.copy(name = name.trim().take(40).ifBlank { it.name }) else it }
        write(c)
    }

    fun delete(c: Context, id: String) {
        load(c)
        runCatching { file(c, id).delete() }
        list = list.filterNot { it.id == id }
        write(c)
    }

    fun points(c: Context, id: String): List<LatLon> =
        runCatching { Gpx.parse(file(c, id).readText()).map { it.position } }.getOrDefault(emptyList())
}
