package app.motonav.trips

import android.content.Context
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import org.json.JSONObject

/**
 * Μετρητής ταξιδιού A/B, όπως στο ταμπλό της μηχανής (και στο Trip A του DMD2).
 * A: μηδενίζεται μόνο του όταν ξεκινάς ξανά μετά από 3+ ώρες. B: μόνο όταν τον μηδενίσεις εσύ
 * (π.χ. στο γέμισμα: πόσα km με το ρεζερβουάρ). Μετράνε πάντα, με ή χωρίς πλοήγηση· ⏸ σταματάει τον έναν.
 */
data class TripMeterData(
    val startMs: Long = 0L,
    val distanceM: Double = 0.0,
    val movingS: Double = 0.0,
    /** Χρόνος που ήταν σε παύση (δεν μετράει στο συνολικό). */
    val pausedS: Double = 0.0,
    val maxKmh: Double = 0.0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val paused: Boolean = false,
    val pausedAtMs: Long = 0L,
    val lastFixMs: Long = 0L,
) {
    /** Συνολικός χρόνος από το μηδέν, χωρίς τις παύσεις. */
    fun totalS(nowMs: Long): Double {
        if (startMs == 0L) return 0.0
        val end = if (paused) pausedAtMs else nowMs
        return ((end - startMs) / 1000.0 - pausedS).coerceAtLeast(movingS)
    }
    fun stoppedS(nowMs: Long) = (totalS(nowMs) - movingS).coerceAtLeast(0.0)
    val avgMovingKmh: Double get() = if (movingS > 1) distanceM / movingS * 3.6 else 0.0
    fun avgTotalKmh(nowMs: Long): Double = totalS(nowMs).let { if (it > 1) distanceM / it * 3.6 else 0.0 }

    fun toJson(): String = JSONObject()
        .put("start", startMs).put("dist", distanceM).put("moving", movingS).put("pausedS", pausedS)
        .put("vmax", maxKmh).put("up", ascentM).put("down", descentM).put("paused", paused)
        .put("pausedAt", pausedAtMs).put("last", lastFixMs).toString()

    companion object {
        fun fromJson(s: String?): TripMeterData = runCatching {
            val o = JSONObject(s ?: return TripMeterData())
            TripMeterData(
                o.optLong("start"), o.optDouble("dist"), o.optDouble("moving"), o.optDouble("pausedS"),
                o.optDouble("vmax"), o.optDouble("up"), o.optDouble("down"), o.optBoolean("paused"),
                o.optLong("pausedAt"), o.optLong("last"),
            )
        }.getOrDefault(TripMeterData())
    }
}

object TripMeter {
    /** Μετά από τόσο χωρίς στίγμα, το A ξεκινάει από την αρχή. */
    const val AUTO_RESET_MS = 3 * 3600_000L

    var a by mutableStateOf(TripMeterData())
        private set
    var b by mutableStateOf(TripMeterData())
        private set
    /** Ποιος φαίνεται στο πάνελ: false = A, true = B. */
    var showB by mutableStateOf(false)

    private var prefs: android.content.SharedPreferences? = null
    private var last: LatLon? = null
    private var lastMs = 0L
    private var altSmooth: Double? = null
    private var altRef: Double? = null
    private var savedAt = 0L

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("trip_meter", Context.MODE_PRIVATE)
        prefs = p
        a = TripMeterData.fromJson(p.getString("a", null))
        b = TripMeterData.fromJson(p.getString("b", null))
        showB = p.getBoolean("showB", false)
    }

    private fun save(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - savedAt < 10_000) return
        savedAt = now
        prefs?.edit()?.putString("a", a.toJson())?.putString("b", b.toJson())?.putBoolean("showB", showB)?.apply()
    }

    /** Κάθε στίγμα GPS. */
    fun add(loc: Location) {
        val now = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        if (loc.hasAccuracy() && loc.accuracy > 40f) return
        val p = LatLon(loc.latitude, loc.longitude)
        // A: νέα βόλτα μετά από πολλές ώρες
        if (a.lastFixMs > 0 && now - a.lastFixMs > AUTO_RESET_MS && !a.paused) a = TripMeterData()
        var d = 0.0
        var dt = 0.0
        last?.let { q ->
            dt = (now - lastMs) / 1000.0
            val dd = Geo.distance(q, p)
            // όχι άλματα GPS και όχι κενά (εφαρμογή κλειστή): μόνο διαδοχικά στίγματα
            if (dt in 0.2..30.0 && dd / dt < 83) d = dd else dt = 0.0
        }
        last = p
        lastMs = now
        val v = if (loc.hasSpeed()) loc.speed.toDouble() else if (dt > 0) d / dt else 0.0
        // Υψόμετρο εξομαλυμένο, με υστέρηση 3 m (όχι θόρυβος του GPS)
        var up = 0.0
        var down = 0.0
        if (loc.hasAltitude()) {
            val s = altSmooth?.let { it + 0.2 * (loc.altitude - it) } ?: loc.altitude
            altSmooth = s
            val ref = altRef ?: s.also { altRef = it }
            if (s - ref >= 3) { up = s - ref; altRef = s } else if (ref - s >= 3) { down = ref - s; altRef = s }
        }
        fun step(m: TripMeterData): TripMeterData {
            if (m.paused) return m
            val start = if (m.startMs == 0L) now else m.startMs
            val moving = d > 0 && v > 1.5
            return m.copy(
                startMs = start,
                distanceM = m.distanceM + d,
                movingS = m.movingS + if (moving) dt else 0.0,
                maxKmh = if (v * 3.6 in m.maxKmh..300.0) v * 3.6 else m.maxKmh,
                ascentM = m.ascentM + up,
                descentM = m.descentM + down,
                lastFixMs = now,
            )
        }
        a = step(a)
        b = step(b)
        save()
    }

    private fun update(which: Boolean, f: (TripMeterData) -> TripMeterData) {
        if (which) b = f(b) else a = f(a)
        save(force = true)
    }

    fun togglePause(useB: Boolean = showB) = update(useB) { m ->
        val now = System.currentTimeMillis()
        if (m.paused) m.copy(paused = false, pausedS = m.pausedS + (now - m.pausedAtMs) / 1000.0)
        else m.copy(paused = true, pausedAtMs = now)
    }

    fun reset(useB: Boolean = showB) = update(useB) { TripMeterData() }

    fun swap() {
        showB = !showB
        save(force = true)
    }
}
