package app.motonav.spots

import app.motonav.geo.LatLon
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.floor

/** Μέρη για μηχανόβιους στον χάρτη, από το OpenStreetMap. */
enum class SpotKind(val label: String, val emoji: String, val color: Int) {
    VIEW("Θέα", "🏞", 0xFF4FC3F7.toInt()),
    PASS("Αυχένας", "⛰", 0xFFB0855A.toInt()),
    WATER("Νερό", "💧", 0xFF29B6F6.toInt()),
    BIKER_CAFE("Στέκι μηχανόβιων", "☕", 0xFFF58142.toInt()),
    MOTO_SHOP("Μαγαζί/συνεργείο μηχανών", "🔧", 0xFFE53935.toInt()),
    WASH("Πλυντήριο", "🚿", 0xFF26A69A.toInt()),
    PICNIC("Στάση/πικνίκ", "🧺", 0xFF8BC34A.toInt()),
}

data class Spot(
    val id: String,
    val kind: SpotKind,
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    /** Φωτογραφία από το OpenStreetMap: image=URL, wikimedia_commons=File:…, ή wikidata=Q… */
    val photo: String = "",
) {
    val latLon get() = LatLon(lat, lon)
}

object Spots {
    /** Κελιά 0,1° (~11 km): κατεβαίνουν μία φορά το καθένα. */
    const val CELL = 0.1

    fun cell(lat: Double, lon: Double): Pair<Int, Int> = floor(lat / CELL).toInt() to floor(lon / CELL).toInt()

    /** Τα κελιά που καλύπτουν την περιοχή (νότος, δύση, βορράς, ανατολή). */
    fun cells(s: Double, w: Double, n: Double, e: Double): List<Pair<Int, Int>> {
        val (a, b) = cell(s, w)
        val (c, d) = cell(n, e)
        val out = ArrayList<Pair<Int, Int>>()
        for (i in a..c) for (j in b..d) out += i to j
        return out
    }

    fun query(s: Double, w: Double, n: Double, e: Double): String = String.format(
        Locale.US,
        """[out:json][timeout:25][bbox:%.4f,%.4f,%.4f,%.4f];
(
 nwr["tourism"="viewpoint"];
 node["mountain_pass"="yes"];
 node["amenity"="drinking_water"];
 node["natural"="spring"]["drinking_water"!="no"];
 nwr["shop"~"^(motorcycle|motorcycle_repair)$"];
 nwr["amenity"="car_wash"];
 nwr["tourism"="picnic_site"];
);
out center tags 3000;""",
        s, w, n, e,
    )

    fun kindOf(t: JSONObject): SpotKind? {
        fun v(k: String) = t.optString(k)
        return when {
            v("tourism") == "viewpoint" -> SpotKind.VIEW
            v("mountain_pass") == "yes" -> SpotKind.PASS
            v("amenity") == "drinking_water" || v("natural") == "spring" -> SpotKind.WATER
            v("shop") == "motorcycle" || v("shop") == "motorcycle_repair" -> SpotKind.MOTO_SHOP
            v("amenity") == "car_wash" -> SpotKind.WASH
            v("tourism") == "picnic_site" -> SpotKind.PICNIC
            v("amenity") in setOf("cafe", "bar", "pub", "restaurant", "fast_food") -> SpotKind.BIKER_CAFE
            else -> null
        }
    }

    fun parse(json: String): List<Spot> {
        val els: JSONArray = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Spot>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: continue
            if (lat.isNaN() || lon.isNaN()) continue
            val t = e.optJSONObject("tags") ?: continue
            val kind = kindOf(t) ?: continue
            val name = t.optString("name:el").ifEmpty { t.optString("name") }.trim()
            val ele = t.optString("ele").toDoubleOrNull()?.let { "${it.toInt()} m" }
            val detail = listOfNotNull(
                kind.label,
                ele,
                t.optString("opening_hours").takeIf { it.isNotEmpty() }?.let { "ωράριο: $it" },
            ).joinToString(" · ")
            val photo = t.optString("image").ifEmpty { t.optString("wikimedia_commons") }
                .ifEmpty { t.optString("wikidata").takeIf { it.startsWith("Q") }.orEmpty() }
            out += Spot(e.optString("type") + e.optLong("id"), kind, name.ifEmpty { kind.label }, detail, lat, lon, photo)
        }
        return out.distinctBy { it.id }
    }
}

/**
 * Μνήμη ανά κελί, στη μνήμη και στον δίσκο: κάθε περιοχή κατεβαίνει μία φορά και κρατιέται 30 μέρες,
 * γιατί το Overpass είναι αργό (30–50″ για μια πόλη).
 */
class SpotCache(private val dir: java.io.File? = null) {
    private val loaded = HashSet<Pair<Int, Int>>()
    private val failedAt = HashMap<Pair<Int, Int>, Long>()
    private val spots = LinkedHashMap<String, Spot>()

    val all: List<Spot> get() = spots.values.toList()

    private fun file(c: Pair<Int, Int>) = dir?.let { java.io.File(it, "v2_${c.first}_${c.second}.json") }

    /** Όσα κελιά λείπουν (και δεν απέτυχαν τα τελευταία 2 λεπτά). Όσα υπάρχουν στον δίσκο φορτώνονται από εκεί. */
    fun missing(cells: List<Pair<Int, Int>>, now: Long): List<Pair<Int, Int>> {
        for (c in cells) {
            if (c in loaded) continue
            val f = file(c) ?: continue
            if (f.exists() && now - f.lastModified() < MAX_AGE_MS) {
                runCatching { fromJson(f.readText()) }.getOrNull()?.let { list ->
                    loaded += c
                    list.forEach { spots[it.id] = it }
                }
            }
        }
        return cells.filter { it !in loaded && (failedAt[it]?.let { t -> now - t > 120_000 } ?: true) }
    }

    fun add(cells: List<Pair<Int, Int>>, found: List<Spot>) {
        loaded += cells
        found.forEach { spots[it.id] = it }
        val d = dir ?: return
        runCatching {
            d.mkdirs()
            val byCell = found.groupBy { Spots.cell(it.lat, it.lon) }
            for (c in cells) file(c)?.writeText(toJson(byCell[c].orEmpty()))
        }
    }

    fun failed(cells: List<Pair<Int, Int>>, now: Long) = cells.forEach { failedAt[it] = now }

    companion object {
        const val MAX_AGE_MS = 30L * 24 * 3600 * 1000

        fun toJson(list: List<Spot>): String = JSONArray().apply {
            list.forEach {
                put(JSONObject().put("id", it.id).put("k", it.kind.name).put("n", it.name).put("d", it.detail).put("lat", it.lat).put("lon", it.lon).put("p", it.photo))
            }
        }.toString()

        fun fromJson(s: String): List<Spot> {
            val a = JSONArray(s)
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val kind = runCatching { SpotKind.valueOf(o.optString("k")) }.getOrNull() ?: return@mapNotNull null
                Spot(o.optString("id"), kind, o.optString("n"), o.optString("d"), o.optDouble("lat"), o.optDouble("lon"), o.optString("p"))
            }
        }
    }
}
