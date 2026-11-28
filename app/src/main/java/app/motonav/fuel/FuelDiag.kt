package app.motonav.fuel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import app.motonav.i18n.trf

/**
 * Τι έγινε στην τελευταία αναζήτηση βενζινάδικων στη διαδρομή, για τις Ρυθμίσεις
 * (για να φαίνεται γιατί δεν βρίσκει: server, internet, κλειδί…).
 */
object FuelDiag {
    var osm by mutableStateOf("—")
        private set
    var tomtom by mutableStateOf("—")
        private set
    var result by mutableStateOf("—")
        private set

    private fun now() = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    fun describe(e: Throwable): String {
        val name = e.javaClass.simpleName
        val msg = e.message?.take(160).orEmpty()
        return if (msg.isBlank()) name else "$name: $msg"
    }

    fun osm(ok: Int?, error: Throwable?, ms: Long, boxes: Int) {
        osm = "${now()} · " + (if (error == null) trf("OK, {0} πρατήρια", ok) else trf("ΛΑΘΟΣ {0}", describe(error))) + trf(" · {0}″ · {1} κουτάκια", ms / 1000.0, boxes)
    }

    fun tomtom(ok: Int?, error: Throwable?, ms: Long, hasKey: Boolean) {
        tomtom = if (!hasKey) trf("{0} · χωρίς κλειδί TomTom", now()) else
            "${now()} · " + (if (error == null) trf("OK, {0} πρατήρια", ok) else trf("ΛΑΘΟΣ {0}", describe(error))) + " · ${ms / 1000.0}″"
    }

    fun result(onRoute: Int, routeKm: Double) {
        result = trf("{0} · {1} πάνω στη διαδρομή ({2} km)", now(), onRoute, "%.1f".format(routeKm))
    }
}
