package app.motonav.data

import app.motonav.search.Place
import java.util.Locale

/** Σελιδοδείκτες σε GPX (για κοινοποίηση) και ανάγνωση από GPX/KML (για εισαγωγή). */
object BookmarkFiles {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun toGpx(listName: String, places: List<Place>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Moto Nav\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<metadata><name>${esc(listName)}</name></metadata>\n")
        places.forEach { p ->
            append(String.format(Locale.US, "<wpt lat=\"%.6f\" lon=\"%.6f\">", p.lat, p.lon))
            append("<name>${esc(p.name)}</name>")
            if (p.detail.isNotBlank()) append("<desc>${esc(p.detail)}</desc>")
            append("</wpt>\n")
        }
        append("</gpx>\n")
    }

    private val wpt = Regex("""<wpt\b([^>]*?)(/>|>(.*?)</wpt>)""", RegexOption.DOT_MATCHES_ALL)
    private val latAttr = Regex("""\blat\s*=\s*["']([-0-9.]+)["']""")
    private val lonAttr = Regex("""\blon\s*=\s*["']([-0-9.]+)["']""")
    private val nameTag = Regex("""<name>\s*(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?\s*</name>""", RegexOption.DOT_MATCHES_ALL)
    private val descTag = Regex("""<desc>\s*(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?\s*</desc>""", RegexOption.DOT_MATCHES_ALL)
    private val placemark = Regex("""<Placemark\b.*?</Placemark>""", RegexOption.DOT_MATCHES_ALL)
    private val point = Regex("""<Point\b.*?<coordinates>\s*([-0-9.]+)\s*,\s*([-0-9.]+)""", RegexOption.DOT_MATCHES_ALL)
    private val metaName = Regex("""<metadata>.*?<name>\s*(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?\s*</name>""", RegexOption.DOT_MATCHES_ALL)
    private val kmlDocName = Regex("""<Document>\s*<name>\s*(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?\s*</name>""", RegexOption.DOT_MATCHES_ALL)

    private fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&").trim()

    /** Σημεία από GPX (wpt) ή KML (Placemark με Point). Οι γραμμές/ίχνη αγνοούνται. */
    fun parse(text: String): List<Place> {
        val out = ArrayList<Place>()
        wpt.findAll(text).forEach { m ->
            val lat = latAttr.find(m.groupValues[1])?.groupValues?.get(1)?.toDoubleOrNull() ?: return@forEach
            val lon = lonAttr.find(m.groupValues[1])?.groupValues?.get(1)?.toDoubleOrNull() ?: return@forEach
            val inner = m.groupValues[3]
            val name = nameTag.find(inner)?.groupValues?.get(1)?.let(::unesc).orEmpty()
            val desc = descTag.find(inner)?.groupValues?.get(1)?.let(::unesc).orEmpty()
            out += Place(name.ifBlank { String.format(Locale.US, "%.4f, %.4f", lat, lon) }, desc.take(80), lat, lon, "saved")
        }
        placemark.findAll(text).forEach { m ->
            val pm = m.value
            val pt = point.find(pm) ?: return@forEach
            val lon = pt.groupValues[1].toDoubleOrNull() ?: return@forEach
            val lat = pt.groupValues[2].toDoubleOrNull() ?: return@forEach
            val name = nameTag.find(pm)?.groupValues?.get(1)?.let(::unesc).orEmpty()
            out += Place(name.ifBlank { String.format(Locale.US, "%.4f, %.4f", lat, lon) }, "", lat, lon, "saved")
        }
        return out.filter { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
    }

    /** Όνομα λίστας από το αρχείο (metadata του GPX ή Document του KML). */
    fun title(text: String): String? =
        (metaName.find(text) ?: kmlDocName.find(text))?.groupValues?.get(1)?.let(::unesc)?.takeIf { it.isNotBlank() }?.take(30)
}
