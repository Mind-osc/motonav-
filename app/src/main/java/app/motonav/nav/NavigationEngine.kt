package app.motonav.nav

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.routing.Maneuver
import app.motonav.routing.Route
import kotlin.math.max
import kotlin.math.min

data class NavState(
    val progressM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val next: Maneuver?,
    val distToNextM: Double,
    /** Δεύτερη κίνηση αν έρχεται αμέσως μετά (< 150 m). */
    val then: Maneuver?,
    val offRoute: Boolean,
    val arrived: Boolean,
    /** Το τμήμα της διαδρομής όπου βρίσκεσαι και το σημείο σου πάνω στη γραμμή (για να «σβήνει» ό,τι πέρασες). */
    val segIndex: Int = 0,
    val snapped: LatLon? = null,
    /** Αυτό το στίγμα είναι πάνω στη διαδρομή (για να «κολλήσει» το βελάκι στη μωβ γραμμή). */
    val onRoute: Boolean = false,
    /** Η κίνηση μετά την επόμενη, όσο μακριά κι αν είναι (για τη λωρίδα «Έπειτα μετά από …» της ταμπέλας). */
    val after: Maneuver? = null,
)

/**
 * Βρίσκει πού είσαι πάνω στη διαδρομή σε κάθε στίγμα GPS.
 * Ψάχνει μόνο λίγο πίσω και αρκετά μπροστά από την προηγούμενη θέση, για να μην «πηδάει»
 * σε κομμάτι της διαδρομής που περνάει δίπλα (φουρκέτες, ίδιος δρόμος επιστροφής).
 */
class NavigationEngine(val route: Route) {
    private var segIndex = 0
    private var progress = 0.0
    private var offCount = 0
    private var snapped: LatLon? = null

    private var offNeeded = OFF_ROUTE_FIXES

    /**
     * bearingDeg/speedMps: πού πας (GPS). Αν πας αντίθετα ή κάθετα στη διαδρομή, βγαίνεις πιο γρήγορα «εκτός»·
     * αν πας παράλληλα και κοντά (GPS που πετάγεται ανάμεσα σε πολυκατοικίες), περιμένει λίγο περισσότερο.
     */
    fun update(p: LatLon, accuracyM: Float, bearingDeg: Float? = null, speedMps: Float = 0f): NavState {
        val pts = route.points
        val cum = route.cumulative
        val lastSeg = pts.size - 2

        val from = max(0, segIndex - 3)
        val to = min(lastSeg, segIndex + if (offCount > 0) 400 else 120)
        var bestD = Double.MAX_VALUE
        var bestI = segIndex
        var bestT = 0.0
        for (i in from..to) {
            val (t, d) = Geo.project(p, pts[i], pts[i + 1])
            if (d < bestD) {
                bestD = d; bestI = i; bestT = t
            }
        }

        // Πιο σφιχτά από πριν (35 m / 3 στίγματα): στα στενά της πόλης η γειτονική οδός είναι 30–40 m μακριά.
        val tolerance = max(25.0, accuracyM * 1.2)
        // Μπήκες σε άλλο δρόμο (π.χ. στενό δεξιά): η κατεύθυνσή σου δεν ταιριάζει με κανένα κομμάτι της διαδρομής
        // εκεί κοντά και έχεις ήδη φύγει ≥ 15 m. Το πιάνουμε πριν φτάσεις στο όριο απόστασης (όπως κάνουν
        // Google/TomTom με το «σε ποιον δρόμο είσαι», όχι μόνο «πόσο μακριά από τη γραμμή»).
        val turnedOff = bearingDeg != null && speedMps > 3f && bestD > TURN_OFF_M && bestD <= tolerance &&
            !headingFitsNearby(bestI, bearingDeg.toDouble())
        if (bestD > tolerance || turnedOff) {
            offCount++
            // Πόσα στίγματα χρειάζονται: 2 αν είσαι πολύ μακριά ή πας αλλού, 4 αν πας παράλληλα (μάλλον GPS).
            val wrongWay = bearingDeg != null && speedMps > 3f && !headingFitsNearby(bestI, bearingDeg.toDouble())
            offNeeded = if (bestD > FAR_M || wrongWay) OFF_ROUTE_FIXES else OFF_ROUTE_FIXES_PARALLEL
        } else {
            offCount = 0
            segIndex = bestI
            progress = cum[bestI] + bestT * (cum[bestI + 1] - cum[bestI])
            val a = pts[bestI]
            val b = pts[bestI + 1]
            snapped = LatLon(a.lat + (b.lat - a.lat) * bestT, a.lon + (b.lon - a.lon) * bestT)
        }

        val remaining = max(0.0, route.lengthM - progress)
        val remainingS = if (route.lengthM > 0) route.durationS * remaining / route.lengthM else 0.0

        // Η στροφή μένει στην οθόνη ώσπου να την περάσεις και να προχωρήσεις PASS_M μέτρα στον νέο δρόμο.
        // (Πριν άλλαζε 3 m πριν από τη στροφή: αν σταματούσες πάνω της, έδειχνε ήδη την επόμενη.)
        val nextIdx = route.maneuvers.indexOfFirst { it.atMeters > progress - PASS_M }
        val next = route.maneuvers.getOrNull(nextIdx)
        val then = route.maneuvers.getOrNull(nextIdx + 1)?.takeIf { next != null && it.atMeters - next.atMeters < 150.0 }

        return NavState(
            progressM = progress,
            remainingM = remaining,
            remainingS = remainingS,
            next = next,
            distToNextM = next?.let { max(0.0, it.atMeters - progress) } ?: 0.0,
            then = then,
            after = route.maneuvers.getOrNull(nextIdx + 1)?.takeIf { next != null },
            offRoute = offCount >= offNeeded,
            arrived = remaining < 30.0 && offCount == 0,
            segIndex = segIndex,
            snapped = snapped,
            onRoute = offCount == 0,
        )
    }

    /** Ταιριάζει η κατεύθυνσή σου με κάποιο κομμάτι της διαδρομής ±40 m γύρω σου; (στροφές της ίδιας διαδρομής δεν μετράνε ως «αλλού»). */
    private fun headingFitsNearby(i: Int, bearing: Double): Boolean {
        val pts = route.points
        val cum = route.cumulative
        val here = cum[i]
        var j = i
        while (j > 0 && here - cum[j - 1] <= 40.0) j--
        while (j < pts.size - 1 && cum[j] - here <= 40.0) {
            if (cum[j + 1] - cum[j] > 3.0 && angleDiff(bearing, Geo.bearing(pts[j], pts[j + 1])) <= 50.0) return true
            j++
        }
        return false
    }

    companion object {
        /** Από τόσα μέτρα και πάνω, με λάθος κατεύθυνση, είσαι σε άλλο δρόμο. */
        const val TURN_OFF_M = 15.0
        /** Πόσα συνεχόμενα στίγματα εκτός διαδρομής (1/s) πριν τη νέα διαδρομή: μακριά ή σε άλλη κατεύθυνση. */
        const val OFF_ROUTE_FIXES = 2
        /** Κοντά και παράλληλα με τη διαδρομή (συχνά GPS που πετάγεται στην πόλη). */
        const val OFF_ROUTE_FIXES_PARALLEL = 4
        /** Πάνω από τόσα μέτρα είσαι σίγουρα αλλού. */
        const val FAR_M = 60.0

        fun angleDiff(a: Double, b: Double): Double {
            val d = ((a - b) % 360.0 + 360.0) % 360.0
            return if (d > 180.0) 360.0 - d else d
        }

        /** Πόσα μέτρα μετά τη στροφή αλλάζει η ταμπέλα στην επόμενη. */
        const val PASS_M = 20.0
    }
}
