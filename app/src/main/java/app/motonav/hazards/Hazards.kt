package app.motonav.hazards

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.routing.OnRoute
import app.motonav.routing.Route
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Είδη αναφοράς κινδύνου, με πόσο ζουν πριν σβηστούν μόνα τους. */
enum class HazardKind(val label: String, val emoji: String, val ttlMin: Int, val color: Int, val labelEn: String) {
    WEATHER("Κακός καιρός", "🌧", 3 * 60, 0xFF4FC3F7.toInt(), "bad weather"),
    OBJECT("Αντικείμενο στον δρόμο", "📦", 2 * 60, 0xFFFFB300.toInt(), "object on the road"),
    SLIPPERY("Λάδια / χαλίκι", "🛢", 24 * 60, 0xFFF58142.toInt(), "oil or gravel"),
    POTHOLE("Λακκούβα", "🕳", 30 * 24 * 60, 0xFF8D6E63.toInt(), "pothole"),
    ANIMAL("Ζώο στον δρόμο", "🐐", 60, 0xFF8BC34A.toInt(), "animal on the road"),
    WORKS("Έργα", "🚧", 7 * 24 * 60, 0xFFFFD23F.toInt(), "road works"),
    ACCIDENT("Τροχαίο", "💥", 2 * 60, 0xFFE53935.toInt(), "accident"),
    CLOSED("Κλειστός δρόμος", "⛔", 24 * 60, 0xFFD32F2F.toInt(), "road closed"),
}

data class Hazard(
    val id: String,
    val kind: HazardKind,
    val lat: Double,
    val lon: Double,
    val createdAtMs: Long,
    /** Η κατεύθυνσή σου όταν το σημείωσες (για ειδοποίηση χωρίς διαδρομή). */
    val headingDeg: Double? = null,
) {
    val position: LatLon get() = LatLon(lat, lon)
    val expiresAtMs: Long get() = createdAtMs + kind.ttlMin * 60_000L
    fun alive(nowMs: Long) = nowMs < expiresAtMs
}

object Hazards {
    /** Από πόσο μακριά ειδοποιούμε. */
    const val RANGE_M = 500.0

    fun angleDiff(a: Double, b: Double): Double = kotlin.math.abs(((a - b) % 360 + 540) % 360 - 180)

    fun toJson(list: List<Hazard>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject().put("id", it.id).put("k", it.kind.name).put("la", it.lat).put("lo", it.lon).put("t", it.createdAtMs)
                    .apply { it.headingDeg?.let { h -> put("h", h) } },
            )
        }
    }.toString()

    fun fromJson(s: String): List<Hazard> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val kind = HazardKind.entries.firstOrNull { it.name == o.optString("k") } ?: return@mapNotNull null
            Hazard(
                o.optString("id"), kind, o.optDouble("la"), o.optDouble("lo"), o.optLong("t"),
                if (o.has("h")) o.optDouble("h") else null,
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Ο πιο κοντινός κίνδυνος μπροστά σου (≤ range m).
     * Με διαδρομή: ≤ 40 m από τη γραμμή. Χωρίς: μπροστά στην κατεύθυνσή σου (±30°).
     */
    fun ahead(
        here: LatLon,
        headingDeg: Double?,
        list: List<Hazard>,
        route: Route? = null,
        progressM: Double? = null,
        range: Double = RANGE_M,
    ): Pair<Hazard, Double>? {
        val near = list.filter { Geo.distance(here, it.position) <= range + 1500 }
        if (near.isEmpty()) return null
        if (route != null && progressM != null) {
            return near.mapNotNull { h ->
                val p = OnRoute.project(route, h.position, progressM - 20, progressM + range + 50) ?: return@mapNotNull null
                val along = p.alongM - progressM
                if (p.offM <= 40.0 && along in 0.0..range) h to along else null
            }.minByOrNull { it.second }
        }
        if (headingDeg == null) return null
        return near.mapNotNull { h ->
            val d = Geo.distance(here, h.position)
            if (d > range) return@mapNotNull null
            val towards = d < 40 || angleDiff(Geo.bearing(here, h.position), headingDeg) <= 30
            if (towards) h to d else null
        }.minByOrNull { it.second }
    }
}

/** Οι αναφορές σου, σε αρχείο στο κινητό. Οι ληγμένες σβήνουν όταν φορτώνεις. */
class HazardStore(dir: File) {
    private val file = File(dir, "hazards.json")

    fun load(nowMs: Long = System.currentTimeMillis()): List<Hazard> =
        (if (file.exists()) Hazards.fromJson(file.readText()) else emptyList()).filter { it.alive(nowMs) }

    private fun save(list: List<Hazard>) = runCatching { file.writeText(Hazards.toJson(list)) }

    fun add(kind: HazardKind, at: LatLon, headingDeg: Double?, nowMs: Long = System.currentTimeMillis()): List<Hazard> {
        // Ίδιο είδος σε < 50 m: ανανεώνεται, δεν διπλασιάζεται.
        val rest = load(nowMs).filterNot { it.kind == kind && Geo.distance(it.position, at) < 50 }
        val list = rest + Hazard("h-$nowMs", kind, at.lat, at.lon, nowMs, headingDeg)
        save(list)
        return list
    }

    fun remove(id: String): List<Hazard> {
        val list = load().filterNot { it.id == id }
        save(list)
        return list
    }
}
