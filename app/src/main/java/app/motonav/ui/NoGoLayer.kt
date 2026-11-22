package app.motonav.ui

import app.motonav.routing.NoGo
import app.motonav.routing.NoGoAreas
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Περιοχές αποφυγής: κόκκινοι διάφανοι κύκλοι στον χάρτη. */
object NoGoLayer {
    private const val SRC = "moto-nogo"
    const val LAYER = "moto-nogo-fill"
    private const val LINE = "moto-nogo-line"

    fun set(style: Style, list: List<NoGo>) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getLayer(LAYER) == null) {
            val fill = FillLayer(LAYER, SRC).withProperties(
                PropertyFactory.fillColor("#FF3B30"),
                PropertyFactory.fillOpacity(0.18f),
            )
            val line = LineLayer(LINE, SRC).withProperties(
                PropertyFactory.lineColor("#FF3B30"),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineOpacity(0.8f),
            )
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) {
                style.addLayerBelow(fill, below)
                style.addLayerBelow(line, below)
            } else {
                style.addLayer(fill)
                style.addLayer(line)
            }
        }
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(list.mapIndexed { i, n ->
                val ring = NoGoAreas.ring(n).map { Point.fromLngLat(it.lon, it.lat) }
                Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addNumberProperty("i", i) }
            }),
        )
    }
}
