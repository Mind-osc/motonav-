package app.motonav.search

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.net.Overpass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import app.motonav.i18n.tr

/** Ώρες, τηλέφωνο, site ενός μέρους, από το OpenStreetMap. */
data class PlaceInfo(
    val hours: String? = null,
    val phone: String? = null,
    val website: String? = null,
) {
    val isEmpty: Boolean get() = hours == null && phone == null && website == null
}

object PlaceInfoLookup {
    /**
     * Το πιο κοντινό αντικείμενο με όνομα σε ~60 m που ταιριάζει με το όνομα του προορισμού.
     * null αν δεν υπάρχει internet ή δεν βρέθηκε κάτι.
     */
    suspend fun lookup(p: LatLon, name: String?): PlaceInfo? = withContext(Dispatchers.IO) {
        if (name.isNullOrBlank()) return@withContext null
        runCatching {
            val q = String.format(
                Locale.US,
                "[out:json][timeout:12];nwr(around:60,%.6f,%.6f)[name];out tags center 15;",
                p.lat, p.lon,
            )
            parse(Overpass.query(q, 15_000), p, name)
        }.getOrNull()
    }

    fun parse(json: String, p: LatLon, name: String): PlaceInfo? {
        val els = JSONObject(json).optJSONArray("elements") ?: return null
        var best: Pair<Int, Double>? = null
        var bestTags: JSONObject? = null
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val tags = e.optJSONObject("tags") ?: continue
            val n = tags.optString("name")
            if (n.isBlank()) continue
            val score = PlaceParsers.textScore(name, n)
            if (score > 2) continue
            val lat = if (e.has("lat")) e.optDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.optDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: continue
            val d = Geo.distance(p, LatLon(lat, lon))
            val b = best
            if (b == null || score < b.first || (score == b.first && d < b.second)) {
                best = score to d
                bestTags = tags
            }
        }
        val t = bestTags ?: return null
        fun tag(vararg keys: String) = keys.map { t.optString(it) }.firstOrNull { it.isNotBlank() }
        val info = PlaceInfo(
            hours = tag("opening_hours")?.let(::hoursGreek),
            phone = tag("phone", "contact:phone", "contact:mobile")?.substringBefore(";")?.trim(),
            website = tag("website", "contact:website", "url")?.substringBefore(";")?.trim(),
        )
        return info.takeUnless { it.isEmpty }
    }

    /** «Mo-Fr 08:00-16:00; Sa 09:00-13:00» → «Δε-Πα 08:00-16:00 · Σα 09:00-13:00» */
    fun hoursGreek(s: String): String {
        if (s.trim() == "24/7") return tr("Όλο το 24ωρο")
        val days = mapOf("Mo" to tr("Δε"), "Tu" to tr("Τρ"), "We" to tr("Τε"), "Th" to tr("Πε"), "Fr" to tr("Πα"), "Sa" to tr("Σα"), "Su" to tr("Κυ"), "PH" to tr("Αργίες"), "off" to tr("κλειστό"))
        var out = s
        days.forEach { (en, gr) -> out = out.replace(Regex("\\b$en\\b"), gr) }
        return out.split(";").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
    }
}

/**
 * Σημείωση στο OpenStreetMap («κάτι λείπει ή είναι λάθος εδώ»). Ανώνυμα, χωρίς λογαριασμό:
 * τη βλέπουν και τη διορθώνουν εθελοντές.
 */
object OsmNotes {
    suspend fun create(p: LatLon, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = "lat=" + String.format(Locale.US, "%.6f", p.lat) +
                "&lon=" + String.format(Locale.US, "%.6f", p.lon) +
                "&text=" + URLEncoder.encode("$text\n\n#Streetwolf", "UTF-8")
            val c = URL("https://api.openstreetmap.org/api/0.6/notes.json").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8_000
                c.readTimeout = 15_000
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("User-Agent", "Streetwolf/0.1 (Android)")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                true
            } finally {
                c.disconnect()
            }
        }.getOrDefault(false)
    }
}
