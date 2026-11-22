package app.motonav.trips

import app.motonav.geo.LatLon
import org.json.JSONArray
import org.json.JSONObject

/**
 * Διαδρομή από αρχείο: GPX (trk/rte), KML (LineString), GeoJSON (LineString/MultiLineString), ITN (TomTom).
 * Για Kurviger: εξαγωγή σε GPX από το Kurviger και άνοιγμα εδώ.
 */
object RouteFiles {
    fun parse(text: String): List<LatLon> {
        Gpx.parse(text).map { it.position }.takeIf { it.size >= 2 }?.let { return it }
        kml(text).takeIf { it.size >= 2 }?.let { return it }
        geoJson(text).takeIf { it.size >= 2 }?.let { return it }
        itn(text).takeIf { it.size >= 2 }?.let { return it }
        return emptyList()
    }

    private val lineString = Regex("""<LineString\b.*?<coordinates>(.*?)</coordinates>""", RegexOption.DOT_MATCHES_ALL)

    fun kml(text: String): List<LatLon> = lineString.findAll(text).flatMap { m ->
        m.groupValues[1].trim().split(Regex("\\s+")).mapNotNull { t ->
            val p = t.split(',')
            val lon = p.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = p.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            LatLon(lat, lon)
        }
    }.toList()

    fun geoJson(text: String): List<LatLon> {
        val t = text.trimStart()
        if (!t.startsWith("{")) return emptyList()
        return runCatching {
            val out = ArrayList<LatLon>()
            fun line(a: JSONArray) {
                for (i in 0 until a.length()) {
                    val c = a.optJSONArray(i) ?: continue
                    out += LatLon(c.getDouble(1), c.getDouble(0))
                }
            }
            fun geom(g: JSONObject?) {
                when (g?.optString("type")) {
                    "LineString" -> line(g.getJSONArray("coordinates"))
                    "MultiLineString" -> { val a = g.getJSONArray("coordinates"); for (i in 0 until a.length()) line(a.getJSONArray(i)) }
                    "GeometryCollection" -> { val a = g.getJSONArray("geometries"); for (i in 0 until a.length()) geom(a.optJSONObject(i)) }
                }
            }
            val o = JSONObject(text)
            when (o.optString("type")) {
                "FeatureCollection" -> { val f = o.getJSONArray("features"); for (i in 0 until f.length()) geom(f.optJSONObject(i)?.optJSONObject("geometry")) }
                "Feature" -> geom(o.optJSONObject("geometry"))
                else -> geom(o)
            }
            out
        }.getOrDefault(emptyList())
    }

    /** ITN (TomTom): «lon×100000|lat×100000|όνομα|τύπος|» ανά γραμμή. */
    fun itn(text: String): List<LatLon> = text.lineSequence().mapNotNull { line ->
        val p = line.trim().split('|')
        if (p.size < 2) return@mapNotNull null
        val lon = p[0].trim().toLongOrNull() ?: return@mapNotNull null
        val lat = p[1].trim().toLongOrNull() ?: return@mapNotNull null
        LatLon(lat / 100000.0, lon / 100000.0)
    }.toList()
}
