package app.motonav.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.search.Place
import app.motonav.search.PlaceParsers
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * «Δικά σου σημεία»: αρχεία POI από άλλες πηγές (π.χ. 4GPS, Garmin, TomTom, Google My Maps).
 * Κάθε αρχείο γίνεται μία κατηγορία: φαίνεται στον χάρτη, βγαίνει στην αναζήτηση και ανοίγει/κλείνει
 * από τις Ρυθμίσεις → Χάρτης. Διαβάζει GPX, KML, KMZ, GeoJSON, CSV (Garmin: lon,lat,όνομα,περιγραφή,
 * ή με επικεφαλίδα lat/lon/name), OV2 (TomTom) και ZIP με οποιαδήποτε από αυτά μέσα.
 * Αποθήκευση: filesDir/pois/<id>.tsv (όνομα, lat, lon, περιγραφή) και pois/index.json.
 */
object ImportedPois {
    class Pt(val name: String, val lat: Double, val lon: Double, val detail: String)
    data class Collection(val id: String, val name: String, val color: Int, val visible: Boolean, val count: Int)

    private val palette = intArrayOf(
        0xFFAB47BC.toInt(), 0xFF26C6DA.toInt(), 0xFFFFCA28.toInt(), 0xFF66BB6A.toInt(),
        0xFFEF5350.toInt(), 0xFF5C6BC0.toInt(), 0xFFFF7043.toInt(), 0xFF8D6E63.toInt(),
    )

    var collections by mutableStateOf<List<Collection>>(emptyList())
        private set

    /** Αλλάζει κάθε φορά που αλλάζουν τα σημεία (για να ξαναζωγραφιστεί ο χάρτης). */
    var version by mutableStateOf(0)
        private set

    private val points = HashMap<String, List<Pt>>()
    @Volatile private var dir: File? = null

    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.filesDir, "pois").apply { mkdirs() }
        dir = d
        collections = runCatching {
            val a = JSONArray(File(d, "index.json").readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Collection(o.getString("id"), o.getString("name"), o.optInt("color", palette[0]), o.optBoolean("visible", true), o.optInt("count"))
            }
        }.getOrDefault(emptyList())
        collections.forEach { c -> points[c.id] = readPoints(File(d, c.id + ".tsv")) }
        version++
    }

    private fun readPoints(f: File): List<Pt> = runCatching {
        f.readLines().mapNotNull { line ->
            val p = line.split('\t')
            val lat = p.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = p.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null
            Pt(p[0], lat, lon, p.getOrElse(3) { "" })
        }
    }.getOrDefault(emptyList())

    private fun saveIndex() {
        val d = dir ?: return
        val a = JSONArray()
        collections.forEach { c ->
            a.put(JSONObject().put("id", c.id).put("name", c.name).put("color", c.color).put("visible", c.visible).put("count", c.count))
        }
        File(d, "index.json").writeText(a.toString())
    }

    /** Τα ορατά σημεία, με το χρώμα της κατηγορίας τους. */
    fun visible(): List<Triple<Collection, Pt, Int>> =
        collections.filter { it.visible }.flatMap { c -> points[c.id].orEmpty().mapIndexed { i, p -> Triple(c, p, i) } }

    fun point(collectionId: String, index: Int): Pt? = points[collectionId]?.getOrNull(index)

    fun setVisible(id: String, visible: Boolean) {
        collections = collections.map { if (it.id == id) it.copy(visible = visible) else it }
        saveIndex(); version++
    }

    fun delete(id: String) {
        collections = collections.filter { it.id != id }
        points.remove(id)
        dir?.let { File(it, "$id.tsv").delete() }
        saveIndex(); version++
    }

    /** Εισαγωγή αρχείου. Επιστρέφει πόσες κατηγορίες και πόσα σημεία μπήκαν. */
    fun import(context: Context, uri: Uri): Pair<Int, Int> {
        init(context)
        val fileName = displayName(context, uri) ?: "Σημεία"
        val found = ArrayList<Pair<String, List<Pt>>>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            collect(fileName, bytes, found)
        }
        var total = 0
        found.filter { it.second.isNotEmpty() }.forEach { (name, pts) ->
            add(name, pts)
            total += pts.size
        }
        return found.count { it.second.isNotEmpty() } to total
    }

    private fun add(name: String, pts: List<Pt>) {
        val d = dir ?: return
        val id = "c" + System.currentTimeMillis().toString(36) + collections.size
        File(d, "$id.tsv").writeText(
            pts.joinToString("\n") { p ->
                listOf(clean(p.name), String.format(Locale.US, "%.6f", p.lat), String.format(Locale.US, "%.6f", p.lon), clean(p.detail)).joinToString("\t")
            },
        )
        points[id] = pts
        collections = collections + Collection(id, name.take(40), palette[collections.size % palette.size], true, pts.size)
        saveIndex(); version++
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun title(fileName: String) = fileName.substringAfterLast('/').substringBeforeLast('.')
        .replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifBlank { "Σημεία" }

    /** Ένα αρχείο → μία κατηγορία· ZIP/KMZ → μία κατηγορία για κάθε αρχείο μέσα. */
    private fun collect(fileName: String, bytes: ByteArray, out: MutableList<Pair<String, List<Pt>>>) {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            ZipInputStream(bytes.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory) {
                        val inner = z.readBytes()
                        // KMZ: το doc.kml παίρνει το όνομα του KMZ
                        val name = if (ext == "kmz") fileName else e.name
                        collect(name, inner, out)
                    }
                }
            }
            return
        }
        val pts = when {
            ext == "ov2" -> parseOv2(bytes)
            ext == "csv" || ext == "txt" || ext == "asc" -> parseCsv(String(bytes, charset(bytes)))
            else -> {
                val text = String(bytes, charset(bytes))
                val t = text.trimStart()
                if (t.startsWith("<") || t.startsWith("{")) {
                    BookmarkFiles.parse(text).map { Pt(it.name, it.lat, it.lon, it.detail) }
                } else parseCsv(text)
            }
        }
        if (pts.isNotEmpty()) out += title(fileName) to pts
    }

    /** UTF-8 ή, αν δεν είναι έγκυρο, Windows-1253 (παλιά ελληνικά αρχεία Garmin/iGo). */
    private fun charset(bytes: ByteArray): java.nio.charset.Charset {
        val dec = Charsets.UTF_8.newDecoder()
        return runCatching { dec.decode(ByteBuffer.wrap(bytes)); Charsets.UTF_8 }
            .getOrElse { runCatching { java.nio.charset.Charset.forName("windows-1253") }.getOrDefault(Charsets.ISO_8859_1) }
    }

    /** Μία γραμμή CSV με εισαγωγικά. */
    private fun splitCsv(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == sep && !q -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    /**
     * CSV: με επικεφαλίδα (lat/latitude/y/πλάτος, lon/lng/longitude/x/μήκος, name/όνομα, desc) ή χωρίς.
     * Χωρίς επικεφαλίδα: οι δύο πρώτοι αριθμοί είναι οι συντεταγμένες· σειρά Garmin (lon,lat) εκτός αν
     * φαίνεται ότι είναι lat,lon (π.χ. 37.98,23.72 για την Ελλάδα).
     */
    fun parseCsv(text: String): List<Pt> {
        val lines = text.lines().map { it.trim().trimStart('﻿') }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }
        if (lines.isEmpty()) return emptyList()
        val sep = listOf(',', ';', '\t', '|').maxBy { s -> lines.take(5).sumOf { l -> l.count { it == s } } }
        val head = splitCsv(lines[0], sep).map { it.lowercase(Locale.ROOT) }
        fun col(vararg names: String) = head.indexOfFirst { h -> names.any { h == it || h.startsWith(it) } }
        var latI = col("lat", "latitude", "y", "πλάτος")
        var lonI = col("lon", "lng", "long", "longitude", "x", "μήκος")
        var nameI = col("name", "όνομα", "title", "τίτλος", "label")
        var descI = col("desc", "description", "περιγραφή", "comment", "address", "διεύθυνση")
        val hasHeader = latI >= 0 && lonI >= 0
        val body = if (hasHeader) lines.drop(1) else lines
        if (!hasHeader) {
            // Χωρίς επικεφαλίδα: εντοπίζουμε τις δύο πρώτες αριθμητικές στήλες.
            val f = splitCsv(body.first(), sep)
            val nums = f.indices.filter { f[it].replace(',', '.').toDoubleOrNull() != null }
            if (nums.size < 2) return emptyList()
            val a = f[nums[0]].replace(',', '.').toDouble()
            val b = f[nums[1]].replace(',', '.').toDouble()
            val latFirst = (a in 34.0..42.5 && b in 19.0..30.0) || kotlin.math.abs(b) > 90
            if (latFirst) { latI = nums[0]; lonI = nums[1] } else { lonI = nums[0]; latI = nums[1] }
            val texts = f.indices.filter { it !in nums.take(2) }
            nameI = texts.getOrElse(0) { -1 }
            descI = texts.getOrElse(1) { -1 }
        }
        return body.mapNotNull { line ->
            val f = splitCsv(line, sep)
            val lat = f.getOrNull(latI)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = f.getOrNull(lonI)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            val name = f.getOrNull(nameI).orEmpty().ifBlank { String.format(Locale.US, "%.4f, %.4f", lat, lon) }
            Pt(name, lat, lon, f.getOrNull(descI).orEmpty().take(80))
        }
    }

    /**
     * TomTom OV2: εγγραφές με 1 byte τύπο. 0 = διαγραμμένη, 1 = «skipper» (21 byte),
     * 2/3 = σημείο: μήκος (int32 LE), lon, lat (int32 LE × 1e-5), όνομα με 0 στο τέλος.
     */
    fun parseOv2(bytes: ByteArray): List<Pt> {
        val out = ArrayList<Pt>()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        while (bb.remaining() >= 5) {
            val start = bb.position()
            val type = bb.get().toInt() and 0xFF
            when (type) {
                1 -> { if (bb.remaining() < 20) break; bb.position(start + 21) }
                0, 2, 3 -> {
                    val len = bb.int
                    if (len < 13 || start + len > bytes.size) break
                    if (type != 0) {
                        val lon = bb.int / 100000.0
                        val lat = bb.int / 100000.0
                        val raw = bytes.copyOfRange(start + 13, start + len)
                        val end = raw.indexOf(0).let { if (it < 0) raw.size else it }
                        val text = String(raw, 0, end, charset(raw.copyOf(end)))
                        val name = text.substringBefore('>').trim()
                        val detail = text.substringAfter('>', "").trim()
                        if (lat in -90.0..90.0 && lon in -180.0..180.0) out += Pt(name.ifBlank { "Σημείο" }, lat, lon, detail.take(80))
                    }
                    bb.position(start + len)
                }
                else -> break
            }
        }
        return out
    }

    /** Αναζήτηση στα δικά σου σημεία (για τη γενική αναζήτηση). */
    fun search(query: String, near: LatLon?, limit: Int = 6): List<Place> {
        val q = PlaceParsers.normalize(query)
        if (q.length < 2) return emptyList()
        val hits = ArrayList<Pair<Place, Double>>()
        collections.forEach { c ->
            points[c.id].orEmpty().forEach { p ->
                if (PlaceParsers.normalize(p.name).contains(q)) {
                    val pl = Place(p.name, c.name + (if (p.detail.isNotBlank()) " · " + p.detail else ""), p.lat, p.lon, "imported")
                    hits += pl to (near?.let { Geo.distance(it, LatLon(p.lat, p.lon)) } ?: 0.0)
                }
            }
        }
        return hits.sortedBy { it.second }.take(limit).map { it.first }
    }
}
