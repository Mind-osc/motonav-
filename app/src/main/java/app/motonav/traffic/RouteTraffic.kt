package app.motonav.traffic

import app.motonav.routing.Route

/** Κίνηση πάνω στη δική μας διαδρομή: από fromM ως toM, επίπεδο 1..3. */
data class RouteJam(val fromM: Double, val toM: Double, val level: Int) {
    companion object {
        /** Κομμάτια που απέχουν λιγότερο από gapM ενώνονται (με το χειρότερο επίπεδο), για να μη βγαίνουν «χάντρες». */
        fun merge(jams: List<RouteJam>, gapM: Double = 120.0): List<RouteJam> {
            val out = ArrayList<RouteJam>()
            for (j in jams.sortedBy { it.fromM }) {
                val last = out.lastOrNull()
                if (last != null && j.fromM - last.toM <= gapM) {
                    out[out.lastIndex] = RouteJam(last.fromM, maxOf(last.toM, j.toM), maxOf(last.level, j.level))
                } else out += j
            }
            return out
        }
    }
}

/** Τα τμήματα κίνησης για μια συγκεκριμένη διαδρομή (για να μη ζωγραφίσουμε παλιά κίνηση σε νέα διαδρομή). */
data class RouteTraffic(val route: Route?, val jams: List<RouteJam>) {
    companion object {
        val NONE = RouteTraffic(null, emptyList())

        /** Ταιριάζει τα σημεία της TomTom πάνω στα μέτρα της δικής μας γραμμής. */
        fun map(route: Route, jams: List<TrafficJam>, fromIndex: Int = 0): List<RouteJam> {
            var idx = fromIndex
            return jams.mapNotNull { j ->
                if (j.points.size < 2) return@mapNotNull null
                val (ia, a) = route.progressNear(j.points.first(), idx)
                val (ib, b) = route.progressNear(j.points.last(), ia)
                idx = ia
                if (b - a < 5.0 || ib <= ia) null else RouteJam(a, b, j.level)
            }
        }
    }
}
