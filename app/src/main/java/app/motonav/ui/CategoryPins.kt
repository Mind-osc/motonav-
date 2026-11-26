package app.motonav.ui

import app.motonav.search.Place
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Αποτελέσματα κατηγορίας της αναζήτησης (π.χ. Καφές) στον χάρτη: κύκλος με τον αριθμό της λίστας. */
object CategoryPins {
    private const val SRC = "cat-pins"
    private const val L_CIRCLE = "cat-pins-circle"
    private const val L_TEXT = "cat-pins-text"

    fun set(style: Style, places: List<Place>, accent: Int, onAccent: Int, outline: Int) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        val below = LocationComponentConstants.SHADOW_LAYER
        if (style.getLayer(L_CIRCLE) == null) {
            val l = CircleLayer(L_CIRCLE, SRC).withProperties(
                PropertyFactory.circleRadius(13f),
                PropertyFactory.circleColor(accent),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(outline),
            )
            if (style.getLayer(below) != null) style.addLayerBelow(l, below) else style.addLayer(l)
        } else style.getLayer(L_CIRCLE)?.setProperties(PropertyFactory.circleColor(accent), PropertyFactory.circleStrokeColor(outline))
        if (style.getLayer(L_TEXT) == null) {
            val t = SymbolLayer(L_TEXT, SRC).withProperties(
                PropertyFactory.textField(Expression.get("n")),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(onAccent),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
            if (style.getLayer(below) != null) style.addLayerBelow(t, below) else style.addLayer(t)
        } else style.getLayer(L_TEXT)?.setProperties(PropertyFactory.textColor(onAccent))
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(places.mapIndexed { i, p ->
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply { addStringProperty("n", "${i + 1}") }
            }.reversed()), // ο 1 πάνω από τους άλλους
        )
    }
}
