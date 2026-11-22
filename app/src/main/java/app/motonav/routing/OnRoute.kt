package app.motonav.routing

import app.motonav.geo.Geo
import app.motonav.geo.LatLon

/** Πού πέφτει ένα σημείο πάνω στη διαδρομή: μέτρα από την αρχή και πόσο μακριά από τη γραμμή. */
data class OnRoutePos(val alongM: Double, val offM: Double)

object OnRoute {
    /**
     * Η πιο κοντινή θέση της γραμμής στο p. Ψάχνει από fromM-50 ως toM+50 (όλη η γραμμή αν δεν δοθούν).
     * null αν η διαδρομή δεν έχει γραμμή.
     */
    fun project(route: Route, p: LatLon, fromM: Double = 0.0, toM: Double = Double.MAX_VALUE): OnRoutePos? {
        val pts = route.points
        val cum = route.cumulative
        if (pts.size < 2) return null
        var i = cum.indexOfFirst { it >= fromM - 50 }.let { if (it < 1) 0 else it - 1 }
        var bestD = Double.MAX_VALUE
        var bestAlong = 0.0
        while (i < pts.size - 1 && cum[i] <= toM + 50) {
            val (t, d) = Geo.project(p, pts[i], pts[i + 1])
            if (d < bestD) {
                bestD = d
                bestAlong = cum[i] + t * (cum[i + 1] - cum[i])
            }
            i++
        }
        if (bestD == Double.MAX_VALUE) return null
        return OnRoutePos(bestAlong, bestD)
    }

    /** Σημεία ανά stepM πάνω στη γραμμή (αρχή και τέλος πάντα μέσα), για ερωτήσεις «γύρω από τη διαδρομή». */
    fun sample(route: Route, stepM: Double, maxPoints: Int = 400): List<LatLon> {
        val len = route.lengthM
        if (route.points.isEmpty()) return emptyList()
        if (len <= 0) return listOf(route.points.first())
        val step = maxOf(stepM, len / (maxPoints - 1))
        val out = ArrayList<LatLon>()
        var m = 0.0
        while (m < len) { out += route.pointAt(m); m += step }
        out += route.points.last()
        return out
    }
}
