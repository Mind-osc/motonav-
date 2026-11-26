package app.motonav.routing

import app.motonav.geo.LatLon
import app.motonav.search.OfflineGazetteer
import app.motonav.search.Place

/**
 * «Επόμενα σημεία στη διαδρομή» (όπως τα Auto POIs του DMD2): βενζίνη, καφές, φαγητό, νερό κ.λπ.
 * από τα σημεία μέσα στην εφαρμογή (χωρίς internet). Για κάθε είδος κρατάμε όσα είναι κοντά στη γραμμή,
 * με το πόσα μέτρα από την αρχή της διαδρομής πέφτουν· στην πλοήγηση δείχνουμε το επόμενο μπροστά σου.
 */
enum class RoutePoiKind(val emoji: String, val label: String, val kinds: Set<String>, val maxOffM: Double) {
    FUEL("⛽", "Βενζίνη", setOf("fuel"), 250.0),
    CAFE("☕", "Καφές", setOf("cafe"), 120.0),
    FOOD("🍴", "Φαγητό", setOf("restaurant", "fast_food"), 120.0),
    WATER("💧", "Νερό", setOf("drinking_water"), 80.0),
    GARAGE("🔧", "Συνεργείο", setOf("motorcycle", "motorcycle_repair", "car_repair", "tyres"), 200.0),
    SLEEP("🛏", "Ύπνος", setOf("hotel", "guest_house", "hostel", "camp_site"), 300.0),
}

data class RoutePoi(val kind: RoutePoiKind, val place: Place, val alongM: Double, val offM: Double)

object RoutePois {
    /** Όλα τα σημεία των ειδών κοντά στη γραμμή, ταξινομημένα κατά μήκος. Βαρύ: τρέξ' το εκτός UI thread. */
    fun along(route: Route, kinds: Collection<RoutePoiKind>): List<RoutePoi> {
        if (kinds.isEmpty() || route.points.size < 2) return emptyList()
        val pts = route.points
        val pad = 0.004 // ~400 m
        val s = pts.minOf { it.lat } - pad
        val n = pts.maxOf { it.lat } + pad
        val w = pts.minOf { it.lon } - pad
        val e = pts.maxOf { it.lon } + pad
        val all = OfflineGazetteer.poisIn(s, w, n, e, kinds.flatMap { it.kinds }.toSet())
        if (all.isEmpty()) return emptyList()
        // Γρήγορο φίλτρο: κελιά ~550 m γύρω από σημεία της γραμμής ανά 200 m.
        val cell = 0.005
        val cells = HashSet<Long>()
        fun key(i: Int, j: Int) = i.toLong() * 1_000_003L + j
        for (p in OnRoute.sample(route, 200.0, 5000)) {
            val i = Math.floor(p.lat / cell).toInt()
            val j = Math.floor(p.lon / cell).toInt()
            for (di in -1..1) for (dj in -1..1) cells += key(i + di, j + dj)
        }
        val out = ArrayList<RoutePoi>()
        for (pl in all) {
            if (key(Math.floor(pl.lat / cell).toInt(), Math.floor(pl.lon / cell).toInt()) !in cells) continue
            val k = kinds.firstOrNull { pl.kind in it.kinds } ?: continue
            val pos = OnRoute.project(route, LatLon(pl.lat, pl.lon)) ?: continue
            if (pos.offM <= k.maxOffM) out += RoutePoi(k, pl.copy(name = pl.name.ifBlank { k.label }, detail = k.label), pos.alongM, pos.offM)
        }
        return out.sortedBy { it.alongM }
    }

    /** Το επόμενο σημείο κάθε είδους μπροστά σου (progressM: πόσα μέτρα της διαδρομής έχεις κάνει). */
    fun next(list: List<RoutePoi>, progressM: Double): List<RoutePoi> =
        list.filter { it.alongM > progressM + 30 }
            .groupBy { it.kind }
            .mapNotNull { (_, v) -> v.minByOrNull { it.alongM } }
            .sortedBy { it.kind.ordinal }
}
