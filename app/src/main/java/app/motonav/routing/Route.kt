package app.motonav.routing

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import org.json.JSONArray
import org.json.JSONObject
import app.motonav.i18n.tr

enum class Turn {
    STRAIGHT, LEFT, SLIGHT_LEFT, SHARP_LEFT, RIGHT, SLIGHT_RIGHT, SHARP_RIGHT,
    KEEP_LEFT, KEEP_RIGHT, UTURN, ROUNDABOUT, ROUNDABOUT_LEFT, EXIT_LEFT, EXIT_RIGHT, ARRIVE,
}

/** Μία κίνηση πάνω στη διαδρομή. atMeters: απόσταση από την αρχή. */
data class Maneuver(
    val pointIndex: Int,
    val turn: Turn,
    val exit: Int,
    val angle: Int,
    val atMeters: Double,
    /** Ο δρόμος όπου μπαίνεις. Στο BRouter συμπληρώνεται αργότερα από το OpenStreetMap (θέλει internet). */
    var street: String? = null,
    /** Αριθμός εξόδου αυτοκινητόδρομου και τι γράφει η πινακίδα (μόνο TomTom). */
    val exitNumber: String? = null,
    val signpost: String? = null,
)

/** Τι δρόμος είναι (για το χρώμα της ταμπέλας). */
enum class RoadKind { MOTORWAY, MAIN, DIRT, OTHER }

/** Μία λωρίδα: πού πάει (STRAIGHT, LEFT, SLIGHT_RIGHT…) και, αν είναι η σωστή για εσένα, το follow. */
data class Lane(val directions: List<String>, val follow: String?)

/** Λωρίδες σε ένα κομμάτι της διαδρομής (από αριστερά προς τα δεξιά). */
data class LaneSection(val startM: Double, val endM: Double, val lanes: List<Lane>)

class Route(
    val points: List<LatLon>,
    val elevations: List<Double?>,
    /** cumulative[i] = μέτρα από την αρχή ως το σημείο i. */
    val cumulative: DoubleArray,
    val maneuvers: List<Maneuver>,
    val durationS: Double,
    /** Καθυστέρηση από κίνηση που περιέχεται ήδη στο durationS (μόνο από TomTom). */
    val trafficDelayS: Double = 0.0,
    val source: String = "BRouter",
    /** Όριο ταχύτητας ανά κομμάτι: (μέτρα από την αρχή όπου τελειώνει το κομμάτι, όριο ή null). */
    val speedLimits: List<Pair<Double, Int?>> = emptyList(),
    val lanes: List<LaneSection> = emptyList(),
    /** (μέτρα ως το τέλος του κομματιού, είδος δρόμου). */
    val roadKinds: List<Pair<Double, RoadKind>> = emptyList(),
    /** Κίνηση πάνω στη διαδρομή, όταν ήρθε μαζί της (TomTom). */
    val trafficJams: List<app.motonav.traffic.RouteJam> = emptyList(),
) {
    fun roadKindAt(m: Double): RoadKind = roadKinds.firstOrNull { it.first >= m }?.second ?: RoadKind.OTHER

    /** Οι λωρίδες στο σημείο m, μόνο αν ξέρουμε ποια είναι η σωστή. */
    fun lanesAt(m: Double): List<Lane>? =
        lanes.firstOrNull { m >= it.startM - 5 && m <= it.endM }?.lanes?.takeIf { l -> l.size > 1 && l.any { it.follow != null } }

    /** Το σημείο σε απόσταση m από την αρχή. */
    fun pointAt(m: Double): LatLon {
        if (points.isEmpty()) return LatLon(0.0, 0.0)
        if (m <= 0.0) return points.first()
        if (m >= lengthM) return points.last()
        var i = cumulative.indexOfFirst { it >= m }
        if (i <= 0) i = 1
        val a = points[i - 1]
        val b = points[i]
        val seg = cumulative[i] - cumulative[i - 1]
        val t = if (seg > 0) (m - cumulative[i - 1]) / seg else 0.0
        return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    val lengthM: Double get() = cumulative.lastOrNull() ?: 0.0

    /** Το κομμάτι της γραμμής από fromM ως toM (μέτρα από την αρχή). */
    fun slice(fromM: Double, toM: Double): List<LatLon> {
        if (points.size < 2 || toM <= fromM) return emptyList()
        val out = ArrayList<LatLon>()
        out += pointAt(fromM)
        for (i in points.indices) if (cumulative[i] > fromM && cumulative[i] < toM) out += points[i]
        out += pointAt(toM)
        return out
    }

    /** Μέτρα από την αρχή ως το σημείο της διαδρομής που είναι πιο κοντά στο p (ψάχνει από το fromIndex και μετά). */
    fun progressNear(p: LatLon, fromIndex: Int = 0): Pair<Int, Double> {
        var best = fromIndex.coerceIn(0, points.lastIndex.coerceAtLeast(0))
        var bestD = Double.MAX_VALUE
        for (i in best..points.lastIndex) {
            val d = Geo.distance(points[i], p)
            if (d < bestD) { bestD = d; best = i }
        }
        return best to (cumulative.getOrNull(best) ?: 0.0)
    }

    /** Όριο ταχύτητας σε km/h στο σημείο m της διαδρομής, αν το ξέρει το OpenStreetMap. */
    fun speedLimitAt(m: Double): Int? = speedLimits.firstOrNull { it.first >= m }?.second

    /** Μοίρες αλλαγής κατεύθυνσης ανά χιλιόμετρο: όσο πιο πολλές, τόσο πιο στριφογυριστή. */
    val twistinessDegPerKm: Double by lazy { Twistiness.degPerKm(points) }

    /** 1 (ευθεία) έως 5 (πολλές στροφές). */
    val twistinessStars: Int get() = Twistiness.stars(twistinessDegPerKm)

    /** Υψόμετρο σε απόσταση m από την αρχή (από το προφίλ της διαδρομής), ή null αν δεν υπάρχει. */
    fun elevationAt(m: Double): Double? {
        if (points.size < 2) return null
        var i = cumulative.indexOfFirst { it >= m }
        if (i < 0) i = points.lastIndex
        // κοντινότερα σημεία με υψόμετρο, πριν και μετά
        var a = i
        while (a >= 0 && elevations[a] == null) a--
        var b = i
        while (b <= points.lastIndex && elevations[b] == null) b++
        val ea = if (a >= 0) elevations[a] else null
        val eb = if (b <= points.lastIndex) elevations[b] else null
        return when {
            ea != null && eb != null && b != a -> {
                val t = ((m - cumulative[a]) / (cumulative[b] - cumulative[a])).coerceIn(0.0, 1.0)
                ea + (eb - ea) * t
            }
            ea != null -> ea
            else -> eb
        }
    }

    /** Κλίση δρόμου σε % γύρω από το m (±half μέτρα). Θετικό = ανηφόρα. */
    fun gradeAt(m: Double, half: Double = 50.0): Double? {
        val from = (m - half).coerceAtLeast(0.0)
        val to = (m + half).coerceAtMost(lengthM)
        val d = to - from
        if (d < 20) return null
        val a = elevationAt(from) ?: return null
        val b = elevationAt(to) ?: return null
        return (b - a) / d * 100.0
    }
}

object Twistiness {
    /**
     * Ξαναδειγματίζει τη γραμμή ανά ~25 m (για να μη μετράει θόρυβο από πυκνά σημεία)
     * και αθροίζει την απόλυτη αλλαγή κατεύθυνσης. Οι αναστροφές > 150° δεν μετράνε (λάθη, U-turn).
     */
    fun degPerKm(points: List<LatLon>, step: Double = 25.0): Double {
        if (points.size < 3) return 0.0
        val sampled = ArrayList<LatLon>()
        sampled += points.first()
        var acc = 0.0
        for (i in 1 until points.size) {
            acc += Geo.distance(points[i - 1], points[i])
            if (acc >= step) {
                sampled += points[i]
                acc = 0.0
            }
        }
        if (sampled.last() != points.last()) sampled += points.last()
        if (sampled.size < 3) return 0.0

        var turn = 0.0
        var length = 0.0
        var prevBearing = Geo.bearing(sampled[0], sampled[1])
        length += Geo.distance(sampled[0], sampled[1])
        for (i in 1 until sampled.size - 1) {
            val b = Geo.bearing(sampled[i], sampled[i + 1])
            var d = kotlin.math.abs(b - prevBearing)
            if (d > 180) d = 360 - d
            if (d < 150) turn += d
            prevBearing = b
            length += Geo.distance(sampled[i], sampled[i + 1])
        }
        return if (length < 1.0) 0.0 else turn / (length / 1000.0)
    }

    fun stars(degPerKm: Double): Int = when {
        degPerKm < 60 -> 1
        degPerKm < 120 -> 2
        degPerKm < 200 -> 3
        degPerKm < 300 -> 4
        else -> 5
    }
}

class RoutingException(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { NOT_INSTALLED, NO_DATA, NO_PROFILE, NO_FIX, TRAFFIC, OTHER }
}

/**
 * Διαβάζει το GeoJSON του BRouter (trackFormat=json).
 * voicehints: [indexInTrack, κωδικός, έξοδος κόμβου, απόσταση ως την επόμενη, γωνία]
 * Κωδικοί από btools.router.Formatter.getJsonCommandIndex.
 */
object BRouterJson {

    private fun turnFor(code: Int): Turn? = when (code) {
        1 -> Turn.STRAIGHT
        2 -> Turn.LEFT
        3 -> Turn.SLIGHT_LEFT
        4 -> Turn.SHARP_LEFT
        5 -> Turn.RIGHT
        6 -> Turn.SLIGHT_RIGHT
        7 -> Turn.SHARP_RIGHT
        8 -> Turn.KEEP_LEFT
        9 -> Turn.KEEP_RIGHT
        10, 11, 15 -> Turn.UTURN
        13 -> Turn.ROUNDABOUT
        14 -> Turn.ROUNDABOUT_LEFT
        17 -> Turn.EXIT_LEFT
        18 -> Turn.EXIT_RIGHT
        else -> null // 12 εκτός διαδρομής, 16 ευθεία γραμμή: δεν τα λέμε
    }

    fun parse(json: String): Route {
        val root = JSONObject(json)
        val feature = root.getJSONArray("features").getJSONObject(0)
        val props = feature.optJSONObject("properties") ?: JSONObject()
        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")

        val points = ArrayList<LatLon>(coords.length())
        val elev = ArrayList<Double?>(coords.length())
        for (i in 0 until coords.length()) {
            val c: JSONArray = coords.getJSONArray(i)
            points += LatLon(lat = c.getDouble(1), lon = c.getDouble(0))
            elev += if (c.length() > 2) c.optDouble(2).takeIf { !it.isNaN() } else null
        }
        if (points.size < 2) throw RoutingException(tr("Η διαδρομή βγήκε άδεια."), RoutingException.Kind.OTHER)

        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + Geo.distance(points[i - 1], points[i])
        }

        val maneuvers = ArrayList<Maneuver>()
        props.optJSONArray("voicehints")?.let { hints ->
            for (i in 0 until hints.length()) {
                val h = hints.optJSONArray(i) ?: continue
                val index = h.optInt(0, -1)
                val turn = turnFor(h.optInt(1, 0)) ?: continue
                if (index !in points.indices) continue
                maneuvers += Maneuver(
                    pointIndex = index,
                    turn = turn,
                    exit = h.optInt(2, 0),
                    angle = h.optInt(4, 0),
                    atMeters = cumulative[index],
                )
            }
        }
        maneuvers.sortBy { it.atMeters }
        maneuvers += Maneuver(points.lastIndex, Turn.ARRIVE, 0, 0, cumulative.last())

        val totalTime = props.optString("total-time").toDoubleOrNull()
            ?: (cumulative.last() / (60.0 / 3.6)) // αν λείπει: 60 km/h μέσος όρος
        val limits = parseSpeedLimits(props.optJSONArray("messages"))
        return Route(points, elev, cumulative, maneuvers, totalTime, speedLimits = limits, roadKinds = parseRoadKinds(props.optJSONArray("messages")))
    }

    /**
     * Τα «messages» του BRouter: μία γραμμή ανά κομμάτι δρόμου με ίδια χαρακτηριστικά.
     * Distance = μήκος του κομματιού, WayTags = στοιχεία OSM του δρόμου (π.χ. «highway=primary maxspeed=50»).
     */
    fun parseSpeedLimits(messages: JSONArray?): List<Pair<Double, Int?>> {
        if (messages == null || messages.length() < 2) return emptyList()
        val header = messages.optJSONArray(0) ?: return emptyList()
        var iDist = -1
        var iTags = -1
        for (k in 0 until header.length()) {
            when (header.optString(k)) {
                "Distance" -> iDist = k
                "WayTags" -> iTags = k
            }
        }
        if (iDist < 0 || iTags < 0) return emptyList()
        val out = ArrayList<Pair<Double, Int?>>()
        var end = 0.0
        for (i in 1 until messages.length()) {
            val row = messages.optJSONArray(i) ?: continue
            end += row.optString(iDist).toDoubleOrNull() ?: 0.0
            out += end to maxspeedFrom(row.optString(iTags))
        }
        return out
    }

    /** Είδος δρόμου ανά κομμάτι, από τα WayTags (highway=…, surface=…). */
    fun parseRoadKinds(messages: JSONArray?): List<Pair<Double, RoadKind>> {
        if (messages == null || messages.length() < 2) return emptyList()
        val header = messages.optJSONArray(0) ?: return emptyList()
        var iDist = -1
        var iTags = -1
        for (k in 0 until header.length()) {
            when (header.optString(k)) {
                "Distance" -> iDist = k
                "WayTags" -> iTags = k
            }
        }
        if (iDist < 0 || iTags < 0) return emptyList()
        val out = ArrayList<Pair<Double, RoadKind>>()
        var end = 0.0
        for (i in 1 until messages.length()) {
            val row = messages.optJSONArray(i) ?: continue
            end += row.optString(iDist).toDoubleOrNull() ?: 0.0
            out += end to roadKindFrom(row.optString(iTags))
        }
        return out
    }

    private val highwayRe = Regex("(?:^|\\s)highway=([a-z_]+)")
    private val surfaceRe = Regex("(?:^|\\s)surface=([a-z_]+)")
    private val unpaved = setOf("unpaved", "gravel", "fine_gravel", "compacted", "dirt", "ground", "earth", "grass", "sand", "mud", "pebblestone", "rock")

    fun roadKindFrom(wayTags: String): RoadKind {
        val hw = highwayRe.find(wayTags)?.groupValues?.get(1).orEmpty()
        val sf = surfaceRe.find(wayTags)?.groupValues?.get(1).orEmpty()
        return when {
            hw == "track" || sf in unpaved -> RoadKind.DIRT
            hw == "motorway" || hw == "motorway_link" -> RoadKind.MOTORWAY
            hw in setOf("trunk", "trunk_link", "primary", "primary_link") -> RoadKind.MAIN
            else -> RoadKind.OTHER
        }
    }

    private val maxspeedRe = Regex("(?:^|\\s)maxspeed=(\\d+)")

    fun maxspeedFrom(wayTags: String): Int? = maxspeedRe.find(wayTags)?.groupValues?.get(1)?.toIntOrNull()
}
