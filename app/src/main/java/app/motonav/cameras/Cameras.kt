package app.motonav.cameras

import android.content.Context
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Κάμερα ταχύτητας.
 * directionDeg: προς τα πού «κοιτάει» (για ποια κατεύθυνση κυκλοφορίας μετράει), αν είναι γνωστό.
 * source: "osm" ή "mine" (δική σου σημείωση).
 */
data class SpeedCamera(
    val id: String,
    val lat: Double,
    val lon: Double,
    val maxspeed: Int? = null,
    val directionDeg: Double? = null,
    val source: String = "osm",
    /** "speed" (ταχύτητας), "red" (φαναριού), "ai" (έξυπνη κάμερα). */
    val kind: String = "speed",
    val label: String? = null,
) {
    val position: LatLon get() = LatLon(lat, lon)

    /** Τι λέμε στη μπάρα και στη φωνή. */
    val title: String
        get() = when (kind) {
            "red" -> "Κάμερα φαναριού"
            "ai" -> "Κάμερα AI"
            else -> "Κάμερα"
        }
}

object CameraParser {
    private val cardinal = mapOf(
        "N" to 0.0, "NNE" to 22.5, "NE" to 45.0, "ENE" to 67.5, "E" to 90.0, "ESE" to 112.5, "SE" to 135.0, "SSE" to 157.5,
        "S" to 180.0, "SSW" to 202.5, "SW" to 225.0, "WSW" to 247.5, "W" to 270.0, "WNW" to 292.5, "NW" to 315.0, "NNW" to 337.5,
    )

    /** OSM direction: μοίρες ή σημείο του ορίζοντα. forward/backward και άγνωστα → null. */
    fun direction(tag: String?): Double? {
        val t = tag?.trim()?.uppercase(Locale.ROOT) ?: return null
        t.toDoubleOrNull()?.let { return ((it % 360) + 360) % 360 }
        return cardinal[t]
    }

    /** "50", "50 km/h", "30 mph" → km/h. */
    fun maxspeed(tag: String?): Int? {
        val t = tag?.trim()?.lowercase(Locale.ROOT) ?: return null
        val n = Regex("""\d+""").find(t)?.value?.toIntOrNull() ?: return null
        return if ("mph" in t) (n * 1.609).toInt() else n
    }

    fun overpass(json: String): List<SpeedCamera> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<SpeedCamera>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: continue
            if (lat.isNaN() || lon.isNaN()) continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            fun tag(k: String) = if (tags.has(k) && !tags.isNull(k)) tags.optString(k) else null
            out += SpeedCamera(
                id = "osm-${e.optString("type")}-${e.optLong("id")}",
                lat = lat,
                lon = lon,
                maxspeed = maxspeed(tag("maxspeed")),
                directionDeg = direction(tag("direction") ?: tag("camera:direction")),
            )
        }
        return out.distinctBy { it.id }
    }

    fun toJson(list: List<SpeedCamera>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject().put("id", it.id).put("la", it.lat).put("lo", it.lon).put("src", it.source)
                    .apply {
                        it.maxspeed?.let { m -> put("max", m) }
                        it.directionDeg?.let { d -> put("dir", d) }
                        if (it.kind != "speed") put("kind", it.kind)
                        it.label?.let { l -> put("label", l) }
                    }
            )
        }
    }.toString()

    fun fromJson(s: String): List<SpeedCamera> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            SpeedCamera(
                id = o.optString("id"),
                lat = o.optDouble("la"),
                lon = o.optDouble("lo"),
                maxspeed = if (o.has("max")) o.optInt("max") else null,
                directionDeg = if (o.has("dir")) o.optDouble("dir") else null,
                source = o.optString("src", "osm"),
                kind = o.optString("kind", "speed").ifBlank { "speed" },
                label = if (o.has("label")) o.optString("label") else null,
            )
        }
    }.getOrDefault(emptyList())
}

/** Κάμερες από το OpenStreetMap γύρω σου (Overpass API, δωρεάν). */
class CameraClient {
    suspend fun around(center: LatLon, radiusM: Int = 40_000): List<SpeedCamera> = withContext(Dispatchers.IO) {
        val around = String.format(Locale.US, "(around:%d,%.5f,%.5f)", radiusM, center.lat, center.lon)
        val q = "[out:json][timeout:25];(" +
            "node[\"highway\"=\"speed_camera\"]$around;" +
            "node[\"enforcement\"=\"maxspeed\"]$around;" +
            "relation[\"type\"=\"enforcement\"][\"enforcement\"~\"maxspeed|average_speed\"]$around;" +
            ");out center tags;"
        CameraParser.overpass(app.motonav.net.Overpass.query(q, 30_000))
    }
}

/** Αρχεία στο κινητό: οι τελευταίες κάμερες από το OSM (για χωρίς internet) και οι δικές σου. */
class CameraStore(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val osmFile = File(dir, "cameras_osm.json")
    private val mineFile = File(dir, "cameras_mine.json")

    private val assets = context.applicationContext.assets

    fun loadOsm(): List<SpeedCamera> = read(osmFile)

    /** Κάμερες που έρχονται μαζί με την εφαρμογή (Αττική, από τις δημοσιευμένες λίστες). */
    fun loadBundled(): List<SpeedCamera> = runCatching {
        assets.open("cameras_attica.json").bufferedReader().use { CameraParser.fromJson(it.readText()) }
    }.getOrDefault(emptyList())

    /** Όλες μαζί, χωρίς διπλές: αν μια δική μας πέφτει < 40 m από κάμερα του OSM, κρατάμε του OSM. */
    fun merge(osm: List<SpeedCamera>, mine: List<SpeedCamera>, bundled: List<SpeedCamera>): List<SpeedCamera> {
        val extra = bundled.filter { b -> osm.none { Geo.distance(it.position, b.position) < 40 } }
        return osm + extra + mine
    }
    fun saveOsm(list: List<SpeedCamera>) = write(osmFile, list)
    fun loadMine(): List<SpeedCamera> = read(mineFile)

    fun addMine(at: LatLon, headingDeg: Double?): List<SpeedCamera> {
        val list = loadMine() + SpeedCamera(
            id = "mine-${System.currentTimeMillis()}",
            lat = at.lat,
            lon = at.lon,
            directionDeg = headingDeg,
            source = "mine",
        )
        write(mineFile, list)
        return list
    }

    fun removeMine(id: String): List<SpeedCamera> {
        val list = loadMine().filterNot { it.id == id }
        write(mineFile, list)
        return list
    }

    private fun read(f: File) = if (f.exists()) CameraParser.fromJson(f.readText()) else emptyList()
    private fun write(f: File, list: List<SpeedCamera>) = runCatching { f.writeText(CameraParser.toJson(list)) }
}

/** Ποια κάμερα είναι μπροστά σου και σε πόσα μέτρα. */
object CameraAlerts {
    const val RANGE_M = 300.0

    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360 + 540) % 360 - 180)
        return d
    }

    /**
     * Με διαδρομή: μόνο κάμερες πάνω στη διαδρομή (≤ 35 m από τη γραμμή) και μπροστά σου (0..range m).
     * Χωρίς διαδρομή: κάμερες ≤ range m, μπροστά στην κατεύθυνσή σου (±25°), και αν η κάμερα έχει
     * κατεύθυνση, να ταιριάζει με τη δική σου (±70°).
     */
    fun ahead(
        here: LatLon,
        headingDeg: Double?,
        cams: List<SpeedCamera>,
        route: Route? = null,
        progressM: Double? = null,
        range: Double = RANGE_M,
    ): Pair<SpeedCamera, Double>? {
        val near = cams.filter { Geo.distance(here, it.position) <= range + 1500 }
        if (near.isEmpty()) return null

        if (route != null && progressM != null) {
            var best: Pair<SpeedCamera, Double>? = null
            val pts = route.points
            val cum = route.cumulative
            val start = cum.indexOfFirst { it >= progressM - 20 }.let { if (it < 1) 0 else it - 1 }
            for (cam in near) {
                // Το πιο κοντινό σημείο της διαδρομής στην κάμερα (όχι το πρώτο που πέφτει μέσα στα 35 m).
                var bestD = Double.MAX_VALUE
                var bestAlong = 0.0
                var bestSeg = start
                var i = start
                while (i < pts.size - 1 && cum[i] <= progressM + range + 50) {
                    val (t, d) = Geo.project(cam.position, pts[i], pts[i + 1])
                    if (d < bestD) {
                        bestD = d
                        bestAlong = cum[i] + t * (cum[i + 1] - cum[i]) - progressM
                        bestSeg = i
                    }
                    i++
                }
                if (bestD <= 35.0 && bestAlong in 0.0..range) {
                    val segBearing = Geo.bearing(pts[bestSeg], pts[minOf(bestSeg + 1, pts.lastIndex)])
                    val dirOk = cam.directionDeg?.let { angleDiff(it, segBearing) <= 70 } ?: true
                    if (dirOk && (best == null || bestAlong < best.second)) best = cam to bestAlong
                }
            }
            return best
        }

        if (headingDeg == null) return null
        return near.mapNotNull { cam ->
            val d = Geo.distance(here, cam.position)
            if (d > range) return@mapNotNull null
            val towards = d < 40 || angleDiff(Geo.bearing(here, cam.position), headingDeg) <= 25
            val dirOk = cam.directionDeg?.let { angleDiff(it, headingDeg) <= 70 } ?: true
            if (towards && dirOk) cam to d else null
        }.minByOrNull { it.second }
    }
}
