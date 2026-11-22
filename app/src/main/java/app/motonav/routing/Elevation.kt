package app.motonav.routing

/**
 * Προφίλ υψομέτρου της διαδρομής, για το γράφημα στην κάρτα και στην πλοήγηση.
 * samples: (μέτρα από την αρχή, υψόμετρο) σε ίσα διαστήματα.
 */
data class ElevationProfile(
    val samples: List<Pair<Double, Double>>,
    val minM: Double,
    val maxM: Double,
    val ascentM: Double,
    val descentM: Double,
) {
    val lengthM: Double get() = samples.lastOrNull()?.first ?: 0.0

    /** Ανάβαση από το fromM ως το τέλος (για «μένουν ↑ 420 m» στην πλοήγηση). */
    fun ascentFrom(fromM: Double): Double = climb(samples.filter { it.first >= fromM }.map { it.second }).first

    companion object {
        /** Μικρότερες αλλαγές από αυτό δεν μετράνε ως ανάβαση (θόρυβος του SRTM). */
        const val HYSTERESIS_M = 3.0

        fun of(route: Route, count: Int = 160): ElevationProfile? {
            val len = route.lengthM
            if (len < 200 || route.elevations.none { it != null }) return null
            val n = count.coerceAtLeast(2)
            val s = (0 until n).mapNotNull { k ->
                val m = len * k / (n - 1)
                route.elevationAt(m)?.let { m to it }
            }
            return of(s)
        }

        fun of(samples: List<Pair<Double, Double>>): ElevationProfile? {
            if (samples.size < 2) return null
            val (up, down) = climb(samples.map { it.second })
            return ElevationProfile(samples, samples.minOf { it.second }, samples.maxOf { it.second }, up, down)
        }

        /** Ανάβαση και κατάβαση με υστέρηση: μετράει μόνο όταν η αλλαγή από το τελευταίο «σταθερό» σημείο ξεπεράσει το όριο. */
        fun climb(e: List<Double>): Pair<Double, Double> {
            if (e.size < 2) return 0.0 to 0.0
            var up = 0.0
            var down = 0.0
            var ref = e.first()
            for (v in e.drop(1)) {
                val d = v - ref
                if (d >= HYSTERESIS_M) { up += d; ref = v } else if (d <= -HYSTERESIS_M) { down -= d; ref = v }
            }
            return up to down
        }
    }
}
