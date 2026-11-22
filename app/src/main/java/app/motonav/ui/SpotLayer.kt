package app.motonav.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import app.motonav.spots.Spot
import app.motonav.spots.SpotKind
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Εικονίδια για τα μέρη μηχανόβιων: σκούρος κύκλος, χρωματιστό δαχτυλίδι, emoji στη μέση. */
object SpotLayer {
    private const val SRC = "moto-spots"
    const val LAYER = "moto-spots-layer"

    private fun icon(kind: SpotKind, density: Float): Bitmap {
        val size = (34 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val r = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF15171C.toInt()
        cv.drawCircle(r, r, r - density, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * density
        p.color = kind.color
        cv.drawCircle(r, r, r - 2f * density, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f; textAlign = Paint.Align.CENTER }
        val fm = t.fontMetrics
        cv.drawText(kind.emoji, r, r - (fm.ascent + fm.descent) / 2, t)
        return bmp
    }

    fun set(style: Style, spots: List<Spot>, density: Float, textColor: Int, haloColor: Int) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getImage("spot-" + SpotKind.VIEW.name) == null) {
            SpotKind.entries.forEach { style.addImage("spot-" + it.name, icon(it, density)) }
        }
        if (style.getLayer(LAYER) == null) {
            val layer = SymbolLayer(LAYER, SRC).withProperties(
                PropertyFactory.iconImage(Expression.concat(Expression.literal("spot-"), Expression.get("kind"))),
                PropertyFactory.iconSize(
                    Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(11, 0.65f), Expression.stop(15, 1f)),
                ),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.textField(Expression.step(Expression.zoom(), Expression.literal(""), Expression.stop(14, Expression.get("name")))),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOptional(true),
                PropertyFactory.textColor(textColor),
                PropertyFactory.textHaloColor(haloColor),
                PropertyFactory.textHaloWidth(1.5f),
            )
            layer.minZoom = 11f
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(spots.map {
                Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                    addStringProperty("id", it.id)
                    addStringProperty("kind", it.kind.name)
                    addStringProperty("name", it.name)
                }
            }),
        )
    }

    fun clear(style: Style) {
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }
}
