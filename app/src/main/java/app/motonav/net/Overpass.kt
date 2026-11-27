package app.motonav.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Overpass (δεδομένα OpenStreetMap). Ο κεντρικός server συχνά δεν προλαβαίνει (HTTP 429/502/503/504),
 * οπότε δοκιμάζουμε με τη σειρά και τους δημόσιους «καθρέφτες». Ο τελευταίος που απάντησε μπαίνει πρώτος.
 */
object Overpass {
    val SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    @Volatile private var preferred = 0

    /** Δεν φταίει το internet σου, φταίει ο server: τότε αξίζει να δοκιμάσουμε άλλον. */
    fun retryable(code: Int) = code == 429 || code in 500..599

    fun query(q: String, readTimeoutMs: Int = 25_000): String {
        var last: Exception? = null
        val order = SERVERS.indices.map { (it + preferred) % SERVERS.size }
        for (i in order) {
            val c = URL(SERVERS[i]).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 7_000
                c.readTimeout = readTimeoutMs
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("User-Agent", "Streetwolf/0.1 (personal Android app)")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                val code = c.responseCode
                if (code in 200..299) {
                    preferred = i
                    return c.inputStream.bufferedReader().use { it.readText() }
                }
                last = OverpassBusy(code)
                if (!retryable(code)) throw last
            } catch (e: OverpassBusy) {
                if (!retryable(e.code)) throw e
            } catch (e: java.net.UnknownHostException) {
                // χωρίς internet: δεν έχει νόημα να δοκιμάσουμε τους άλλους
                throw NoInternet()
            } catch (e: IOException) {
                last = e
            } finally {
                c.disconnect()
            }
        }
        throw last ?: OverpassBusy(503)
    }
}

class OverpassBusy(val code: Int) : IOException("Overpass HTTP $code")
class NoInternet : IOException("Δεν υπάρχει internet")

/** Μήνυμα για τον χρήστη: τι πήγε στραβά, χωρίς τεχνικά. */
fun friendlyError(e: Exception): String = when (e) {
    is NoInternet, is java.net.UnknownHostException -> "Δεν υπάρχει internet αυτή τη στιγμή."
    is OverpassBusy -> "Οι servers του χάρτη είναι φορτωμένοι. Δοκίμασε ξανά σε λίγο."
    is java.net.SocketTimeoutException -> "Ο server αργεί να απαντήσει. Δοκίμασε ξανά."
    else -> if (e.message?.startsWith("HTTP 5") == true || e.message?.startsWith("HTTP 429") == true) {
        "Ο server αναζήτησης έχει πρόβλημα. Δοκίμασε ξανά σε λίγο."
    } else {
        "Δεν έγινε αναζήτηση (${e.message})."
    }
}
