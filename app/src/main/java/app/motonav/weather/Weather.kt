package app.motonav.weather

import app.motonav.geo.LatLon
import app.motonav.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** Καιρός σε ένα σημείο, την ώρα που θα είσαι εκεί. atMeters: πόσα μέτρα από την αρχή της διαδρομής. */
data class WeatherPoint(
    val position: LatLon,
    val atMeters: Double,
    val etaEpochS: Long,
    val tempC: Double,
    val rainProb: Int,
    val code: Int,
    val windKmh: Double,
) {
    val sky: Sky get() = WeatherCodes.sky(code)

    /** Αξίζει προειδοποίηση για μηχανή; */
    val isBad: Boolean
        get() = rainProb >= 50 || sky == Sky.STORM || sky == Sky.SNOW || windKmh >= 45 || tempC <= 2
}

object WeatherCodes {
    /** Κωδικοί WMO, όπως τους δίνει το Open-Meteo. */
    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        in 51..57 -> Sky.DRIZZLE
        in 61..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun label(sky: Sky): String = when (sky) {
        Sky.CLEAR -> "καθαρός"
        Sky.PARTLY -> "λίγα σύννεφα"
        Sky.CLOUDY -> "συννεφιά"
        Sky.FOG -> "ομίχλη"
        Sky.DRIZZLE -> "ψιχάλα"
        Sky.RAIN -> "βροχή"
        Sky.SNOW -> "χιόνι"
        Sky.STORM -> "καταιγίδα"
    }
}

object RouteSampler {
    /**
     * Σημεία κατά μήκος της διαδρομής ανά ~everyM (αρχή και τέλος πάντα μέσα), με την ώρα που θα περάσεις.
     * Αν η διαδρομή είναι μεγάλη, μεγαλώνει το βήμα ώστε να μη ξεπερνάμε τα maxPoints.
     */
    fun sample(route: Route, startEpochS: Long, everyM: Double = 15_000.0, maxPoints: Int = 12): List<Triple<LatLon, Double, Long>> {
        val length = route.lengthM
        if (length <= 0) return emptyList()
        val step = maxOf(everyM, length / (maxPoints - 1))
        val targets = ArrayList<Double>()
        var d = 0.0
        while (d < length) {
            targets += d
            d += step
        }
        if (targets.size == 1 || length - targets.last() > step * 0.3) targets += length else targets[targets.lastIndex] = length

        val out = ArrayList<Triple<LatLon, Double, Long>>()
        var i = 0
        for (t in targets) {
            while (i < route.cumulative.size - 1 && route.cumulative[i + 1] < t) i++
            val p = route.points[minOf(i + 1, route.points.lastIndex)]
            val eta = startEpochS + (route.durationS * t / length).toLong()
            out += Triple(p, t, eta)
        }
        return out
    }
}

object OpenMeteoParser {
    /**
     * Η απάντηση είναι αντικείμενο για ένα σημείο ή λίστα για πολλά.
     * Για κάθε σημείο κρατάμε την ώρα της πρόγνωσης που είναι πιο κοντά στην ώρα που θα περάσεις.
     */
    fun parse(json: String, samples: List<Triple<LatLon, Double, Long>>): List<WeatherPoint> {
        val trimmed = json.trimStart()
        val locations: List<JSONObject> = if (trimmed.startsWith("[")) {
            val a = JSONArray(trimmed)
            (0 until a.length()).map { a.getJSONObject(it) }
        } else {
            listOf(JSONObject(trimmed))
        }
        val out = ArrayList<WeatherPoint>()
        for ((idx, loc) in locations.withIndex()) {
            val sample = samples.getOrNull(idx) ?: break
            val hourly = loc.optJSONObject("hourly") ?: continue
            val times = hourly.optJSONArray("time") ?: continue
            if (times.length() == 0) continue
            var best = 0
            var bestDiff = Long.MAX_VALUE
            for (k in 0 until times.length()) {
                val diff = abs(times.getLong(k) - sample.third)
                if (diff < bestDiff) {
                    bestDiff = diff; best = k
                }
            }
            out += WeatherPoint(
                position = sample.first,
                atMeters = sample.second,
                etaEpochS = sample.third,
                tempC = hourly.optJSONArray("temperature_2m")?.optDouble(best) ?: Double.NaN,
                rainProb = hourly.optJSONArray("precipitation_probability")?.optInt(best, 0) ?: 0,
                code = hourly.optJSONArray("weather_code")?.optInt(best, 3) ?: 3,
                windKmh = hourly.optJSONArray("wind_speed_10m")?.optDouble(best) ?: 0.0,
            )
        }
        return out
    }
}

/** Πρόγνωση από το Open-Meteo (δωρεάν, χωρίς κλειδί). Όλα τα σημεία σε μία κλήση. */
class WeatherClient {
    suspend fun forecast(samples: List<Triple<LatLon, Double, Long>>): List<WeatherPoint> = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext emptyList()
        val lats = samples.joinToString(",") { String.format(Locale.US, "%.4f", it.first.lat) }
        val lons = samples.joinToString(",") { String.format(Locale.US, "%.4f", it.first.lon) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m" +
            "&forecast_days=2&timeformat=unixtime&timezone=GMT"
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "MotoNav/0.1 (personal Android app)")
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            OpenMeteoParser.parse(body, samples)
        } finally {
            c.disconnect()
        }
    }
}
