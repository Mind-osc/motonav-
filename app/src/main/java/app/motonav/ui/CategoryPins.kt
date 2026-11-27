package app.motonav.ui

import app.motonav.search.Place
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Αποτελέσματα κατηγορίας της αναζήτησης (π.χ. Καφές) στον χάρτη: κύκλος στο χρώμα της εφαρμογής. Πάτημα = προορισμός. */
object CategoryPins {
    private const val SRC = "cat-pins"
    const val LAYER = "cat-pins-circle"
    private const val L_DOT = "cat-pins-dot"
    private const val L_NAME = "cat-pins-name"

    fun set(style: Style, places: List<Place>, accent: Int, onAccent: Int, outline: Int, textColor: Int = 0xFFFFFFFF.toInt()) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        style.getLayer("cat-pins-text")?.let { style.removeLayer(it) }
        val below = LocationComponentConstants.SHADOW_LAYER
        if (style.getLayer(LAYER) == null) {
            val l = CircleLayer(LAYER, SRC).withProperties(
                PropertyFactory.circleRadius(11f),
                PropertyFactory.circleColor(accent),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(outline),
            )
            if (style.getLayer(below) != null) style.addLayerBelow(l, below) else style.addLayer(l)
            val d = CircleLayer(L_DOT, SRC).withProperties(PropertyFactory.circleRadius(3.5f), PropertyFactory.circleColor(onAccent))
            if (style.getLayer(below) != null) style.addLayerBelow(d, below) else style.addLayer(d)
        } else {
            style.getLayer(LAYER)?.setProperties(PropertyFactory.circleColor(accent), PropertyFactory.circleStrokeColor(outline))
            style.getLayer(L_DOT)?.setProperties(PropertyFactory.circleColor(onAccent))
        }
        // Όνομα κάτω από κάθε σημείο (κομμένο), για να ξέρεις τι είναι πριν το πατήσεις.
        if (style.getLayer(L_NAME) == null) {
            val t = SymbolLayer(L_NAME, SRC).withProperties(
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOptional(true),
                PropertyFactory.textMaxWidth(8f),
                PropertyFactory.textColor(textColor),
                PropertyFactory.textHaloColor(outline),
                PropertyFactory.textHaloWidth(1.6f),
            )
            if (style.getLayer(below) != null) style.addLayerBelow(t, below) else style.addLayer(t)
        }
        style.getLayer(L_NAME)?.setProperties(PropertyFactory.textColor(textColor), PropertyFactory.textHaloColor(outline))
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(places.mapIndexed { i, p ->
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                    addNumberProperty("i", i)
                    addStringProperty("name", p.name.take(22))
                }
            }),
        )
    }
}
