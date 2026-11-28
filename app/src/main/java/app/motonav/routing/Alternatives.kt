package app.motonav.routing

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.i18n.tr

/** Μια εναλλακτική με το σημείο όπου θα μπει το «συννεφάκι» με τη διαφορά χρόνου. */
class Alternative(val route: Route, val labelAt: LatLon)

object Alternatives {
    /** Πόσο απέχει (σε μέτρα) το p από τη γραμμή, με δειγματοληψία για ταχύτητα. */
    private fun distToLine(p: LatLon, line: List<LatLon>, step: Int): Double {
        var best = Double.MAX_VALUE
        var i = 0
        while (i < line.size - 1) {
            val j = minOf(i + step, line.size - 1)
            val d = Geo.project(p, line[i], line[j]).second
            if (d < best) best = d
            i = j
        }
        return best
    }

    /**
     * Κρατάει όσες είναι λογικές (όχι πολύ πιο αργές/μακριές) και διαφέρουν ουσιαστικά από την κύρια και μεταξύ τους,
     * έως 4, και για καθεμιά βρίσκει το σημείο που απέχει περισσότερο από την κύρια: εκεί φαίνεται καλύτερα.
     */
    /** Λογική εναλλακτική: όχι πάνω από ~30% (ή 2′) πιο αργή και 50% πιο μακριά. */
    fun reasonable(main: Route, c: Route): Boolean =
        c.durationS <= maxOf(main.durationS * 1.3, main.durationS + 120.0) && c.lengthM <= main.lengthM * 1.5

    fun build(main: Route, candidates: List<Route>, max: Int = 4): List<Alternative> {
        val kept = ArrayList<Alternative>()
        val lines = arrayListOf(main.points)
        // Πρέπει να ξεχωρίζει ουσιαστικά: ≥ 300 m, και σε μεγάλες διαδρομές λίγο περισσότερο (έως 1,5 km).
        val minApart = (main.lengthM * 0.03).coerceIn(300.0, 1500.0)
        for (c in candidates) {
            if (kept.size >= max || c.points.size < 2) continue
            if (!reasonable(main, c)) continue
            val step = maxOf(1, c.points.size / 150)
            var farthest = -1.0
            var at = c.points[c.points.size / 2]
            var i = 0
            while (i < c.points.size) {
                val p = c.points[i]
                // απόσταση από την πιο κοντινή από τις γραμμές που ήδη δείχνουμε
                val d = lines.minOf { distToLine(p, it, maxOf(1, it.size / 400)) }
                if (d > farthest) { farthest = d; at = p }
                i += step
            }
            if (farthest < minApart) continue
            kept += Alternative(c, at)
            lines += c.points
        }
        return kept
    }

    /** «+3′», «−2′», «ίδια ώρα». */
    fun deltaLabel(altS: Double, mainS: Double): String {
        val min = Math.round((altS - mainS) / 60.0).toInt()
        return when {
            min == 0 -> tr("ίδια ώρα")
            min > 0 -> "+$min′"
            else -> "−${-min}′"
        }
    }
}
