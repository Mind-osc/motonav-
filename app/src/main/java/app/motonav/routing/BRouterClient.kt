package app.motonav.routing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import app.motonav.geo.LatLon
import btools.routingapp.IBRouterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

enum class RouteType {
    /** Offline (BRouter), η πιο γρήγορη χωρίς κίνηση. */
    FASTEST,
    /** Offline (BRouter), με στροφές. */
    TWISTY,
    /** Online (TomTom), η πιο γρήγορη με την τωρινή κίνηση. */
    TRAFFIC,
    /** Offline (BRouter), δικό μας προφίλ: προτιμά χωματόδρομους όπου επιτρέπεται. */
    DIRT,
}

/**
 * Υπολογισμός διαδρομής χωρίς internet μέσω της εφαρμογής BRouter (btools.routingapp).
 * Το BRouter κρατάει τα δεδομένα διαδρομών (.rd5) και τα προφίλ. Εμείς του στέλνουμε σημεία
 * και παίρνουμε πίσω GeoJSON με τη γραμμή και τις οδηγίες στροφών.
 */
class BRouterClient(context: Context) {
    private val app = context.applicationContext

    fun isInstalled(): Boolean =
        runCatching { app.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    suspend fun route(
        waypoints: List<LatLon>,
        headingDeg: Float? = null,
        avoidMotorways: Boolean = false,
        type: RouteType = RouteType.FASTEST,
        avoidTolls: Boolean = false,
    ): Route = withService { service ->
        when (type) {
            RouteType.FASTEST -> {
                val extra = mutableListOf("vmax=110")
                if (avoidMotorways) extra += "avoid_motorways=1"
                if (avoidTolls) extra += "avoid_toll=1"
                call(service, waypoints, headingDeg, extra, alternative = 0)
            }
            RouteType.TWISTY -> twisty(service, waypoints, headingDeg, avoidTolls)
            RouteType.TRAFFIC -> error("Η διαδρομή με κίνηση γίνεται από το TomTom, όχι από το BRouter")
            RouteType.DIRT -> dirt(service, waypoints, headingDeg, avoidTolls)
        }
    }

    /**
     * Κυκλική βόλτα με στροφές: δοκιμάζει κύκλο προς 4 κατευθύνσεις (όσες πέφτουν σε θάλασσα αποτυγχάνουν μόνες τους),
     * κρατάει αυτόν με τις περισσότερες στροφές κοντά στον χρόνο που ζήτησες, και αν βγήκε πολύ μικρός ή μεγάλος
     * τον ξαναϋπολογίζει μία φορά με διορθωμένη ακτίνα. Επιστρέφει τη διαδρομή και τα ενδιάμεσα σημεία της.
     */
    suspend fun loop(start: LatLon, targetS: Double, avoidTolls: Boolean = false): Pair<Route, List<LatLon>> = withService { service ->
        val extra = listOf("vmax=80", "avoid_motorways=1", "consider_town=1") +
            (if (avoidTolls) listOf("avoid_toll=1") else emptyList())
        val lengthM = targetS * LoopPlanner.AVG_KMH / 3.6
        val results = ArrayList<Triple<Route, List<LatLon>, Int>>()
        var firstError: RoutingException? = null
        for (dir in LoopPlanner.DIRECTIONS) {
            val via = LoopPlanner.loop(start, lengthM, dir)
            try {
                results += Triple(call(service, listOf(start) + via + start, null, extra, alternative = 0), via, dir)
            } catch (e: RoutingException) {
                if (e.kind == RoutingException.Kind.NOT_INSTALLED || e.kind == RoutingException.Kind.NO_PROFILE) throw e
                if (firstError == null) firstError = e
            }
        }
        val best = LoopPlanner.pick(results.map { it.first }, targetS)
            ?: throw (firstError ?: RoutingException("Δεν βρέθηκε κυκλική διαδρομή εδώ γύρω.", RoutingException.Kind.OTHER))
        val chosen = results.first { it.first === best }
        val ratio = best.durationS / targetS
        if (ratio < 0.8 || ratio > 1.25) {
            val via = LoopPlanner.loop(start, lengthM / ratio, chosen.third)
            runCatching { call(service, listOf(start) + via + start, null, extra, alternative = 0) }.getOrNull()?.let { r ->
                if (kotlin.math.abs(r.durationS / targetS - 1.0) < kotlin.math.abs(ratio - 1.0)) return@withService r to via
            }
        }
        best to chosen.second
    }

    /**
     * Διαδρομή με στροφές: χωρίς αυτοκινητόδρομους, παρακάμπτοντας πόλεις, χαμηλότερη ταχύτητα-στόχος
     * (ώστε να μη «κερδίζουν» οι γρήγοροι ευθείς δρόμοι). Ζητάμε έως 4 εναλλακτικές
     * και κρατάμε αυτή με τις περισσότερες στροφές ανά χιλιόμετρο, αν δεν είναι πολύ πιο αργή.
     */
    private suspend fun twisty(service: IBRouterService, waypoints: List<LatLon>, headingDeg: Float?, avoidTolls: Boolean): Route {
        val extra = listOf("vmax=80", "avoid_motorways=1", "consider_town=1") +
            (if (avoidTolls) listOf("avoid_toll=1") else emptyList())
        val candidates = ArrayList<Route>()
        for (alt in 0..3) {
            try {
                candidates += call(service, waypoints, headingDeg, extra, alternative = alt)
            } catch (e: RoutingException) {
                if (alt == 0) throw e // χωρίς ούτε μία διαδρομή: λάθος
                break // λιγότερες εναλλακτικές από 4: εντάξει
            }
        }
        return TwistyPicker.pick(candidates).also { chosen -> lastTwisty = candidates.filter { it !== chosen } }
    }

    /** Χωματόδρομοι: το προφίλ moto-dirt.brf (από το car-vario) στέλνεται ολόκληρο στο BRouter. */
    /** Οι υπόλοιπες υποψήφιες της τελευταίας διαδρομής με στροφές (τις έχουμε ήδη, δεν ξαναϋπολογίζονται). */
    private var lastTwisty: List<Route> = emptyList()

    /**
     * Εναλλακτικές για την ίδια διαδρομή (το BRouter δίνει έως 3 με alternativeidx 1..3).
     * Όσες δεν βγουν (λιγότερες επιλογές στην περιοχή) απλώς λείπουν.
     */
    suspend fun alternatives(
        waypoints: List<LatLon>,
        headingDeg: Float?,
        avoidMotorways: Boolean,
        type: RouteType,
        avoidTolls: Boolean,
    ): List<Route> {
        if (type == RouteType.TWISTY) return lastTwisty
        return withService { service ->
            val out = ArrayList<Route>()
            for (alt in 1..3) {
                val r = try {
                    when (type) {
                        RouteType.DIRT -> dirt(service, waypoints, headingDeg, avoidTolls, alt)
                        else -> {
                            val extra = mutableListOf("vmax=110")
                            if (avoidMotorways) extra += "avoid_motorways=1"
                            if (avoidTolls) extra += "avoid_toll=1"
                            call(service, waypoints, headingDeg, extra, alternative = alt)
                        }
                    }
                } catch (e: RoutingException) {
                    break
                }
                out += r
            }
            out
        }
    }

    private suspend fun dirt(service: IBRouterService, waypoints: List<LatLon>, headingDeg: Float?, avoidTolls: Boolean, alternative: Int = 0): Route {
        val profile = app.assets.open("brouter/moto-dirt.brf").bufferedReader().use { it.readText() }
        val params = Bundle().apply {
            putString("lonlats", waypoints.joinToString("|") { String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat) })
            putString("remoteProfile", profile)
            putString("trackFormat", "json")
            putString("timode", "2")
            putString("maxRunningTime", "90")
            putString("alternativeidx", alternative.toString())
            if (avoidTolls) putString("extraParams", "avoid_toll=1")
            headingDeg?.let { putString("heading", it.roundToInt().toString()) }
        }
        val result = withContext(Dispatchers.IO) {
            try {
                service.getTrackFromParams(params)
            } catch (e: Exception) {
                throw RoutingException(e.message ?: e.toString(), RoutingException.Kind.OTHER)
            }
        } ?: throw RoutingException("Το BRouter δεν έστειλε διαδρομή.", RoutingException.Kind.OTHER)
        if (!result.trimStart().startsWith("{")) throw classify(result)
        return withContext(Dispatchers.Default) { BRouterJson.parse(result) }
    }

    private suspend fun call(
        service: IBRouterService,
        waypoints: List<LatLon>,
        headingDeg: Float?,
        extra: List<String>,
        alternative: Int,
    ): Route {
        // Πρώτα το προφίλ που δούλεψε την τελευταία φορά, μετά τα υπόλοιπα με τη σειρά.
        val order = (listOfNotNull(workingProfile) + PROFILES).distinct()
        var lastError: RoutingException? = null
        for (profile in order) {
            val params = Bundle().apply {
                putString("lonlats", waypoints.joinToString("|") {
                    String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat)
                })
                putString("profile", profile)
                putString("trackFormat", "json")
                putString("timode", "2")
                putString("maxRunningTime", "60")
                putString("alternativeidx", alternative.toString())
                putString("extraParams", extra.joinToString("&"))
                headingDeg?.let { putString("heading", it.roundToInt().toString()) }
            }
            val result = withContext(Dispatchers.IO) {
                try {
                    service.getTrackFromParams(params)
                } catch (e: Exception) {
                    throw RoutingException(e.message ?: e.toString(), RoutingException.Kind.OTHER)
                }
            } ?: throw RoutingException("Το BRouter δεν έστειλε διαδρομή.", RoutingException.Kind.OTHER)

            if (result.trimStart().startsWith("{")) {
                workingProfile = profile
                return withContext(Dispatchers.Default) { BRouterJson.parse(result) }
            }
            val err = classify(result)
            if (err.kind != RoutingException.Kind.NO_PROFILE) throw err
            lastError = err
        }
        throw lastError ?: RoutingException("Δεν βρέθηκε προφίλ αυτοκινήτου στο BRouter.", RoutingException.Kind.NO_PROFILE)
    }

    private suspend fun <T> withService(block: suspend (IBRouterService) -> T): T {
        if (!isInstalled()) {
            throw RoutingException("Δεν βρέθηκε η εφαρμογή BRouter.", RoutingException.Kind.NOT_INSTALLED)
        }
        val conn = Connection()
        val service = try {
            withTimeout(15_000) { connect(conn) }
        } catch (e: RoutingException) {
            throw e
        } catch (e: Exception) {
            runCatching { app.unbindService(conn) }
            throw RoutingException("Το BRouter δεν απάντησε. Άνοιξέ το μία φορά και ξαναδοκίμασε.", RoutingException.Kind.OTHER)
        }
        try {
            return block(service)
        } finally {
            runCatching { app.unbindService(conn) }
        }
    }

    private fun classify(message: String): RoutingException {
        val m = message.lowercase(Locale.ROOT)
        val kind = when {
            "datafile" in m || ".rd5" in m || "segment" in m -> RoutingException.Kind.NO_DATA
            "profile" in m && "not" in m -> RoutingException.Kind.NO_PROFILE
            else -> RoutingException.Kind.OTHER
        }
        return RoutingException(message.trim(), kind)
    }

    private suspend fun connect(conn: Connection): IBRouterService =
        suspendCancellableCoroutine { cont ->
            conn.onConnected = { binder ->
                if (cont.isActive) cont.resume(IBRouterService.Stub.asInterface(binder))
            }
            val intent = Intent().setClassName(PACKAGE, "$PACKAGE.BRouterService")
            val ok = runCatching { app.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
            if (!ok) {
                cont.resumeWithException(
                    RoutingException("Δεν μπόρεσα να συνδεθώ με το BRouter.", RoutingException.Kind.OTHER)
                )
            }
        }

    private class Connection : ServiceConnection {
        var onConnected: ((IBinder) -> Unit)? = null
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            service?.let { onConnected?.invoke(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    companion object {
        const val PACKAGE = "btools.routingapp"

        // Προφίλ αυτοκινήτου του BRouter. Η εφαρμογή BRouter δεν περιέχει το car-vario,
        // αλλά φτιάχνει από αυτό τα car-fast / car-eco (ίδιες ρυθμίσεις, άλλο vmax).
        val PROFILES = listOf("car-fast", "car-eco", "car-vario")

        @Volatile
        private var workingProfile: String? = null
    }
}

/** Διαλέγει τη διαδρομή με τις περισσότερες στροφές, μέχρι 60% πιο αργή από την πιο γρήγορη εναλλακτική. */
/** Γεωμετρία για κυκλική βόλτα. */
object LoopPlanner {
    /** Μέση ταχύτητα σε δρόμο με στροφές, για να βγει μήκος από τις ώρες. */
    const val AVG_KMH = 50.0
    /** Οι δρόμοι είναι πιο μακριοί από τον κύκλο στον χάρτη. */
    const val ROAD_FACTOR = 1.4
    val DIRECTIONS = listOf(0, 90, 180, 270)

    fun offset(p: LatLon, bearingDeg: Double, distM: Double): LatLon {
        val r = 6371008.8
        val br = Math.toRadians(bearingDeg)
        val la1 = Math.toRadians(p.lat)
        val lo1 = Math.toRadians(p.lon)
        val d = distM / r
        val la2 = kotlin.math.asin(kotlin.math.sin(la1) * kotlin.math.cos(d) + kotlin.math.cos(la1) * kotlin.math.sin(d) * kotlin.math.cos(br))
        val lo2 = lo1 + kotlin.math.atan2(
            kotlin.math.sin(br) * kotlin.math.sin(d) * kotlin.math.cos(la1),
            kotlin.math.cos(d) - kotlin.math.sin(la1) * kotlin.math.sin(la2),
        )
        return LatLon(Math.toDegrees(la2), Math.toDegrees(lo2))
    }

    /** Δύο σημεία πάνω σε κύκλο που περνάει από την αφετηρία και «γέρνει» προς το dirDeg. */
    fun loop(start: LatLon, lengthM: Double, dirDeg: Int): List<LatLon> {
        val radius = lengthM / ROAD_FACTOR / (2 * Math.PI)
        val centre = offset(start, dirDeg.toDouble(), radius)
        val back = dirDeg + 180.0
        return listOf(offset(centre, back + 120.0, radius), offset(centre, back + 240.0, radius))
    }

    /** Οι περισσότερες στροφές, από όσες είναι κοντά στον χρόνο-στόχο (60%–150%)· αλλιώς η πιο κοντινή σε χρόνο. */
    fun pick(routes: List<Route>, targetS: Double): Route? {
        if (routes.isEmpty()) return null
        val ok = routes.filter { it.durationS in targetS * 0.6..targetS * 1.5 }
        return if (ok.isNotEmpty()) ok.maxBy { it.twistinessDegPerKm } else routes.minBy { kotlin.math.abs(it.durationS - targetS) }
    }
}

object TwistyPicker {
    const val MAX_SLOWDOWN = 1.6

    fun pick(candidates: List<Route>): Route {
        require(candidates.isNotEmpty())
        val fastest = candidates.minOf { it.durationS }
        return candidates
            .filter { it.durationS <= fastest * MAX_SLOWDOWN }
            .maxBy { it.twistinessDegPerKm }
    }
}
