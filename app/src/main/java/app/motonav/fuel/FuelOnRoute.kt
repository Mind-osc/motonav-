package app.motonav.fuel

import app.motonav.geo.LatLon
import app.motonav.routing.OnRoute
import app.motonav.routing.Route
import org.json.JSONObject
import java.util.Locale
import app.motonav.i18n.tr

/** Βενζινάδικο από το OpenStreetMap. */
data class FuelStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** opening_hours όπως είναι στο OSM (μπορεί να λείπει). */
    val hours: String? = null,
) {
    val position: LatLon get() = LatLon(lat, lon)
}

/** Βενζινάδικο πάνω στη διαδρομή: πόσο μετά την αρχή και πόσο έξω από τη γραμμή. */
data class FuelOnRouteStop(val station: FuelStation, val alongM: Double, val offM: Double) {
    /** Χονδρική παράκαμψη: πήγαινε-έλα από τη γραμμή. */
    val detourM: Double get() = 2 * offM
}

object FuelOnRoute {
    /** Πόσο μακριά από τη γραμμή μετράει ακόμα ως «στη διαδρομή». */
    const val MAX_OFF_M = 700.0

    /**
     * Ερώτηση Overpass: βενζινάδικα μέσα σε κουτάκια που σκεπάζουν τη διαδρομή (ένα ανά ~25 km, με περιθώριο ~1 km).
     * Τα κουτάκια (bbox) απαντιούνται γρήγορα από το ευρετήριο του Overpass· το around γύρω από μια γραμμή
     * εκατοντάδων σημείων κόβεται από timeout. Το «≤ 700 m από τη γραμμή» το κάνει μετά το along().
     */
    fun query(route: Route): String {
        val boxes = boxes(route)
        val parts = boxes.joinToString("\n") { b ->
            String.format(Locale.US, " nwr[\"amenity\"=\"fuel\"](%.4f,%.4f,%.4f,%.4f);", b[0], b[1], b[2], b[3])
        }
        return "[out:json][timeout:50];\n(\n$parts\n);\nout center tags 1500;"
    }

    /** (νότος, δύση, βορράς, ανατολή) για κάθε κομμάτι ~chunkM της διαδρομής. */
    fun boxes(route: Route, chunkM: Double = 25_000.0, padDeg: Double = 0.009): List<DoubleArray> {
        val pts = OnRoute.sample(route, 500.0, maxPoints = 2000)
        if (pts.isEmpty()) return emptyList()
        val step = maxOf(1, (chunkM / maxOf(1.0, route.lengthM / pts.size)).toInt())
        return pts.chunked(step).map { c ->
            val k = 1.0 / kotlin.math.cos(Math.toRadians(c.first().lat))
            doubleArrayOf(
                c.minOf { it.lat } - padDeg, c.minOf { it.lon } - padDeg * k,
                c.maxOf { it.lat } + padDeg, c.maxOf { it.lon } + padDeg * k,
            )
        }
    }

    /**
     * TomTom Search Along Route (με το κλειδί σου): βενζινάδικα (κατηγορία 7311) με παράκαμψη ≤ 10′ από τη διαδρομή.
     * Έως 20 ανά ερώτηση, οπότε σε μεγάλες διαδρομές ρωτάμε ανά κομμάτι ~60 km.
     */
    fun tomtomAlong(route: Route, key: String): List<FuelStation> {
        val len = route.lengthM
        if (len <= 0 || route.points.size < 2) return emptyList()
        val chunk = 60_000.0
        val out = ArrayList<FuelStation>()
        var from = 0.0
        var lastError: Exception? = null
        var ok = false
        while (from < len && out.size < 400) {
            val to = minOf(len, from + chunk)
            val part = route.slice(from, to)
            if (part.size >= 2) {
                // έως ~150 σημεία ανά κομμάτι (ένα ανά ~400 m)
                val step = maxOf(1, part.size / 150)
                val pts = part.filterIndexed { i, _ -> i % step == 0 || i == part.lastIndex }
                try {
                    out += tomtomParse(tomtomPost(pts, key))
                    ok = true
                } catch (e: java.io.IOException) {
                    lastError = e
                }
            }
            from = to
        }
        if (!ok && lastError != null) throw lastError
        return out.distinctBy { it.id }
    }

    private fun tomtomPost(pts: List<LatLon>, key: String): String {
        val url = java.net.URL(
            "https://api.tomtom.com/search/2/searchAlongRoute/" +
                java.net.URLEncoder.encode("βενζίνη", "UTF-8").replace("+", "%20") +
                ".json?categorySet=7311&maxDetourTime=600&limit=20&spreadingMode=auto&language=el-GR&key=" +
                java.net.URLEncoder.encode(key, "UTF-8"),
        )
        val body = org.json.JSONObject().put(
            "route",
            org.json.JSONObject().put("points", org.json.JSONArray().apply {
                pts.forEach { put(org.json.JSONObject().put("lat", it.lat).put("lon", it.lon)) }
            }),
        ).toString()
        val c = url.openConnection() as java.net.HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 20_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                throw java.io.IOException("TomTom HTTP $code ${err.take(140)}")
            }
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun tomtomParse(json: String): List<FuelStation> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<FuelStation>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pos = o.optJSONObject("position") ?: continue
            val lat = pos.optDouble("lat")
            val lon = pos.optDouble("lon")
            if (lat.isNaN() || lon.isNaN()) continue
            val poi = o.optJSONObject("poi")
            val brand = poi?.optJSONArray("brands")?.optJSONObject(0)?.optString("name").orEmpty()
            val name = brand.ifBlank { poi?.optString("name").orEmpty() }.ifBlank { tr("Βενζινάδικο") }
            out += FuelStation("tt-" + o.optString("id").ifBlank { "$lat,$lon" }, name, lat, lon)
        }
        return out
    }

    fun parse(json: String): List<FuelStation> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<FuelStation>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: continue
            if (lat.isNaN() || lon.isNaN()) continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            fun tag(k: String) = if (tags.has(k) && !tags.isNull(k)) tags.optString(k).takeIf { it.isNotBlank() } else null
            val name = tag("brand") ?: tag("name:el") ?: tag("name") ?: tag("operator") ?: tr("Βενζινάδικο")
            out += FuelStation("osm-${e.optString("type")}-${e.optLong("id")}", name, lat, lon, tag("opening_hours"))
        }
        return out.distinctBy { it.id }
    }

    /** Τα βενζινάδικα πάνω στη διαδρομή, με τη σειρά που θα τα βρεις. Δύο σε < 60 m (π.χ. node + κτίριο) γίνονται ένα. */
    fun along(route: Route, stations: List<FuelStation>, maxOffM: Double = MAX_OFF_M): List<FuelOnRouteStop> {
        val on = stations.mapNotNull { s ->
            OnRoute.project(route, s.position)?.takeIf { it.offM <= maxOffM }?.let { FuelOnRouteStop(s, it.alongM, it.offM) }
        }.sortedBy { it.alongM }
        val out = ArrayList<FuelOnRouteStop>()
        for (f in on) {
            val dup = out.lastOrNull { kotlin.math.abs(it.alongM - f.alongM) < 300 && app.motonav.geo.Geo.distance(it.station.position, f.station.position) < 60 }
            if (dup == null) out += f
        }
        return out
    }

    /** Τα επόμενα μπροστά σου (όχι αυτά που πέρασες). */
    fun ahead(stops: List<FuelOnRouteStop>, progressM: Double): List<FuelOnRouteStop> = stops.filter { it.alongM > progressM + 30 }

    /**
     * Το μεγαλύτερο κομμάτι της διαδρομής χωρίς βενζινάδικο (από fromM ως το τέλος), σε μέτρα.
     * Για το «προσοχή, 120 km χωρίς βενζίνη».
     */
    fun longestGap(stops: List<FuelOnRouteStop>, lengthM: Double, fromM: Double = 0.0): Double {
        var prev = fromM
        var gap = 0.0
        for (s in stops) {
            if (s.alongM < fromM) continue
            gap = maxOf(gap, s.alongM - prev)
            prev = s.alongM
        }
        return maxOf(gap, lengthM - prev)
    }
}
