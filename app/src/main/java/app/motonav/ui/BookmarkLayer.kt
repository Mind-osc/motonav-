package app.motonav.ui

import app.motonav.search.Place
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Σελιδοδείκτες στον χάρτη: κουκκίδα στο χρώμα της λίστας τους. Πάτημα = διαδρομή εκεί. */
object BookmarkLayer {
    private const val SRC = "moto-bookmarks"
    const val LAYER = "moto-bookmarks-layer"

    fun set(style: Style, list: List<Place>, accent: Int) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getLayer(LAYER) == null) {
            val layer = CircleLayer(LAYER, SRC).withProperties(
                PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.circleRadius(
                    Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 3f), Expression.stop(15, 7f)),
                ),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.5f),
            )
            layer.minZoom = 7f
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(list.map {
                Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                    val col = if (it.color != 0) it.color else accent
                    addStringProperty("color", String.format("#%06X", col and 0xFFFFFF))
                    addStringProperty("name", it.name)
                }
            }),
        )
    }
}
