package app.motonav.search

import android.content.Context
import app.motonav.geo.Geo
import app.motonav.geo.LatLon

/**
 * Διασταυρώσεις οδών χωρίς internet: «Λιοσίων & Πραΐδου», «Λιοσίων και Πραΐδου», «Λιοσίων / Πραΐδου».
 * Το OpenStreetMap στην Ελλάδα έχει λίγους αριθμούς σπιτιών· η γωνία δύο δρόμων βρίσκεται πάντα.
 * Δεδομένα: assets/places/gr-cross.tsv (οδός Α, οδός Β, lat, lon), από το Geofabrik κάθε εβδομάδα.
 * Το αρχείο είναι μεγάλο, οπότε δεν μένει στη μνήμη: διαβάζεται μία φορά ανά αναζήτηση διασταύρωσης.
 */
object OfflineCross {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val separators = Regex("\\s*(?:&|/|\\+|\\s(?:και|κ\\.|x|X|×|and)\\s)\\s*")

    /** «Λιοσίων & Πραΐδου» → («Λιοσίων», «Πραΐδου»). null αν δεν είναι διασταύρωση. */
    fun split(query: String): Pair<String, String>? {
        val parts = query.trim().split(separators, limit = 2)
        if (parts.size != 2) return null
        val a = parts[0].trim().trimEnd(',')
        val b = parts[1].trim().trimEnd(',')
        // όχι αριθμοί σπιτιών («Λιοσίων 12/14») και όχι πολύ μικρά κομμάτια
        if (a.length < 3 || b.length < 3 || b.first().isDigit()) return null
        return a to b
    }

    private class Matcher(part: String) {
        private val bare = PlaceParsers.stripStreetType(part)
        private val words = bare.split(' ').filter { it.isNotEmpty() }
        /** Ρίζα κάθε λέξης: χωρίς κατάληξη πτώσης (Λιοσίων/Λιοσίου), τουλάχιστον 4 γράμματα. */
        private val stems = words.map { w -> if (w.length >= 6) w.dropLast(2) else w }

        fun matches(name: String): Boolean {
            if (bare.isEmpty()) return false
            val n = PlaceParsers.stripStreetType(name)
            if (n.contains(bare)) return true
            val nw = n.split(' ')
            // κάθε λέξη του ερωτήματος να ξεκινάει κάποια λέξη του ονόματος
            return stems.all { s -> s.length >= 3 && nw.any { it.startsWith(s) } }
        }

        /** 0 ίδιο όνομα, 1 το περιέχει, 2 μόνο κατά ρίζα. */
        fun score(name: String): Int {
            val n = PlaceParsers.stripStreetType(name)
            return when {
                n == bare -> 0
                n.contains(bare) -> 1
                else -> 2
            }
        }
    }

    fun search(query: String, near: LatLon?, limit: Int = 10): List<Place> {
        val (qa, qb) = split(query) ?: return emptyList()
        val ctx = appContext ?: return emptyList()
        val ma = Matcher(qa)
        val mb = Matcher(qb)
        class Hit(val a: String, val b: String, val p: LatLon, val score: Int)
        val hits = ArrayList<Hit>()
        runCatching {
            ctx.assets.open("places/gr-cross.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val t1 = line.indexOf('\t')
                    if (t1 < 0) continue
                    val t2 = line.indexOf('\t', t1 + 1)
                    if (t2 < 0) continue
                    val a = line.substring(0, t1)
                    val b = line.substring(t1 + 1, t2)
                    val direct = ma.matches(a) && mb.matches(b)
                    val swapped = !direct && ma.matches(b) && mb.matches(a)
                    if (!direct && !swapped) continue
                    val f = line.substring(t2 + 1).split('\t')
                    val lat = f.getOrNull(0)?.toDoubleOrNull() ?: continue
                    val lon = f.getOrNull(1)?.toDoubleOrNull() ?: continue
                    val (x, y) = if (direct) a to b else b to a
                    hits += Hit(x, y, LatLon(lat, lon), ma.score(x) + mb.score(y))
                }
            }
        }
        if (hits.isEmpty()) return emptyList()
        // Πρώτα όσα ταιριάζουν καλύτερα στο όνομα, μετά τα πιο κοντινά. Από κάθε ζευγάρι οδών το πιο κοντινό σημείο.
        val sorted = hits.sortedWith(compareBy<Hit>({ it.score }, { h -> near?.let { Geo.distance(it, h.p) } ?: 0.0 }))
        val seen = HashSet<String>()
        // Ίδιο ζευγάρι οδών σε άλλη πόλη = άλλο αποτέλεσμα (κελί ~5 km).
        return sorted.filter { h -> seen.add("${h.a}|${h.b}|${Math.round(h.p.lat * 20)}|${Math.round(h.p.lon * 20)}") }
            .take(limit)
            .map { h -> Place("${h.a} & ${h.b}", "Διασταύρωση", h.p.lat, h.p.lon, "crossing") }
    }
}
