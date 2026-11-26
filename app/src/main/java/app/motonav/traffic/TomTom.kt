package app.motonav.traffic

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.routing.Maneuver
import app.motonav.routing.Route
import app.motonav.routing.RoutingException
import app.motonav.routing.Turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Χρόνος για μια διαδρομή με την τωρινή κίνηση. */
data class TrafficEta(val durationS: Double, val delayS: Double, val jams: List<TrafficJam> = emptyList())

/** Κομμάτι με κίνηση πάνω στη διαδρομή της TomTom: 1 = αργά, 2 = πολύ αργά, 3 = σταματημένα/κλειστό. */
data class TrafficJam(val points: List<LatLon>, val level: Int)

/**
 * TomTom (κλειδί του χρήστη, δωρεάν πακέτο). Μόνο με internet.
 * - route(): η πιο γρήγορη διαδρομή με την τωρινή κίνηση, με οδηγίες στροφών.
 * - eta(): χρόνος με κίνηση για μια διαδρομή που ήδη έχουμε (π.χ. από το BRouter).
 * - flowTileUrl(): πλακίδια χάρτη με χρώματα κίνησης.
 */
class TomTomClient(private val key: String) {
    /** «Αυτοκίνητο» στις Ρυθμίσεις: travelMode=car αντί για motorcycle. */
    private val mode get() = if (app.motonav.routing.Vehicle.car) "car" else "motorcycle"


    suspend fun route(
        from: LatLon,
        to: LatLon,
        headingDeg: Float?,
        avoidTolls: Boolean = false,
        avoidMotorways: Boolean = false,
        via: List<LatLon> = emptyList(),
    ): Route = routes(from, to, headingDeg, avoidTolls, avoidMotorways, via, 0).first()

    /** Η κύρια και έως `alternatives` εναλλακτικές (μόνο χωρίς ενδιάμεσα σημεία, έτσι δουλεύει το TomTom). */
    suspend fun routes(
        from: LatLon,
        to: LatLon,
        headingDeg: Float?,
        avoidTolls: Boolean = false,
        avoidMotorways: Boolean = false,
        via: List<LatLon> = emptyList(),
        alternatives: Int = 0,
    ): List<Route> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://api.tomtom.com/routing/1/calculateRoute/")
            append((listOf(from) + via + to).joinToString(":") { loc(it) })
            append("/json?key=").append(enc(key))
            append("&traffic=true&routeType=fastest&travelMode=$mode")
            append("&instructionsType=coded&language=el-GR&computeTravelTimeFor=all&sectionType=lanes&sectionType=motorway&sectionType=unpaved&sectionType=traffic")
            if (headingDeg != null) append("&vehicleHeading=").append(headingDeg.toInt().coerceIn(0, 359))
            if (avoidTolls) append("&avoid=tollRoads")
            if (avoidMotorways) append("&avoid=motorways")
            if (app.motonav.routing.Avoid.ferries) append("&avoid=ferries")
            if (app.motonav.routing.Avoid.unpaved) append("&avoid=unpavedRoads")
            if (alternatives > 0 && via.isEmpty()) append("&maxAlternatives=").append(alternatives)
        }
        TomTomParser.routes(request(url, null))
    }

    suspend fun eta(points: List<LatLon>): TrafficEta = withContext(Dispatchers.IO) {
        require(points.size >= 2)
        val support = TomTomParser.downsample(points)
        val url = buildString {
            append("https://api.tomtom.com/routing/1/calculateRoute/")
            append(loc(support.first())).append(':').append(loc(support.last()))
            append("/json?key=").append(enc(key))
            append("&traffic=true&travelMode=$mode&computeTravelTimeFor=all&routeRepresentation=polyline&sectionType=traffic")
        }
        val body = JSONObject().put(
            "supportingPoints",
            JSONArray().apply {
                support.forEach { put(JSONObject().put("latitude", it.lat).put("longitude", it.lon)) }
            },
        ).toString()
        TomTomParser.eta(request(url, body))
    }

    private fun loc(p: LatLon) = String.format(Locale.US, "%.6f,%.6f", p.lat, p.lon)

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    private fun request(url: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code == 401 || code == 403) {
                throw RoutingException("Το κλειδί TomTom δεν δέχτηκε (HTTP $code). Έλεγξέ το στις Ρυθμίσεις.", RoutingException.Kind.TRAFFIC)
            }
            if (code == 429) {
                throw RoutingException("Τέλος οι δωρεάν κλήσεις TomTom για σήμερα.", RoutingException.Kind.TRAFFIC)
            }
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw IOException("TomTom HTTP $code ${err?.take(200) ?: ""}")
            }
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** Διανυσματικά πλακίδια ροής (pbf). Τα στυλ/χρώματα τα ορίζουμε εμείς στον χάρτη. */
        fun flowVectorTileUrl(key: String): String =
            "https://api.tomtom.com/traffic/map/4/tile/flow/relative/{z}/{x}/{y}.pbf?trafficLevelStep=0.05&key=" +
                URLEncoder.encode(key.trim(), "UTF-8")

        /**
         * Πλακίδια ροής, στυλ «relative-delay»: μόνο οι δρόμοι που κινούνται πιο αργά από το κανονικό
         * (πορτοκαλί, κόκκινο). Οι ελεύθεροι δρόμοι (πράσινοι) δεν ζωγραφίζονται, για καθαρό χάρτη.
         */
        @Suppress("UNUSED_PARAMETER")
        fun flowTileUrl(key: String, dark: Boolean): String =
            "https://api.tomtom.com/traffic/map/4/tile/flow/relative-delay" +
                "/{z}/{x}/{y}.png?tileSize=256&key=" + URLEncoder.encode(key.trim(), "UTF-8")
    }
}

object TomTomParser {

    /** Ένα σημείο ανά ~150 m, το πολύ 400. Αρκεί για να ακολουθήσει το TomTom την ίδια διαδρομή. */
    fun downsample(points: List<LatLon>, stepM: Double = 150.0, max: Int = 400): List<LatLon> {
        if (points.size <= 2) return points
        var total = 0.0
        for (i in 1 until points.size) total += Geo.distance(points[i - 1], points[i])
        val step = maxOf(stepM, total / (max - 1))
        val out = ArrayList<LatLon>()
        out += points.first()
        var acc = 0.0
        for (i in 1 until points.size - 1) {
            acc += Geo.distance(points[i - 1], points[i])
            if (acc >= step) {
                out += points[i]
                acc = 0.0
            }
        }
        out += points.last()
        return out
    }

    fun turnFor(maneuver: String): Turn? = when (maneuver) {
        "TURN_LEFT" -> Turn.LEFT
        "TURN_RIGHT" -> Turn.RIGHT
        "BEAR_LEFT" -> Turn.SLIGHT_LEFT
        "BEAR_RIGHT" -> Turn.SLIGHT_RIGHT
        "SHARP_LEFT" -> Turn.SHARP_LEFT
        "SHARP_RIGHT" -> Turn.SHARP_RIGHT
        "KEEP_LEFT" -> Turn.KEEP_LEFT
        "KEEP_RIGHT" -> Turn.KEEP_RIGHT
        "MAKE_UTURN", "TRY_MAKE_UTURN" -> Turn.UTURN
        "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT", "ROUNDABOUT_CROSS", "ROUNDABOUT_BACK" -> Turn.ROUNDABOUT
        "MOTORWAY_EXIT_LEFT" -> Turn.EXIT_LEFT
        "MOTORWAY_EXIT_RIGHT", "TAKE_EXIT" -> Turn.EXIT_RIGHT
        "STRAIGHT" -> Turn.STRAIGHT
        "DEPART", "ARRIVE", "ARRIVE_LEFT", "ARRIVE_RIGHT", "FOLLOW", "WAYPOINT_REACHED" -> null
        else -> when {
            "LEFT" in maneuver -> Turn.KEEP_LEFT
            "RIGHT" in maneuver -> Turn.KEEP_RIGHT
            else -> null
        }
    }

    fun route(json: String): Route = routes(json).first()

    fun routes(json: String): List<Route> {
        val arr = JSONObject(json).optJSONArray("routes")
        if (arr == null || arr.length() == 0) throw RoutingException("Το TomTom δεν βρήκε διαδρομή.", RoutingException.Kind.OTHER)
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { r -> runCatching { one(r) }.getOrNull() } }
            .ifEmpty { throw RoutingException("Η διαδρομή TomTom βγήκε άδεια.", RoutingException.Kind.OTHER) }
    }

    private fun one(r: JSONObject): Route {
        val summary = r.optJSONObject("summary") ?: JSONObject()

        val points = ArrayList<LatLon>()
        val legs = r.optJSONArray("legs") ?: JSONArray()
        for (l in 0 until legs.length()) {
            val pts = legs.optJSONObject(l)?.optJSONArray("points") ?: continue
            for (i in 0 until pts.length()) {
                val p = pts.optJSONObject(i) ?: continue
                val ll = LatLon(p.optDouble("latitude"), p.optDouble("longitude"))
                if (points.isEmpty() || points.last() != ll) points += ll
            }
        }
        if (points.size < 2) throw RoutingException("Η διαδρομή TomTom βγήκε άδεια.", RoutingException.Kind.OTHER)

        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + Geo.distance(points[i - 1], points[i])

        val maneuvers = ArrayList<Maneuver>()
        val instr = r.optJSONObject("guidance")?.optJSONArray("instructions") ?: JSONArray()
        for (i in 0 until instr.length()) {
            val o = instr.optJSONObject(i) ?: continue
            val turn = turnFor(o.optString("maneuver")) ?: continue
            val atM = if (o.has("routeOffsetInMeters")) o.optDouble("routeOffsetInMeters") else Double.NaN
            val idx = if (o.has("pointIndex")) o.optInt("pointIndex").coerceIn(0, points.lastIndex)
            else nearestIndex(cumulative, atM)
            maneuvers += Maneuver(
                pointIndex = idx,
                turn = turn,
                exit = o.optInt("roundaboutExitNumber", 0),
                angle = 0,
                atMeters = if (atM.isNaN()) cumulative[idx] else atM,
                street = o.optString("street").ifBlank {
                    o.optJSONArray("roadNumbers")?.optString(0).orEmpty()
                }.ifBlank { null },
                exitNumber = o.optString("exitNumber").ifBlank { null },
                signpost = o.optString("signpostText").ifBlank { null },
            )
        }
        val laneSections = ArrayList<app.motonav.routing.LaneSection>()
        val sections = r.optJSONArray("sections") ?: JSONArray()
        // Αυτοκινητόδρομοι και χωματόδρομοι (για το χρώμα της ταμπέλας). Τα υπόλοιπα μένουν «κανονικά».
        val kinds = ArrayList<Triple<Double, Double, app.motonav.routing.RoadKind>>()
        for (i in 0 until sections.length()) {
            val o = sections.optJSONObject(i) ?: continue
            val kind = when (o.optString("sectionType")) {
                "MOTORWAY" -> app.motonav.routing.RoadKind.MOTORWAY
                "UNPAVED" -> app.motonav.routing.RoadKind.DIRT
                else -> null
            } ?: continue
            val a = o.optInt("startPointIndex").coerceIn(0, points.lastIndex)
            val b = o.optInt("endPointIndex").coerceIn(0, points.lastIndex)
            kinds += Triple(cumulative[a], cumulative[b], kind)
        }
        // Εθνικές/κύριες: όσα κομμάτια έχουν αριθμό δρόμου στην οδηγία (π.χ. «1», «8Α», «Ε65») → μπλε ταμπέλα,
        // όπως στις διαδρομές BRouter. Αυτοκινητόδρομος και χώμα έχουν προτεραιότητα.
        val numbered = ArrayList<Pair<Double, Double>>()
        val instrAt = (0 until instr.length()).mapNotNull { i ->
            val o = instr.optJSONObject(i) ?: return@mapNotNull null
            val m = if (o.has("routeOffsetInMeters")) o.optDouble("routeOffsetInMeters")
            else if (o.has("pointIndex")) cumulative[o.optInt("pointIndex").coerceIn(0, points.lastIndex)] else return@mapNotNull null
            m to ((o.optJSONArray("roadNumbers")?.length() ?: 0) > 0)
        }.sortedBy { it.first }
        instrAt.forEachIndexed { i, (m, hasNumber) ->
            if (hasNumber) numbered += m to (instrAt.getOrNull(i + 1)?.first ?: cumulative.last())
        }
        fun kindAt(m: Double): app.motonav.routing.RoadKind =
            kinds.firstOrNull { m >= it.first && m <= it.second }?.third
                ?: if (numbered.any { m >= it.first && m <= it.second }) app.motonav.routing.RoadKind.MAIN
                else app.motonav.routing.RoadKind.OTHER
        val cuts = (listOf(0.0, cumulative.last()) + kinds.flatMap { listOf(it.first, it.second) } + numbered.flatMap { listOf(it.first, it.second) })
            .filter { it in 0.0..cumulative.last() }.distinct().sorted()
        val roadKinds = ArrayList<Pair<Double, app.motonav.routing.RoadKind>>()
        for (i in 1 until cuts.size) {
            val k = kindAt((cuts[i - 1] + cuts[i]) / 2)
            if (roadKinds.isNotEmpty() && roadKinds.last().second == k) roadKinds[roadKinds.lastIndex] = cuts[i] to k
            else roadKinds += cuts[i] to k
        }
        for (i in 0 until sections.length()) {
            val o = sections.optJSONObject(i) ?: continue
            if (o.optString("sectionType") != "LANES") continue
            val arr = o.optJSONArray("lanes") ?: continue
            val lanes = (0 until arr.length()).mapNotNull { k ->
                val l = arr.optJSONObject(k) ?: return@mapNotNull null
                val dirs = l.optJSONArray("directions")?.let { d -> (0 until d.length()).map { d.optString(it) } } ?: emptyList()
                app.motonav.routing.Lane(dirs, l.optString("follow").ifBlank { null })
            }
            val a = o.optInt("startPointIndex").coerceIn(0, points.lastIndex)
            val b = o.optInt("endPointIndex").coerceIn(0, points.lastIndex)
            laneSections += app.motonav.routing.LaneSection(cumulative[a], cumulative[b], lanes)
        }
        // Κίνηση: έρχεται μαζί με τη διαδρομή, χωρίς δεύτερη κλήση.
        val jams = ArrayList<RouteJam>()
        for (i in 0 until sections.length()) {
            val o = sections.optJSONObject(i) ?: continue
            if (o.optString("sectionType") != "TRAFFIC") continue
            val level = jamLevel(o.optInt("magnitudeOfDelay", 0), o.optString("simpleCategory"))
            if (level == 0) continue
            val a = o.optInt("startPointIndex").coerceIn(0, points.lastIndex)
            val b = o.optInt("endPointIndex").coerceIn(0, points.lastIndex)
            if (cumulative[b] - cumulative[a] >= 5.0) jams += RouteJam(cumulative[a], cumulative[b], level)
        }
        maneuvers.sortBy { it.atMeters }
        maneuvers += Maneuver(points.lastIndex, Turn.ARRIVE, 0, 0, cumulative.last())

        val duration = summary.optDouble("travelTimeInSeconds", cumulative.last() / (50.0 / 3.6))
        return Route(
            points = points,
            elevations = points.map { null },
            cumulative = cumulative,
            maneuvers = maneuvers,
            durationS = duration,
            trafficDelayS = summary.optDouble("trafficDelayInSeconds", 0.0),
            source = "TomTom",
            lanes = laneSections,
            roadKinds = roadKinds,
            trafficJams = jams,
        )
    }

    fun eta(json: String): TrafficEta {
        val s = JSONObject(json).optJSONArray("routes")?.optJSONObject(0)?.optJSONObject("summary")
            ?: throw IOException("TomTom: χωρίς summary")
        return TrafficEta(
            durationS = s.optDouble("travelTimeInSeconds"),
            delayS = s.optDouble("trafficDelayInSeconds", 0.0),
            jams = jams(json),
        )
    }

    /** Τμήματα TRAFFIC της απάντησης, με τα σημεία τους από τη γραμμή της TomTom. */
    fun jams(json: String): List<TrafficJam> {
        val r = JSONObject(json).optJSONArray("routes")?.optJSONObject(0) ?: return emptyList()
        val pts = ArrayList<LatLon>()
        val legs = r.optJSONArray("legs") ?: JSONArray()
        for (i in 0 until legs.length()) {
            val lp = legs.optJSONObject(i)?.optJSONArray("points") ?: continue
            for (j in 0 until lp.length()) {
                val o = lp.optJSONObject(j) ?: continue
                pts += LatLon(o.optDouble("latitude"), o.optDouble("longitude"))
            }
        }
        val sections = r.optJSONArray("sections") ?: return emptyList()
        val out = ArrayList<TrafficJam>()
        for (i in 0 until sections.length()) {
            val o = sections.optJSONObject(i) ?: continue
            if (o.optString("sectionType") != "TRAFFIC") continue
            val a = o.optInt("startPointIndex", -1)
            val b = o.optInt("endPointIndex", -1)
            if (a < 0 || b <= a || b > pts.lastIndex) continue
            val level = jamLevel(o.optInt("magnitudeOfDelay", 0), o.optString("simpleCategory"))
            if (level > 0) out += TrafficJam(pts.subList(a, b + 1).toList(), level)
        }
        return out
    }

    fun jamLevel(magnitude: Int, category: String): Int = when {
        category == "ROAD_CLOSURE" -> 3
        magnitude >= 3 -> 3
        magnitude == 2 -> 2
        magnitude == 1 -> 1
        category == "JAM" -> 1
        else -> 0
    }

    private fun nearestIndex(cum: DoubleArray, atM: Double): Int {
        if (atM.isNaN()) return 0
        var best = 0
        for (i in cum.indices) if (kotlin.math.abs(cum[i] - atM) < kotlin.math.abs(cum[best] - atM)) best = i
        return best
    }
}
