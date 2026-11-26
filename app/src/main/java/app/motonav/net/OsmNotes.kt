package app.motonav.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * «Αναφορά χάρτη»: σημείωση στο OpenStreetMap (Notes API, χωρίς λογαριασμό). Τη βλέπουν οι εθελοντές
 * της περιοχής στο openstreetmap.org και διορθώνουν τον χάρτη· η διόρθωση έρχεται και σε εμάς
 * (χάρτης, διασταυρώσεις, σημεία) στην επόμενη ανανέωση.
 */
object OsmNotes {
    enum class Kind(val label: String, val en: String) {
        MISSING_ROAD("Λείπει δρόμος", "Missing road"),
        WRONG_NAME("Λάθος όνομα δρόμου", "Wrong street name"),
        MISSING_NUMBER("Λείπουν αριθμοί σπιτιών", "Missing house numbers"),
        CLOSED("Ο δρόμος είναι κλειστός / δεν περνάει", "Road closed / impassable"),
        ONEWAY("Λάθος μονόδρομος", "Wrong one-way"),
        SURFACE("Χωματόδρομος / άσφαλτος λάθος", "Wrong surface (dirt/paved)"),
        PLACE("Λείπει ή είναι λάθος μαγαζί/σημείο", "Missing or wrong place"),
        OTHER("Άλλο", "Other"),
    }

    /** Επιστρέφει το id της σημείωσης. Πετάει IOException χωρίς internet. */
    suspend fun create(lat: Double, lon: Double, kind: Kind, text: String): Long = withContext(Dispatchers.IO) {
        val body = buildString {
            append(kind.label).append(" / ").append(kind.en)
            if (text.isNotBlank()) append("\n\n").append(text.trim())
            append("\n\n#MotoNav (αναφορά από αναβάτη, Android)")
        }
        val url = URL(
            String.format(Locale.US, "https://api.openstreetmap.org/api/0.6/notes.json?lat=%.6f&lon=%.6f&text=", lat, lon) +
                URLEncoder.encode(body, "UTF-8"),
        )
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "MotoNav/1.0 (Android; https://github.com/Mind-osc/motonav-)")
            doOutput = true
        }
        try {
            c.outputStream.use { }
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("OSM $code")
            val json = c.inputStream.bufferedReader().readText()
            org.json.JSONObject(json).optJSONObject("properties")?.optLong("id") ?: 0L
        } finally {
            c.disconnect()
        }
    }
}
