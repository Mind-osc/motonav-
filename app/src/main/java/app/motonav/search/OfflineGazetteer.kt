package app.motonav.search

import android.content.Context
import app.motonav.geo.Geo
import app.motonav.geo.LatLon

/**
 * Αναζήτηση χωρίς internet: πόλεις, χωριά, οικισμοί, νησιά, κορυφές και διάσελα της Ελλάδας
 * (assets/places/gr.tsv, από το OpenStreetMap· το ανανεώνει κάθε μήνα το GitHub Actions).
 * Και οδοί (assets/places/gr-streets.tsv), χωρίς αριθμούς σπιτιών.
 */
object OfflineGazetteer {
    private class Entry(val name: String, val latin: String, val norm: String, val normLatin: String, val lat: Double, val lon: Double, val kind: String, val detail: String)

    @Volatile private var appContext: Context? = null
    @Volatile private var entries: List<Entry>? = null

    /** Οδοί: φορτώνονται μόνο την πρώτη φορά που χρειάζονται (μεγάλο αρχείο). */
    private class Street(val name: String, val norm: String, val bare: String, val lat: Double, val lon: Double, val area: String)
    @Volatile private var streets: List<Street>? = null

    private fun loadStreets(): List<Street> {
        streets?.let { return it }
        val ctx = appContext ?: return emptyList()
        val list = runCatching {
            ctx.assets.open("places/gr-streets.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    val f = line.split('\t')
                    if (f.size < 3) return@mapNotNull null
                    val lat = f[1].toDoubleOrNull() ?: return@mapNotNull null
                    val lon = f[2].toDoubleOrNull() ?: return@mapNotNull null
                    Street(f[0], PlaceParsers.normalize(f[0]), PlaceParsers.stripStreetType(f[0]), lat, lon, f.getOrElse(3) { "" })
                }.toList()
            }
        }.getOrDefault(emptyList())
        streets = list
        return list
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun load(): List<Entry> {
        entries?.let { return it }
        val ctx = appContext ?: return emptyList()
        val list = runCatching {
            ctx.assets.open("places/gr.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line -> parseLine(line) }.toList()
            }
        }.getOrDefault(emptyList())
        entries = list
        return list
    }

    private fun parseLine(line: String): Entry? {
        val f = line.split('\t')
        if (f.size < 5) return null
        val lat = f[2].toDoubleOrNull() ?: return null
        val lon = f[3].toDoubleOrNull() ?: return null
        return Entry(f[0], f[1], PlaceParsers.normalize(f[0]), PlaceParsers.normalize(f[1]), lat, lon, f[4], f.getOrElse(5) { "" })
    }

    val available: Boolean get() = load().isNotEmpty()

    private fun weight(kind: String) = when (kind) {
        "city" -> 0
        "town" -> 1
        "island" -> 2
        "village" -> 3
        "peak", "pass", "volcano" -> 4
        "suburb", "quarter", "neighbourhood" -> 5
        else -> 6
    }

    private fun kindLabel(kind: String) = when (kind) {
        "city" -> "Πόλη"
        "town" -> "Κωμόπολη"
        "village" -> "Χωριό"
        "hamlet", "isolated_dwelling" -> "Οικισμός"
        "island", "islet" -> "Νησί"
        "peak", "volcano" -> "Κορυφή"
        "pass" -> "Διάσελο"
        "suburb", "quarter", "neighbourhood" -> "Περιοχή"
        else -> "Τοποθεσία"
    }

    fun search(query: String, near: LatLon?, limit: Int = 15): List<Place> {
        val q = PlaceParsers.normalize(query)
        if (q.length < 2) return emptyList()
        data class Hit(val e: Entry, val score: Int)
        val hits = ArrayList<Hit>()
        for (e in load()) {
            val s = when {
                e.norm == q || e.normLatin == q -> 0
                e.norm.startsWith(q) || e.normLatin.startsWith(q) -> 1
                e.norm.split(' ', '-').any { it.startsWith(q) } || e.normLatin.split(' ', '-').any { it.startsWith(q) } -> 2
                q.length >= 4 && (e.norm.contains(q) || e.normLatin.contains(q)) -> 3
                else -> continue
            }
            hits += Hit(e, s)
        }
        val places = hits.sortedWith(
            compareBy<Hit> { it.score }
                .thenBy { weight(it.e.kind) }
                .thenBy { h -> near?.let { Geo.distance(it, LatLon(h.e.lat, h.e.lon)) } ?: 0.0 },
        ).take(limit).map { h ->
            val detail = listOf(kindLabel(h.e.kind), h.e.detail).filter { it.isNotBlank() }.joinToString(" · ")
            h.score to Place(h.e.name, "$detail · χωρίς internet", h.e.lat, h.e.lon, h.e.kind)
        }

        // Οδοί: «Λιοσίων», «λεωφ. Αλεξάνδρας», «Αριστοτέλους» (χωρίς αριθμό). Πρώτα οι πιο κοντινές.
        val qb = PlaceParsers.stripStreetType(query).ifEmpty { q }
        val streetHits = ArrayList<Pair<Int, Street>>()
        if (qb.length >= 3) {
            for (st in loadStreets()) {
                val sc = when {
                    st.bare == qb || st.norm == q -> 0
                    st.bare.startsWith(qb) -> 1
                    st.bare.split(' ').any { it.startsWith(qb) } -> 2
                    else -> continue
                }
                streetHits += sc to st
            }
        }
        val streetPlaces = streetHits.sortedWith(
            compareBy<Pair<Int, Street>> { it.first }
                .thenBy { (_, st) -> near?.let { Geo.distance(it, LatLon(st.lat, st.lon)) } ?: 0.0 },
        ).take(limit).map { (sc, st) ->
            sc to Place(st.name, listOf("Οδός", st.area).filter { it.isNotBlank() }.joinToString(" · ") + " · χωρίς internet", st.lat, st.lon, "street")
        }

        // Ίδιο «πόσο ταιριάζει»: πρώτα τα μέρη (πόλη/χωριό), μετά οι οδοί.
        return (places + streetPlaces).sortedBy { it.first }.map { it.second }.take(limit)
    }
}
