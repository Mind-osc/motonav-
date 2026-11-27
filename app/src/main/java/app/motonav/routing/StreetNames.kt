package app.motonav.routing

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Το BRouter δεν ξέρει ονόματα δρόμων. Για κάθε στροφή ρωτάμε το OpenStreetMap (Overpass)
 * ποιος δρόμος περνάει από δύο σημεία λίγο μετά τη στροφή (25 m και 55 m): αυτός είναι ο δρόμος όπου μπαίνεις.
 * Θέλει internet· χωρίς αυτό οι οδηγίες μένουν χωρίς όνομα.
 */
object StreetNames {
    class Probe(val maneuver: Maneuver, val a: LatLon, val b: LatLon)
    class Way(val name: String, val line: List<LatLon>)

    private const val CHUNK = 40
    private const val MAX_MANEUVERS = 160

    fun probes(route: Route): List<Probe> = route.maneuvers
        .filter { it.turn != Turn.ARRIVE && it.turn != Turn.ROUNDABOUT && it.turn != Turn.ROUNDABOUT_LEFT && it.street == null }
        .take(MAX_MANEUVERS)
        .map { Probe(it, route.pointAt(it.atMeters + 25.0), route.pointAt(it.atMeters + 55.0)) }

    fun query(probes: List<Probe>): String = buildString {
        append("[out:json][timeout:25];(")
        probes.forEach { p ->
            append(String.format(Locale.US, "way(around:18,%.6f,%.6f)[highway][name];", p.a.lat, p.a.lon))
        }
        append(");out tags geom;")
    }

    fun parse(json: String): List<Way> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Way>()
        for (i in 0 until els.length()) {
            val o = els.optJSONObject(i) ?: continue
            val name = o.optJSONObject("tags")?.optString("name").orEmpty()
            val g = o.optJSONArray("geometry") ?: continue
            if (name.isBlank() || g.length() < 2) continue
            out += Way(name, (0 until g.length()).map { k -> g.getJSONObject(k).let { LatLon(it.getDouble("lat"), it.getDouble("lon")) } })
        }
        return out
    }

    private fun dist(p: LatLon, line: List<LatLon>): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val d = Geo.project(p, line[i], line[i + 1]).second
            if (d < best) best = d
        }
        return best
    }

    /** Ο δρόμος που περνάει κοντά (< 18 m) και από τα δύο σημεία μετά τη στροφή. */
    fun match(probe: Probe, ways: List<Way>): String? {
        var best: String? = null
        var bestD = 18.0
        for (w in ways) {
            val d = maxOf(dist(probe.a, w.line), dist(probe.b, w.line))
            if (d < bestD) { bestD = d; best = w.name }
        }
        return best
    }

    /** Συμπληρώνει τα ονόματα πάνω στις κινήσεις της διαδρομής. Επιστρέφει πόσα βρήκε. */
    suspend fun fill(route: Route): Int = withContext(Dispatchers.IO) {
        var found = 0
        for (chunk in probes(route).chunked(CHUNK)) {
            val ways = parse(app.motonav.net.Overpass.query(query(chunk), 30_000))
            for (p in chunk) {
                match(p, ways)?.let { p.maneuver.street = it; found++ }
            }
        }
        found
    }

    private fun post(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 30_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("User-Agent", "Streetwolf/0.1 (personal Android app)")
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/** Διαδρομή από έτοιμο ίχνος (GPX): ακολουθούμε ακριβώς τη γραμμή του. */
object TrackRoute {
    fun build(raw: List<LatLon>, avgKmh: Double = 55.0): Route? {
        // πετάμε σημεία πιο κοντά από 4 m (θόρυβος GPS σε στάσεις)
        val pts = ArrayList<LatLon>()
        for (p in raw) if (pts.isEmpty() || Geo.distance(pts.last(), p) >= 4.0) pts += p
        if (pts.size < 2) return null
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Geo.distance(pts[i - 1], pts[i])
        val bare = Route(pts, pts.map { null }, cum, emptyList(), cum.last() / (avgKmh / 3.6), source = "GPX")

        // Στροφές από τη γεωμετρία: αλλαγή κατεύθυνσης ≥ 55° μέσα σε ~24 m.
        val maneuvers = ArrayList<Maneuver>()
        var lastAt = -100.0
        for (i in 1 until pts.size - 1) {
            val at = cum[i]
            if (at - lastAt < 40.0 || at < 15.0 || cum.last() - at < 15.0) continue
            val before = Geo.bearing(bare.pointAt(at - 12.0), pts[i])
            val after = Geo.bearing(pts[i], bare.pointAt(at + 12.0))
            var d = after - before
            while (d > 180) d -= 360
            while (d < -180) d += 360
            if (kotlin.math.abs(d) < 55.0) continue
            val turn = when {
                d >= 110 -> Turn.SHARP_RIGHT
                d > 0 -> Turn.RIGHT
                d <= -110 -> Turn.SHARP_LEFT
                else -> Turn.LEFT
            }
            maneuvers += Maneuver(i, turn, 0, d.toInt(), at)
            lastAt = at
        }
        maneuvers += Maneuver(pts.lastIndex, Turn.ARRIVE, 0, 0, cum.last())
        return Route(pts, pts.map { null }, cum, maneuvers, bare.durationS, source = "GPX")
    }
}

/**
 * Μπαίνεις σε αποθηκευμένη διαδρομή από όπου είσαι: διαδρομή ως το πιο κοντινό σημείο της (προς τα μπροστά)
 * και μετά ακριβώς πάνω της ως το τέλος.
 */
object TrackJoin {
    /**
     * Πού μπαίνεις: το πιο κοντινό σημείο, αλλά ανάμεσα σε όσα είναι σχεδόν το ίδιο κοντά (≤ 30% + 100 m)
     * διαλέγει το πιο «μπροστά», για να μη σε γυρίσει πίσω. Όχι πριν από το minIndex (όσα έχεις ήδη κάνει).
     */
    fun joinIndex(track: List<LatLon>, from: LatLon, minIndex: Int = 0): Int {
        if (track.isEmpty()) return 0
        val start = minIndex.coerceIn(0, track.lastIndex)
        val step = maxOf(1, (track.size - start) / 2000)
        val ds = ArrayList<Pair<Int, Double>>()
        var i = start
        while (i < track.size) { ds += i to Geo.distance(from, track[i]); i += step }
        if (ds.last().first != track.lastIndex) ds += track.lastIndex to Geo.distance(from, track.last())
        val best = ds.minOf { it.second }
        val limit = best * 1.3 + 100.0
        // Το τέλος της διαδρομής μόνο αν δεν υπάρχει κάτι άλλο κοντά.
        return ds.filter { it.second <= limit && it.first < track.lastIndex }.maxOfOrNull { it.first }
            ?: ds.minByOrNull { it.second }!!.first
    }

    /** Ενώνει «προσέγγιση» (κανονική διαδρομή ως το σημείο εισόδου) με το υπόλοιπο της αποθηκευμένης. */
    fun combine(approach: Route?, rest: List<LatLon>): Route? {
        val tail = TrackRoute.build(rest) ?: return null
        if (approach == null || approach.points.size < 2) return tail
        val all = ArrayList<LatLon>(approach.points)
        for (p in tail.points.drop(1)) all += p
        // Ίδια γεωμετρία με τις «έτοιμες» διαδρομές (χωρίς σημεία < 4 m), και στροφές από αυτήν μετά την είσοδο.
        val built = TrackRoute.build(all) ?: return tail
        val pts = built.points
        val cum = built.cumulative
        val joinAt = approach.lengthM
        fun idxAt(m: Double) = cum.indexOfFirst { it >= m }.let { if (it < 0) cum.lastIndex else it }
        val mans = approach.maneuvers.filter { it.turn != Turn.ARRIVE && it.atMeters < joinAt - 25.0 }.map { it.copy(pointIndex = idxAt(it.atMeters)) } +
            built.maneuvers.filter { it.atMeters >= joinAt - 25.0 }
        return Route(
            points = pts,
            elevations = pts.map { null },
            cumulative = cum,
            maneuvers = mans.sortedBy { it.atMeters },
            durationS = approach.durationS + tail.durationS,
            source = "GPX",
            speedLimits = approach.speedLimits,
            roadKinds = approach.roadKinds + (cum.last() to RoadKind.OTHER),
        )
    }
}
