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
    private const val SRC = "moto-slope"
    private const val LAYER = "moto-slope-line"
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

    fun set(style: Style, route: Route?, on: Boolean) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getLayer(LAYER) == null) {
            val layer = LineLayer(LAYER, SRC).withProperties(
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(10, 3f), Expression.stop(16, 8f))),
                PropertyFactory.lineColor(
                    Expression.step(
                        Expression.get("g"), Expression.color(0xFFFFD54F.toInt()),
                        Expression.stop(7, Expression.color(0xFFFF9800.toInt())),
                        Expression.stop(10, Expression.color(0xFFE53935.toInt())),
                    ),
                ),
            )
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        val feats = ArrayList<Feature>()
        if (on && route != null) {
            for ((m, g) in grades(route)) {
                val ag = abs(g)
                if (ag < 4.0) continue
                val pts = (0..4).map { k -> route.pointAt((m + STEP_M * k / 4).coerceAtMost(route.lengthM)) }
                feats += Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                    addNumberProperty("g", ag)
                }
            }
        }
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }
}
