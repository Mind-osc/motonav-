package app.motonav.spots

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Φωτογραφία για ένα μέρος και ποιος την τράβηξε (οι φωτογραφίες του Commons θέλουν αναφορά). */
data class PhotoInfo(val url: String, val credit: String, val nearby: Boolean)

/**
 * Φωτογραφία μέρους, χωρίς κλειδί:
 * 1. ό,τι γράφει το OpenStreetMap (image / wikimedia_commons / wikidata),
 * 2. αλλιώς η πιο κοντινή φωτογραφία του Wikimedia Commons σε 150 m.
 */
object SpotPhoto {
    private const val COMMONS = "https://commons.wikimedia.org/w/api.php?format=json&action=query&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=900"

    fun find(spot: Spot): PhotoInfo? {
        val tag = spot.photo.trim()
        val file: String? = when {
            tag.startsWith("File:", ignoreCase = true) -> tag
            tag.contains("wikimedia.org/wiki/File:") -> "File:" + URLDecoder.decode(tag.substringAfter("/wiki/File:"), "UTF-8")
            tag.startsWith("Q") -> runCatching { wikidataImage(tag) }.getOrNull()
            else -> null
        }
        if (file != null) runCatching { commonsFile(file) }.getOrNull()?.let { return it }
        if (tag.startsWith("http") && Regex("""\.(jpe?g|png|webp)(\?.*)?$""", RegexOption.IGNORE_CASE).containsMatchIn(tag)) {
            return PhotoInfo(tag, "Φωτογραφία από το OpenStreetMap", nearby = false)
        }
        return runCatching { nearby(spot.lat, spot.lon) }.getOrNull()
    }

    private fun wikidataImage(q: String): String? {
        val json = JSONObject(get("https://www.wikidata.org/wiki/Special:EntityData/$q.json"))
        val name = json.optJSONObject("entities")?.optJSONObject(q)?.optJSONObject("claims")?.optJSONArray("P18")
            ?.optJSONObject(0)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
        return name?.takeIf { it.isNotEmpty() }?.let { "File:$it" }
    }

    private fun commonsFile(file: String): PhotoInfo? =
        parse(get(COMMONS + "&titles=" + URLEncoder.encode(file, "UTF-8")), nearby = false)

    private fun nearby(lat: Double, lon: Double): PhotoInfo? = parse(
        get(COMMONS + String.format(Locale.US, "&generator=geosearch&ggscoord=%.6f%%7C%.6f&ggsradius=150&ggsnamespace=6&ggslimit=10", lat, lon)),
        nearby = true,
    )

    /** Από απάντηση του Commons: η πρώτη (πιο κοντινή) εικόνα με τον δημιουργό της. */
    fun parse(json: String, nearby: Boolean): PhotoInfo? {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val list = pages.keys().asSequence().mapNotNull { pages.optJSONObject(it) }
            .filter { p -> p.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")?.let { Regex("""\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true }
            .sortedBy { it.optInt("index", 0) }
            .toList()
        val p = list.firstOrNull() ?: return null
        val info = p.optJSONArray("imageinfo")!!.optJSONObject(0)
        val meta = info.optJSONObject("extmetadata")
        val artist = meta?.optJSONObject("Artist")?.optString("value").orEmpty().replace(Regex("<[^>]*>"), "").trim()
        val license = meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty().trim()
        val credit = listOf(artist.ifEmpty { null }, license.ifEmpty { null }, "Wikimedia Commons").filterNotNull().joinToString(" · ")
        return PhotoInfo(info.optString("thumburl"), credit, nearby)
    }

    fun bytes(url: String): ByteArray = open(url).use { it.readBytes() }

    private fun get(url: String): String = open(url).bufferedReader().use { it.readText() }

    private fun open(url: String): java.io.InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Streetwolf/0.1 (personal Android app)")
        if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
        return c.inputStream
    }
}
