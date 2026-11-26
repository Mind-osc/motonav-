package app.motonav.ui

import app.motonav.routing.Route
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs

/**
 * Χρώμα της γραμμής ανάλογα με την κλίση του δρόμου (όπως το overlay κλίσης του DMD2):
 * κίτρινο 4–7 %, πορτοκαλί 7–10 %, κόκκινο πάνω από 10 % (ανηφόρα ή κατηφόρα). Επίπεδα κομμάτια: το κανονικό χρώμα.
 */
object SlopeLayer {
    private const val SRC = "moto-slope-grad"
    private const val LAYER = "moto-slope-grad-line"
    const val STEP_M = 100.0

    /** Κλίση % ανά κομμάτι STEP_M (από το υψόμετρο της διαδρομής, εξομαλυμένο σε ±150 m). */
    fun grades(route: Route): List<Pair<Double, Double>> {
        val len = route.lengthM
        if (len < 300 || route.elevations.none { it != null }) return emptyList()
        val out = ArrayList<Pair<Double, Double>>()
        var m = 0.0
        while (m < len) {
            val a = (m - 150).coerceAtLeast(0.0)
            val b = (m + STEP_M + 150).coerceAtMost(len)
            val ea = route.elevationAt(a)
            val eb = route.elevationAt(b)
            if (ea != null && eb != null && b - a > 50) out += m to (eb - ea) / (b - a) * 100.0
            m += STEP_M
        }
        return out
    }

    /** Χρώμα για κλίση |g| %: διάφανο ως 3 %, κίτρινο 5, πορτοκαλί 8, κόκκινο 11+, με ομαλή μετάβαση. */
    private fun colorFor(g: Double): Expression {
        fun mix(a: Int, b: Int, t: Double) = (a + (b - a) * t.coerceIn(0.0, 1.0)).toInt()
        val (r, gg, b, al) = when {
            g < 3.0 -> listOf(255, 213, 79, 0)
            g < 5.0 -> { val t = (g - 3) / 2; listOf(255, 213, 79, mix(0, 230, t)) }
            g < 8.0 -> { val t = (g - 5) / 3; listOf(255, mix(213, 152, t), mix(79, 0, t), 230) }
            g < 11.0 -> { val t = (g - 8) / 3; listOf(mix(255, 229, t), mix(152, 57, t), mix(0, 53, t), 235) }
            else -> listOf(229, 57, 53, 240)
        }
        return Expression.rgba(r, gg, b, al / 255f)
    }

    fun set(style: Style, route: Route?, on: Boolean) {
        // Μία γραμμή με διαβάθμιση (line-gradient): τα χρώματα «δένουν» μεταξύ τους και σβήνουν στα επίπεδα κομμάτια,
        // όπως η κίνηση. Χρειάζεται lineMetrics, γι' αυτό νέα πηγή.
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC, org.maplibre.android.style.sources.GeoJsonOptions().withLineMetrics(true)))
        val gr = if (on && route != null) grades(route) else emptyList()
        val len = route?.lengthM ?: 0.0
        if (gr.size < 2 || len <= 0 || gr.none { abs(it.second) >= 3.0 }) {
            style.getLayer(LAYER)?.setProperties(PropertyFactory.visibility(Property.NONE))
            (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        // Έως ~600 στάσεις (η διαβάθμιση γίνεται υφή· περισσότερες δεν φαίνονται)
        val step = maxOf(1, gr.size / 600)
        val stops = ArrayList<Expression.Stop>()
        var lastP = -1.0
        for (i in gr.indices step step) {
            val (m, g) = gr[i]
            val p = ((m + STEP_M / 2) / len).coerceIn(0.0, 1.0)
            if (p <= lastP) continue
            stops += Expression.stop(p, colorFor(abs(g)))
            lastP = p
        }
        if (stops.size < 2) return
        val gradient = Expression.interpolate(Expression.linear(), Expression.lineProgress(), *stops.toTypedArray())
        val layer = style.getLayer(LAYER) as? LineLayer ?: LineLayer(LAYER, SRC).withProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(10, 3f), Expression.stop(16, 8f))),
        ).also { l ->
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(l, below) else style.addLayer(l)
        }
        layer.setProperties(PropertyFactory.lineGradient(gradient), PropertyFactory.visibility(Property.VISIBLE))
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            Feature.fromGeometry(LineString.fromLngLats(route!!.points.map { Point.fromLngLat(it.lon, it.lat) })),
        )
    }
}
