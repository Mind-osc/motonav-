package app.motonav.routing

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.motonav.geo.LatLon
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Περιοχή αποφυγής: κύκλος όπου δεν περνάνε οι διαδρομές (BRouter). */
data class NoGo(val center: LatLon, val radiusM: Int)

object NoGoAreas {
    var list by mutableStateOf<List<NoGo>>(emptyList())
        private set
    /** Προσωρινές (π.χ. «κλειστός δρόμος μπροστά»): μόνο για την τωρινή πλοήγηση, δεν αποθηκεύονται. */
    var temp by mutableStateOf<List<NoGo>>(emptyList())
        private set

    fun addTemp(center: LatLon, radiusM: Int) { temp = temp + NoGo(center, radiusM) }
    fun clearTemp() { temp = emptyList() }
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        list = runCatching {
            val a = JSONArray(prefs()?.getString("areas", "[]") ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { NoGo(LatLon(it.getDouble("la"), it.getDouble("lo")), it.getInt("r")) }
            }
        }.getOrDefault(emptyList())
    }

    private fun prefs() = appContext?.getSharedPreferences("nogo", Context.MODE_PRIVATE)

    private fun save() {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("la", it.center.lat).put("lo", it.center.lon).put("r", it.radiusM)) }
        prefs()?.edit()?.putString("areas", a.toString())?.apply()
    }

    fun add(center: LatLon, radiusM: Int) {
        list = list + NoGo(center, radiusM)
        save()
    }

    fun remove(n: NoGo) {
        list = list - n
        save()
    }

    fun clear() {
        list = emptyList()
        save()
    }

    /** Για το BRouter: «lon,lat,radius|lon,lat,radius». null αν δεν υπάρχουν. */
    fun brouterParam(): String? = (list + temp).takeIf { it.isNotEmpty() }?.joinToString("|") {
        String.format(Locale.US, "%.6f,%.6f,%d", it.center.lon, it.center.lat, it.radiusM)
    }

    /** Πολύγωνο (48 σημεία) για να φαίνεται στον χάρτη. */
    fun ring(n: NoGo, steps: Int = 48): List<LatLon> = (0..steps).map { i ->
        LoopPlanner.offset(n.center, 360.0 * i / steps, n.radiusM.toDouble())
    }
}
