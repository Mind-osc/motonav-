package app.motonav.search

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    val kind: String = "",
    /** Λίστα σελιδοδεικτών (κενό = «Αγαπημένα») και χρώμα της (ARGB, 0 = χρώμα θέματος). */
    val list: String = "",
    val color: Int = 0,
) {
    val latLon: LatLon get() = LatLon(lat, lon)
    val listName: String get() = list.ifBlank { DEFAULT_LIST }

    companion object {
        const val DEFAULT_LIST = "Αγαπημένα"
    }
}

/** Κατηγορίες «κοντά μου». Φίλτρα OpenStreetMap για το Overpass. */
/** tomtom: κατηγορίες TomTom (γρήγορη αναζήτηση με κλειδί)· null = μόνο OpenStreetMap. */
enum class Category(val label: String, val filter: String, val fallbackName: String, val tomtom: String? = null) {
    FUEL("Βενζίνη", "[amenity=fuel]", "Βενζινάδικο", "7311"),
    FOOD("Φαγητό", "[amenity~\"^(restaurant|fast_food)$\"]", "Φαγητό", "7315"),
    CAFE("Καφές", "[amenity=cafe]", "Καφέ", "9376"),
    GARAGE("Συνεργείο", "[shop~\"^(motorcycle|motorcycle_repair|car_repair|tyres)$\"]", "Συνεργείο", "7310"),
    LODGING("Ύπνος", "[tourism~\"^(hotel|guest_house|hostel|camp_site)$\"]", "Κατάλυμα", "7314,7360"),
    VIEW("Θέα", "[tourism=viewpoint]", "Θέα", "7337"),
    WATER("Νερό", "[amenity=drinking_water]", "Βρύση"),
    MOTO("Μηχανές", "[shop~\"^(motorcycle|motorcycle_repair)$\"]", "Μαγαζί μηχανών"),
    WASH("Πλυντήριο", "[amenity=car_wash]", "Πλυντήριο", "9155"),
}

/**
 * Αναζήτηση με internet.
 * Κείμενο: Photon (komoot), βασισμένο στο OpenStreetMap, χωρίς κλειδί.
 * Κατηγορίες: Overpass API, ό,τι υπάρχει στο OpenStreetMap γύρω σου.
 * Η offline αναζήτηση έρχεται μαζί με τον offline χάρτη.
 */
class PlaceSearch(private val tomtomKey: String = "") {

    /**
     * Photon σε κάθε γράμμα· με «Αναζήτηση»/φωνή (thorough) και Nominatim. Μαζί (το Nominatim ξέρει καλύτερα δήμους και ελληνικά ονόματα),
     * χωρίς διπλά, και ταξινόμηση πρώτα κατά το πόσο ταιριάζει το όνομα, μετά πόλεις/χωριά,
     * και στο τέλος η απόσταση. Έτσι το «Αχαρνές» φέρνει τις Αχαρνές πριν από την οδό Αχαρνών.
     */
    suspend fun text(query: String, near: LatLon?, thorough: Boolean = false): List<Place> = coroutineScope {
        // «Λ. Συγγρού» → «Λεωφόρος Συγγρού», «Πλ.» → «Πλατεία» κ.λπ.
        val q = PlaceParsers.expandAbbreviations(query.trim())
        if (q.length < 2) return@coroutineScope emptyList()
        fun photonUrl(text: String) = buildString {
            append("https://photon.komoot.io/api/?limit=15&q=")
            append(enc(text))
            if (near != null) append(String.format(Locale.US, "&lat=%.5f&lon=%.5f", near.lat, near.lon))
        }
        val split = PlaceParsers.splitHouseNumber(q)
        val photon = async(Dispatchers.IO) { runCatching { PlaceParsers.photon(get(photonUrl(q))) } }
        // Χωρίς τόνο («συγγρου»): το Photon δεν ταιριάζει «συγγρου» με «Συγγρού». Ο τόνος στα ελληνικά είναι πάντα
        // σε μία από τις 3 τελευταίες συλλαβές, οπότε ρωτάμε και αυτές τις (έως 3) εκδοχές.
        val variants = PlaceParsers.accentVariants(split?.first ?: q).map { v -> if (split != null) "$v ${split.second}" else v }
        val accented = variants.map { v -> async(Dispatchers.IO) { runCatching { PlaceParsers.photon(get(photonUrl(v))) } } }
        // «Αριστοτέλους 227»: αν ο αριθμός δεν υπάρχει στο OpenStreetMap, το Photon φέρνει μόνο
        // μακρινούς δρόμους που τον έχουν. Ψάχνουμε και τον δρόμο σκέτο, για να βγει ο κοντινός.
        val streetOnly = async(Dispatchers.IO) {
            if (split == null) return@async Result.success(emptyList<Place>())
            runCatching {
                (listOf(split.first) + PlaceParsers.accentVariants(split.first)).flatMap { PlaceParsers.photon(get(photonUrl(it))) }
            }
        }
        val nominatim = async(Dispatchers.IO) {
            if (!thorough) return@async Result.success(emptyList<Place>())
            runCatching {
                val url = buildString {
                    append("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=10&accept-language=el&q=")
                    append(enc(q))
                    if (near != null) {
                        // προτίμηση (όχι όριο) σε ±1° γύρω σου
                        append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f", near.lon - 1, near.lat + 1, near.lon + 1, near.lat - 1))
                    }
                }
                PlaceParsers.nominatim(get(url))
            }
        }
        // Αριθμοί σπιτιών: το OpenStreetMap στην Ελλάδα έχει λίγους. Το TomTom (με το κλειδί σου) έχει σχεδόν όλους
        // και καταλαβαίνει μισές λέξεις, χωρίς τόνους, χωρίς «Λεωφόρος». Με κλειδί το ρωτάμε σε κάθε αναζήτηση
        // (typeahead όσο γράφεις· η εφαρμογή περιμένει 400 ms μετά το τελευταίο γράμμα).
        val tomtom = async(Dispatchers.IO) {
            if (tomtomKey.isBlank() || q.length < 3) return@async Result.success(emptyList<Place>())
            runCatching {
                val url = buildString {
                    append("https://api.tomtom.com/search/2/search/")
                    append(enc(q).replace("+", "%20"))
                    append(".json?limit=10&countrySet=GR&language=el-GR&typeahead=")
                    append(if (thorough) "false" else "true")
                    append("&key=")
                    append(enc(tomtomKey))
                    if (near != null) append(String.format(Locale.US, "&lat=%.5f&lon=%.5f", near.lat, near.lon))
                }
                PlaceParsers.tomtom(get(url))
            }
        }
        val a = photon.await()
        val av = accented.map { it.await() }
        val b = nominatim.await()
        val c = streetOnly.await()
        val t = tomtom.await()
        if (a.isFailure && b.isFailure && c.isFailure && t.isFailure && av.all { it.isFailure }) {
            // Χωρίς internet: πόλεις, χωριά και κορυφές από τη λίστα μέσα στην εφαρμογή.
            val offline = withContext(Dispatchers.IO) { OfflineGazetteer.search(q, near) }
            if (offline.isNotEmpty()) return@coroutineScope offline
            throw (a.exceptionOrNull() ?: IOException("search"))
        }
        val ranked = PlaceParsers.rank(
            q,
            a.getOrDefault(emptyList()) + av.flatMap { it.getOrDefault(emptyList()) } + c.getOrDefault(emptyList()),
            t.getOrDefault(emptyList()) + b.getOrDefault(emptyList()),
            near,
        )
        val result = PlaceParsers.markMissingNumber(ranked, split?.second)
        result.ifEmpty { withContext(Dispatchers.IO) { OfflineGazetteer.search(q, near) } }
    }

    /**
     * Κατηγορία γύρω σου. Με κλειδί TomTom: TomTom (1–2″). Αλλιώς, ή αν δεν βρει τίποτα, OpenStreetMap/Overpass,
     * που όταν έχει φόρτο αργεί πολύ· γι' αυτό σταματάμε στα 40″ με μήνυμα αντί για ατελείωτο «Ψάχνω…».
     */
    suspend fun category(cat: Category, near: LatLon, radiusM: Int = 10_000): List<Place> = withContext(Dispatchers.IO) {
        if (tomtomKey.isNotBlank() && cat.tomtom != null) {
            val found = runCatching {
                val url = String.format(
                    Locale.US,
                    "https://api.tomtom.com/search/2/nearbySearch/.json?lat=%.6f&lon=%.6f&radius=%d&limit=50&categorySet=%s&language=el-GR&key=%s",
                    near.lat, near.lon, radiusM, cat.tomtom, enc(tomtomKey),
                )
                PlaceParsers.tomtom(get(url)).map { if (it.name.isBlank()) it.copy(name = cat.fallbackName) else it }
            }.getOrNull()
            if (!found.isNullOrEmpty()) return@withContext found
        }
        val query = String.format(
            Locale.US,
            "[out:json][timeout:15];nwr(around:%d,%.5f,%.5f)%s;out center 60;",
            radiusM, near.lat, near.lon, cat.filter,
        )
        // Η κλήση τρέχει χωριστά: αν αργήσει, σταματάμε να περιμένουμε (η σύνδεση κλείνει μόνη της στο timeout της).
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        val call = kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { app.motonav.net.Overpass.query(query, 15_000) }
        val json = kotlinx.coroutines.withTimeoutOrNull(40_000) { call.await() }
            ?: throw java.net.SocketTimeoutException("Overpass")
        PlaceParsers.overpass(json, near, cat.fallbackName)
    }

    /** Όνομα για σημείο στον χάρτη (δρόμος / χωριό), από το Photon. null αν δεν βρεθεί ή δεν υπάρχει internet. */
    suspend fun reverse(p: LatLon): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = String.format(Locale.US, "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f&limit=1", p.lat, p.lon)
            PlaceParsers.photon(get(url)).firstOrNull()?.let { pl ->
                listOf(pl.name, pl.detail.substringBefore(",")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            }
        }.getOrNull()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String = request(url, null)

    private fun post(url: String, body: String): String = request(url, body)

    private fun request(url: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "MotoNav/0.1 (personal Android app)")
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

object PlaceParsers {

    private fun JSONObject.str(key: String): String =
        if (!has(key) || isNull(key)) "" else optString(key).trim()

    fun photon(json: String): List<Place> {
        val feats = JSONObject(json).optJSONArray("features") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until feats.length()) {
            val f = feats.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val p = f.optJSONObject("properties") ?: JSONObject()
            val street = listOf(p.str("street"), p.str("housenumber")).filter { it.isNotEmpty() }.joinToString(" ")
            val town = p.str("city").ifEmpty { p.str("district") }.ifEmpty { p.str("locality") }
            val name = listOf(p.str("name"), street, town).firstOrNull { it.isNotEmpty() } ?: continue
            val detail = listOf(
                street.takeIf { it != name },
                town.takeIf { it != name },
                p.str("county").ifEmpty { p.str("state") },
                p.str("country").takeIf { p.str("countrycode").uppercase(Locale.ROOT) != "GR" },
            ).filterNotNull().filter { it.isNotEmpty() }.distinct().joinToString(", ")
            out += Place(
                name = name,
                detail = detail,
                lat = coords.getDouble(1),
                lon = coords.getDouble(0),
                kind = p.str("osm_value"),
            )
        }
        return out.distinctBy { Triple(it.name, (it.lat * 1e4).toLong(), (it.lon * 1e4).toLong()) }
    }

    fun nominatim(json: String): List<Place> {
        val arr = JSONArray(json)
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val lat = o.optString("lat").toDoubleOrNull() ?: continue
            val lon = o.optString("lon").toDoubleOrNull() ?: continue
            val addr = o.optJSONObject("address") ?: JSONObject()
            val road = listOf(addr.str("road"), addr.str("house_number")).filter { it.isNotEmpty() }.joinToString(" ")
            val name = o.str("name").ifEmpty { road }.ifEmpty { o.str("display_name").substringBefore(",") }
            if (name.isEmpty()) continue
            val town = addr.str("city").ifEmpty { addr.str("town") }.ifEmpty { addr.str("village") }
                .ifEmpty { addr.str("municipality") }.ifEmpty { addr.str("suburb") }
            val detail = listOf(
                road.takeIf { it != name },
                town.takeIf { it != name },
                addr.str("county").ifEmpty { addr.str("state_district") }.ifEmpty { addr.str("state") },
                addr.str("country").takeIf { addr.str("country_code").lowercase(Locale.ROOT) != "gr" },
            ).filterNotNull().filter { it.isNotEmpty() }.distinct().joinToString(", ")
            val type = o.str("addresstype").ifEmpty { o.str("type") }
            out += Place(name, detail, lat, lon, type)
        }
        return out
    }

    /** TomTom Search: διευθύνσεις με αριθμό («Point Address»), δρόμοι, σημεία. */
    fun tomtom(json: String): List<Place> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pos = o.optJSONObject("position") ?: continue
            val lat = pos.optDouble("lat")
            val lon = pos.optDouble("lon")
            if (lat.isNaN() || lon.isNaN()) continue
            val a = o.optJSONObject("address") ?: JSONObject()
            val street = listOf(a.str("streetName"), a.str("streetNumber")).filter { it.isNotEmpty() }.joinToString(" ")
            val poi = o.optJSONObject("poi")?.str("name").orEmpty()
            val name = poi.ifEmpty { street }.ifEmpty { a.str("municipality") }.ifEmpty { a.str("freeformAddress") }
            if (name.isEmpty()) continue
            val detail = listOf(
                street.takeIf { it != name },
                a.str("municipalitySubdivision").ifEmpty { a.str("municipality") }.takeIf { it != name },
                a.str("countrySecondarySubdivision"),
            ).filterNotNull().filter { it.isNotEmpty() }.distinct().joinToString(", ")
            val kind = when (o.str("type")) {
                "Point Address", "Address Range" -> "house"
                "Street" -> "street"
                "Geography" -> "town"
                else -> "poi"
            }
            out += Place(name, detail, lat, lon, kind)
        }
        return out
    }

    /** Λέξεις που λένε τι είδους δρόμος είναι. Χωρίς τόνους, πεζά (όπως τα βγάζει το normalize). */
    private val streetTypes = setOf(
        "λεωφορος", "λεωφ", "λ", "οδος", "οδ", "πλατεια", "πλ", "παροδος", "παρ", "πεζοδρομος",
        "εθνικη", "επαρχιακη", "οδου", "λεωφορου", "πλατειας",
    ).map { normalize(it) }.toSet()

    /** Είδη αποτελεσμάτων που είναι δρόμος (όχι σπίτι/μαγαζί): εκεί λέμε «χωρίς τον αριθμό». */
    private val streetKinds = setOf(
        "street", "road", "residential", "primary", "secondary", "tertiary", "trunk", "motorway", "unclassified",
        "living_street", "service", "pedestrian", "primary_link", "secondary_link", "tertiary_link", "trunk_link", "track",
    )

    /** «Λ. Συγγρού», «Λεωφ Συγγρού» → «Λεωφόρος Συγγρού»· «Πλ.» → «Πλατεία»· «Οδ.» → «Οδός». */
    fun expandAbbreviations(q: String): String {
        var t = q
        val rules = listOf(
            Regex("(?iu)(^|\\s)(λ|λεωφ|λεωφ\\.|λ\\.)(?=\\s)") to "Λεωφόρος",
            Regex("(?iu)(^|\\s)(πλ|πλ\\.)(?=\\s)") to "Πλατεία",
            Regex("(?iu)(^|\\s)(οδ|οδ\\.)(?=\\s)") to "Οδός",
        )
        for ((re, full) in rules) t = re.replace(t) { m -> m.groupValues[1] + full }
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** Χωρίς «Λεωφόρος», «Οδός», «Πλατεία» μπροστά (για να ταιριάζει «Συγγρού» με «Λεωφόρος Ανδρέα Συγγρού»). */
    fun stripStreetType(s: String): String {
        val words = normalize(s).split(Regex("[\\s,.]+")).filter { it.isNotEmpty() }
        return words.dropWhile { it in streetTypes }.joinToString(" ")
    }

    private val accentOf = mapOf('α' to 'ά', 'ε' to 'έ', 'η' to 'ή', 'ι' to 'ί', 'ο' to 'ό', 'υ' to 'ύ', 'ω' to 'ώ')
    private val diphthongs = setOf("αι", "ει", "οι", "ου", "υι", "αυ", "ευ")

    /**
     * Ελληνική λέξη χωρίς τόνο → οι εκδοχές με τόνο σε καθεμία από τις 3 τελευταίες συλλαβές
     * («συγγρου» → «σύγγρου», «συγγρού»). Μόνο για την πιο μακριά λέξη χωρίς τόνο (≥ 4 γράμματα), έως 3 εκδοχές.
     * Κενή λίστα αν όλα έχουν ήδη τόνο ή δεν είναι ελληνικά.
     */
    fun accentVariants(q: String): List<String> {
        val words = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun greekNoAccent(w: String) = w.length >= 4 && w.all { it in 'α'..'ω' || it in 'Α'..'Ω' || it == 'ς' } &&
            java.text.Normalizer.normalize(w, java.text.Normalizer.Form.NFD) == w
        val target = words.withIndex().filter { greekNoAccent(it.value) }.maxByOrNull { it.value.length } ?: return emptyList()
        val w = target.value.lowercase(Locale.ROOT).let { if (it.endsWith("σ")) it.dropLast(1) + "ς" else it }
        // θέσεις συλλαβών: (θέση γράμματος που παίρνει τόνο)
        val nuclei = ArrayList<Int>()
        var i = 0
        while (i < w.length) {
            if (w[i] in accentOf) {
                if (i + 1 < w.length && w.substring(i, i + 2) in diphthongs) { nuclei += i + 1; i += 2; continue }
                nuclei += i
            }
            i++
        }
        if (nuclei.size < 2) return emptyList() // μονοσύλλαβες δεν παίρνουν τόνο
        return nuclei.takeLast(3).reversed().map { pos ->
            val acc = StringBuilder(w).apply { setCharAt(pos, accentOf.getValue(w[pos])) }.toString()
            words.toMutableList().apply { this[target.index] = acc }.joinToString(" ")
        }
    }

    /**
     * Έγραψες αριθμό αλλά το αποτέλεσμα είναι ο δρόμος χωρίς αυτόν (δεν τον ξέρει το OpenStreetMap):
     * το λέμε στην περιγραφή, για να μη νομίζεις ότι σε πάει στην πόρτα.
     */
    fun markMissingNumber(list: List<Place>, number: String?): List<Place> {
        if (number == null) return list
        val re = Regex("(^|\\s)" + Regex.escape(number) + "($|\\s|,)")
        return list.map { p ->
            if (p.kind !in streetKinds || re.containsMatchIn(p.name) || p.detail.contains("χωρίς τον αριθμό")) p
            else p.copy(detail = listOf("χωρίς τον αριθμό $number", p.detail).filter { it.isNotBlank() }.joinToString(" · "))
        }
    }

    /** Πεζά, χωρίς τόνους, ς → σ. */
    fun normalize(s: String): String {
        val d = java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        return d.replace(Regex("\\p{Mn}+"), "").replace('ς', 'σ').trim()
    }

    private val placeKinds = setOf(
        "city", "town", "village", "hamlet", "suburb", "municipality", "locality", "neighbourhood",
        "quarter", "island", "administrative", "county", "state", "region",
    )

    /** Πόσο ταιριάζει το όνομα: 0 ίδιο, 1 αρχίζει έτσι, 2 το περιέχει (ή τη ρίζα, π.χ. Αχαρνές/Αχαρνών), 3 τίποτα. */
    fun textScore(query: String, name: String): Int {
        val q = normalize(query)
        val n = normalize(name)
        if (q.isEmpty()) return 3
        if (n == q) return 0
        // «Συγγρού» = «Λεωφόρος Συγγρού»· και «Ανδρέα Συγγρού» όταν γράφεις μόνο το επώνυμο.
        val qc = stripStreetType(query)
        val nc = stripStreetType(name)
        if (qc.isNotEmpty() && nc == qc) return 0
        if (n.startsWith(q)) return 1
        if (qc.isNotEmpty() && (nc.startsWith(qc) || nc.endsWith(" $qc"))) return 1
        val words = n.split(Regex("[\\s,.\\-]+")).filter { it.isNotEmpty() }
        if (words.any { it == q }) return 1
        if (n.contains(q)) return 2
        // ρίζα: χωρίς τις 2 τελευταίες (καταλήξεις πτώσεων), τουλάχιστον 4 γράμματα
        val stem = if (q.length >= 6) q.dropLast(2) else q
        if (stem.length >= 4 && words.any { it.startsWith(stem) }) return 2
        return 3
    }

    /** «Αριστοτέλους 227» ή «227 Αριστοτέλους» → ("Αριστοτέλους", "227"). */
    fun splitHouseNumber(q: String): Pair<String, String>? {
        val t = q.trim()
        Regex("^(.*\\D)[\\s,]+(\\d+[\\p{L}]?)$").find(t)?.let { m ->
            val street = m.groupValues[1].trim().trimEnd(',')
            if (street.length >= 3) return street to m.groupValues[2]
        }
        Regex("^(\\d+[\\p{L}]?)[\\s,]+(\\D.*)$").find(t)?.let { m ->
            val street = m.groupValues[2].trim()
            if (street.length >= 3) return street to m.groupValues[1]
        }
        return null
    }

    fun rank(query: String, photon: List<Place>, nominatim: List<Place>, near: LatLon?): List<Place> {
        data class Scored(val p: Place, val score: Double)
        val split = splitHouseNumber(query)
        val nameQuery = split?.first ?: query
        val number = split?.second
        val all = ArrayList<Scored>()
        fun add(list: List<Place>, sourceBonus: Double) {
            list.forEachIndexed { i, p ->
                var sc = textScore(nameQuery, p.name.replace(Regex("\\s*\\d+\\p{L}?$"), "")).toDouble()
                if (number != null) {
                    // ο σωστός αριθμός σπιτιού μπαίνει πρώτος, εκτός αν είναι πολύ μακριά
                    sc += if (Regex("(^|\\s)" + Regex.escape(number) + "($|\\s)").containsMatchIn(p.name)) -0.3 else 0.2
                }
                if (p.kind in placeKinds) sc -= 1.2
                if (near != null) sc += kotlin.math.log10(Geo.distance(near, p.latLon) / 1000.0 + 1.0) * 0.6
                sc += i * 0.03 + sourceBonus
                all += Scored(p, sc)
            }
        }
        add(nominatim, 0.0)
        add(photon, 0.01)
        val out = ArrayList<Place>()
        for (s in all.sortedBy { it.score }) {
            val dup = out.any { o ->
                normalize(o.name) == normalize(s.p.name) && Geo.distance(o.latLon, s.p.latLon) < (if (s.p.kind in placeKinds) 3000.0 else 300.0)
            }
            if (!dup) out += s.p
        }
        return out.take(20)
    }

    fun overpass(json: String, near: LatLon, fallbackName: String): List<Place> {
        val els: JSONArray = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: continue
            if (lat.isNaN() || lon.isNaN()) continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val name = tags.str("name").ifEmpty { tags.str("brand") }.ifEmpty { tags.str("operator") }.ifEmpty { fallbackName }
            val detail = listOf(
                tags.str("brand").takeIf { it != name },
                listOf(tags.str("addr:street"), tags.str("addr:housenumber")).filter { it.isNotEmpty() }.joinToString(" "),
                tags.str("addr:city"),
                tags.str("opening_hours").takeIf { it.isNotEmpty() }?.let { "ωράριο: $it" },
            ).filterNotNull().filter { it.isNotEmpty() }.distinct().joinToString(" · ")
            out += Place(name, detail, lat, lon, tags.str("amenity").ifEmpty { tags.str("shop") }.ifEmpty { tags.str("tourism") })
        }
        return out.sortedBy { Geo.distance(near, it.latLon) }
    }
}
