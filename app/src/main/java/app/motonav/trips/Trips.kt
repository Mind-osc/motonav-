package app.motonav.trips

import android.content.Context
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import org.json.JSONObject
import java.io.File

/** Σύνοψη μιας οδήγησης. Χρόνοι σε epoch ms, αποστάσεις σε μέτρα. */
data class TripSummary(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val movingS: Double,
    val maxSpeedKmh: Double,
    val maxLeanLeft: Double,
    val maxLeanRight: Double,
    val maxAltM: Double?,
    val minAltM: Double?,
    val ascentM: Double,
) {
    val avgMovingKmh: Double get() = if (movingS > 0) distanceM / movingS * 3.6 else 0.0

    companion object {
        fun toJson(s: TripSummary): String = JSONObject()
            .put("id", s.id).put("start", s.startMs).put("end", s.endMs)
            .put("dist", s.distanceM).put("moving", s.movingS).put("vmax", s.maxSpeedKmh)
            .put("leanL", s.maxLeanLeft).put("leanR", s.maxLeanRight)
            .apply {
                s.maxAltM?.let { put("altMax", it) }
                s.minAltM?.let { put("altMin", it) }
            }
            .put("ascent", s.ascentM)
            .toString()

        fun fromJson(json: String): TripSummary? = runCatching {
            val o = JSONObject(json)
            TripSummary(
                id = o.getString("id"),
                startMs = o.getLong("start"),
                endMs = o.getLong("end"),
                distanceM = o.getDouble("dist"),
                movingS = o.getDouble("moving"),
                maxSpeedKmh = o.getDouble("vmax"),
                maxLeanLeft = o.optDouble("leanL", 0.0),
                maxLeanRight = o.optDouble("leanR", 0.0),
                maxAltM = if (o.has("altMax")) o.getDouble("altMax") else null,
                minAltM = if (o.has("altMin")) o.getDouble("altMin") else null,
                ascentM = o.optDouble("ascent", 0.0),
            )
        }.getOrNull()
    }
}

/** Υπολογισμός στατιστικών καθώς έρχονται τα στίγματα. */
class TripStats {
    var points = 0
        private set
    private var last: Pair<LatLon, Long>? = null
    private var startMs = 0L
    private var endMs = 0L
    private var dist = 0.0
    private var moving = 0.0
    private var vmax = 0.0
    private var leanL = 0.0
    private var leanR = 0.0
    private var altMax: Double? = null
    private var altMin: Double? = null
    private var ascent = 0.0
    private var altSmooth: Double? = null

    fun add(lat: Double, lon: Double, timeMs: Long, speedKmh: Double?, altitude: Double?) {
        val p = LatLon(lat, lon)
        if (points == 0) startMs = timeMs
        endMs = timeMs
        last?.let { (q, t) ->
            val d = Geo.distance(q, p)
            val dt = (timeMs - t) / 1000.0
            // αγνοούμε άλματα GPS (> 300 km/h)
            if (dt > 0 && d / dt < 83) {
                dist += d
                if (d / dt > 1.5) moving += dt
            }
        }
        last = p to timeMs
        speedKmh?.let { if (it > vmax && it < 300) vmax = it }
        if (altitude != null) {
            altMax = maxOf(altMax ?: altitude, altitude)
            altMin = minOf(altMin ?: altitude, altitude)
            // ανάβαση από εξομαλυμένο υψόμετρο, για να μη μετράει τον θόρυβο του GPS
            val prev = altSmooth
            val s = prev?.let { it + 0.2 * (altitude - it) } ?: altitude
            if (prev != null && s > prev) ascent += s - prev
            altSmooth = s
        }
        points++
    }

    fun lean(deg: Double) {
        if (deg < 0 && -deg > leanL) leanL = -deg
        if (deg > 0 && deg > leanR) leanR = deg
    }

    fun summary(id: String) = TripSummary(id, startMs, endMs, dist, moving, vmax, leanL, leanR, altMax, altMin, ascent)
}

/** Ένα σημείο του ίχνους. */
data class TrackPoint(val position: LatLon, val ele: Double?, val timeMs: Long?)

object Gpx {
    // <trkpt> ή <rtept>, με τα lat/lon σε όποια σειρά, και με ή χωρίς περιεχόμενο.
    private val point = Regex("""<(trkpt|rtept)\b([^>]*?)(/>|>(.*?)</\1>)""", RegexOption.DOT_MATCHES_ALL)
    private val latAttr = Regex("""\blat\s*=\s*["']([-0-9.]+)["']""")
    private val lonAttr = Regex("""\blon\s*=\s*["']([-0-9.]+)["']""")
    private val ele = Regex("""<ele>\s*([-0-9.]+)\s*</ele>""")
    private val time = Regex("""<time>\s*([^<\s]+)\s*</time>""")

    private fun millis(s: String): Long? =
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()

    fun parse(text: String): List<TrackPoint> {
        val all = point.findAll(text).mapNotNull { m ->
            val attrs = m.groupValues[2]
            val lat = latAttr.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = lonAttr.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val inner = m.groupValues[4]
            Triple(
                m.groupValues[1],
                TrackPoint(
                    LatLon(lat, lon),
                    ele.find(inner)?.groupValues?.get(1)?.toDoubleOrNull(),
                    time.find(inner)?.groupValues?.get(1)?.let { millis(it) },
                ),
                Unit,
            )
        }.toList()
        // Αν το αρχείο έχει και ίχνος και «route», κρατάμε το ίχνος (πιο λεπτομερές).
        val trk = all.filter { it.first == "trkpt" }
        return (if (trk.size >= 2) trk else all).map { it.second }
    }

    /** Σύνοψη από GPX (για διαδρομές χωρίς .json, π.χ. αν η εφαρμογή έκλεισε απότομα). */
    fun summarize(id: String, pts: List<TrackPoint>): TripSummary {
        val s = TripStats()
        var prev: TrackPoint? = null
        pts.forEach { p ->
            val t = p.timeMs ?: 0L
            val speed = prev?.let { q ->
                val dt = ((t - (q.timeMs ?: t)) / 1000.0)
                if (dt > 0) Geo.distance(q.position, p.position) / dt * 3.6 else null
            }
            s.add(p.position.lat, p.position.lon, t, speed, p.ele)
            prev = p
        }
        return s.summary(id)
    }
}

/** Τα αρχεία των διαδρομών στο κινητό. */
class TripRepo(context: Context) {
    private val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "trips")

    fun list(): List<TripSummary> {
        val gpx = dir.listFiles { f -> f.extension == "gpx" }?.toList() ?: return emptyList()
        return gpx.mapNotNull { g ->
            val j = File(dir, g.nameWithoutExtension + ".json")
            (if (j.exists()) TripSummary.fromJson(j.readText()) else null)
                ?: runCatching {
                    val pts = Gpx.parse(g.readText())
                    if (pts.size < 2) null else Gpx.summarize(g.nameWithoutExtension, pts).also {
                        runCatching { j.writeText(TripSummary.toJson(it)) }
                    }
                }.getOrNull()
        }.sortedByDescending { it.startMs }
    }

    fun track(id: String): List<TrackPoint> =
        runCatching { Gpx.parse(File(dir, "$id.gpx").readText()) }.getOrDefault(emptyList())

    fun file(id: String): File = File(dir, "$id.gpx")

    fun delete(id: String) {
        File(dir, "$id.gpx").delete()
        File(dir, "$id.json").delete()
    }
}
