package app.motonav.map

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Ό,τι βλέπει τώρα ο χάρτης: νότος, δύση, βορράς, ανατολή. Το γράφει η οθόνη του χάρτη. */
object MapBus {
    @Volatile var visible: DoubleArray? = null
}

/**
 * Χάρτης χωρίς internet: το MapLibre κατεβάζει τα πλακίδια μιας περιοχής και τα κρατάει στο κινητό.
 * Κάθε περιοχή κατεβαίνει για το σκούρο και για το ανοιχτό στυλ (τα πλακίδια είναι κοινά, αλλάζουν μόνο λίγα αρχεία).
 */
object OfflineMaps {
    const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
    const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"

    /** Ελλάδα: νότος, δύση, βορράς, ανατολή. */
    val GREECE = doubleArrayOf(34.7, 19.3, 41.8, 28.3)
    const val GREECE_MAX_ZOOM = 12
    const val AREA_MAX_ZOOM = 14
    const val AREA_MAX_TILES = 30_000L

    /** Γειτονικές χώρες για ταξίδια (κύριοι δρόμοι και πόλεις, όπως η Ελλάδα): νότος, δύση, βορράς, ανατολή. */
    class Country(val name: String, val bounds: DoubleArray)
    val COUNTRIES = listOf(
        Country("Κύπρος", doubleArrayOf(34.5, 32.2, 35.75, 34.65)),
        Country("Αλβανία", doubleArrayOf(39.6, 19.2, 42.7, 21.1)),
        Country("Βόρεια Μακεδονία", doubleArrayOf(40.8, 20.4, 42.4, 23.1)),
        Country("Βουλγαρία", doubleArrayOf(41.2, 22.3, 44.3, 28.7)),
        Country("Τουρκία (δυτική)", doubleArrayOf(36.0, 26.0, 42.2, 32.0)),
        Country("Μαυροβούνιο", doubleArrayOf(41.8, 18.4, 43.6, 20.4)),
        Country("Σερβία", doubleArrayOf(42.2, 18.8, 46.2, 23.1)),
        Country("Βοσνία-Ερζεγοβίνη", doubleArrayOf(42.5, 15.7, 45.3, 19.7)),
        Country("Κροατία", doubleArrayOf(42.3, 13.4, 46.6, 19.5)),
        Country("Ρουμανία", doubleArrayOf(43.6, 20.2, 48.3, 29.8)),
        Country("Ιταλία (νότια)", doubleArrayOf(36.6, 12.3, 42.0, 18.6)),
        Country("Ιταλία (βόρεια)", doubleArrayOf(42.0, 6.6, 47.1, 14.0)),
    )

    /** Πόσα πλακίδια έχει μια περιοχή από ζουμ 0 ως maxZoom. */
    fun tileCount(south: Double, west: Double, north: Double, east: Double, maxZoom: Int): Long {
        var total = 0L
        for (z in 0..maxZoom) {
            val n = (1 shl z).toDouble()
            fun x(lon: Double) = floor((lon + 180.0) / 360.0 * n).coerceIn(0.0, n - 1)
            fun y(lat: Double): Double {
                val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
                return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n).coerceIn(0.0, n - 1)
            }
            total += ((x(east) - x(west) + 1) * (y(south) - y(north) + 1)).toLong()
        }
        return total
    }

    class Saved(val name: String, val ids: List<Long>, val tiles: Long, val bytes: Long, val complete: Boolean)

    /** Πρόοδος της λήψης που τρέχει (ή null). Η οθόνη τη διαβάζει ως state. */
    var progress by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf<List<Saved>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set

    private fun meta(name: String, style: String) = JSONObject().put("name", name).put("style", style).toString().toByteArray()
    private fun nameOf(r: OfflineRegion) = runCatching { JSONObject(String(r.metadata)).optString("name") }.getOrDefault("").ifBlank { "Περιοχή ${r.id}" }

    fun download(context: Context, name: String, b: DoubleArray, maxZoom: Int) {
        if (busy) return
        busy = true
        error = null
        progress = "Ξεκινάει…"
        val manager = OfflineManager.getInstance(context.applicationContext)
        manager.setOfflineMapboxTileCountLimit(200_000)
        val bounds = LatLngBounds.from(b[2], b[3], b[0], b[1])
        val ratio = context.resources.displayMetrics.density
        val styles = listOf(STYLE_DARK to "σκούρο", STYLE_LIGHT to "ανοιχτό")

        fun step(i: Int) {
            if (i >= styles.size) {
                busy = false
                progress = null
                refresh(context)
                return
            }
            val (url, label) = styles[i]
            val def = OfflineTilePyramidRegionDefinition(url, bounds, 0.0, maxZoom.toDouble(), ratio)
            manager.createOfflineRegion(def, meta(name, label), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = run(context, offlineRegion, "$name ($label)") { step(i + 1) }
                override fun onError(error: String) {
                    this@OfflineMaps.error = error
                    busy = false
                    progress = null
                }
            })
        }
        step(0)
    }

    private fun run(context: Context, region: OfflineRegion, label: String, onDone: () -> Unit) {
        var finished = false
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (finished) return
                progress = "$label: ${status.completedResourceCount} / ${status.requiredResourceCount}" +
                    (if (status.isRequiredResourceCountPrecise) "" else "+") + " · ${status.completedResourceSize / 1_000_000} MB"
                if (status.isComplete) {
                    finished = true
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    region.setObserver(null)
                    onDone()
                }
            }

            override fun onError(error: OfflineRegionError) {
                // Διακοπή σύνδεσης: το MapLibre ξαναδοκιμάζει μόνο του. Απλώς το δείχνουμε.
                this@OfflineMaps.error = "${error.reason}: ${error.message}"
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                this@OfflineMaps.error = "Πέρασε το όριο των $limit πλακιδίων."
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    /** Διαβάζει τι υπάρχει στο κινητό. Αν κάτι έμεινε στη μέση (έκλεισε η εφαρμογή), το συνεχίζει. */
    fun refresh(context: Context, resume: Boolean = false) {
        OfflineManager.getInstance(context.applicationContext).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions?.toList().orEmpty()
                if (regions.isEmpty()) { saved = emptyList(); return }
                val stats = HashMap<Long, OfflineRegionStatus?>()
                var left = regions.size
                regions.forEach { r ->
                    r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) { stats[r.id] = status; done() }
                        override fun onError(error: String?) { stats[r.id] = null; done() }
                        fun done() {
                            if (--left > 0) return
                            saved = regions.groupBy { nameOf(it) }.map { (name, rs) ->
                                Saved(
                                    name,
                                    rs.map { it.id },
                                    rs.maxOf { stats[it.id]?.completedTileCount ?: 0L },
                                    rs.sumOf { stats[it.id]?.completedResourceSize ?: 0L },
                                    rs.all { stats[it.id]?.isComplete == true },
                                )
                            }
                            if (resume && !busy) {
                                val pending = regions.filter { stats[it.id]?.isComplete != true }
                                if (pending.isNotEmpty()) {
                                    busy = true
                                    fun next(i: Int) {
                                        if (i >= pending.size) { busy = false; progress = null; refresh(context); return }
                                        run(context, pending[i], nameOf(pending[i])) { next(i + 1) }
                                    }
                                    next(0)
                                }
                            }
                        }
                    })
                }
            }

            override fun onError(error: String) { this@OfflineMaps.error = error }
        })
    }

    // ---- Ενημέρωση: οι δρόμοι αλλάζουν. Ξανακατεβάζει ό,τι έχει αλλάξει στον server (το MapLibre ελέγχει κάθε πλακίδιο).

    private const val UPDATE_EVERY_MS = 30L * 24 * 3600 * 1000

    fun lastUpdate(context: Context): Long =
        context.getSharedPreferences("offline", Context.MODE_PRIVATE).getLong("updated", 0L)

    /** Όλοι οι χάρτες στο κινητό: «λήγουν» και ξανακατεβαίνουν όσα πλακίδια άλλαξαν. */
    fun updateAll(context: Context) {
        if (busy) return
        val app = context.applicationContext
        OfflineManager.getInstance(app).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions?.toList().orEmpty()
                if (regions.isEmpty()) return
                busy = true
                error = null
                fun next(i: Int) {
                    if (i >= regions.size) {
                        busy = false
                        progress = null
                        app.getSharedPreferences("offline", Context.MODE_PRIVATE).edit().putLong("updated", System.currentTimeMillis()).apply()
                        refresh(app)
                        return
                    }
                    val r = regions[i]
                    progress = "Ενημέρωση ${nameOf(r)}…"
                    r.invalidate(object : OfflineRegion.OfflineRegionInvalidateCallback {
                        override fun onInvalidate() = run(app, r, "Ενημέρωση ${nameOf(r)}") { next(i + 1) }
                        override fun onError(error: String) { this@OfflineMaps.error = error; next(i + 1) }
                    })
                }
                next(0)
            }

            override fun onError(error: String) { this@OfflineMaps.error = error }
        })
    }

    /** Μόνο του, μία φορά τον μήνα, με Wi‑Fi (χωρίς χρέωση δεδομένων). */
    fun autoUpdate(context: Context) {
        if (busy) return
        if (System.currentTimeMillis() - lastUpdate(context) < UPDATE_EVERY_MS) return
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return
        if (!caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return
        OfflineManager.getInstance(context.applicationContext).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (offlineRegions.isNullOrEmpty()) {
                    // τίποτα κατεβασμένο: «ενημερωμένο», για να μη ρωτάμε συνέχεια
                    context.applicationContext.getSharedPreferences("offline", Context.MODE_PRIVATE).edit().putLong("updated", System.currentTimeMillis()).apply()
                    return
                }
                updateAll(context)
            }

            override fun onError(error: String) = Unit
        })
    }

    fun delete(context: Context, item: Saved) {
        if (busy) return
        OfflineManager.getInstance(context.applicationContext).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val mine = offlineRegions?.filter { it.id in item.ids }.orEmpty()
                var left = mine.size
                if (left == 0) { refresh(context); return }
                mine.forEach { r ->
                    r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() { if (--left == 0) refresh(context) }
                        override fun onError(error: String) { this@OfflineMaps.error = error; if (--left == 0) refresh(context) }
                    })
                }
            }

            override fun onError(error: String) { this@OfflineMaps.error = error }
        })
    }
}
