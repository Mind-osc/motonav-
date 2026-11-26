package app.motonav.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import app.motonav.data.ImportedPois
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

/** «Δικά σου σημεία» (εισαγμένα αρχεία): πινέζα με το χρώμα της κατηγορίας, όνομα από ζουμ 14. */
object PoiLayer {
    private const val SRC = "imported-pois"
    const val LAYER = "imported-pois-layer"

    private fun icon(color: Int, density: Float): Bitmap {
        val size = (26 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val r = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF15171C.toInt()
        cv.drawCircle(r, r, r - density, p)
        p.color = color
        cv.drawCircle(r, r, r - 3.5f * density, p)
        p.color = 0xFFFFFFFF.toInt()
        cv.drawCircle(r, r, r * 0.28f, p)
        return bmp
    }

    /**
     * Μόνο από ζουμ 14 (επίπεδο γειτονιάς): με χιλιάδες σημεία (π.χ. 3.755 φαρμακεία) σε μικρότερο ζουμ ο χάρτης γέμιζε κουκκίδες.
     * Στην πλοήγηση κρύβονται: εκεί φαίνεται μόνο το επόμενο στη διαδρομή (⛽ 12 km …) και οι κάμερες ειδοποιούν.
     */
    fun set(style: Style, density: Float, textColor: Int, haloColor: Int, navigating: Boolean = false) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        ImportedPois.collections.forEach { c ->
            if (style.getImage("poi-" + c.id) == null) style.addImage("poi-" + c.id, icon(c.color, density))
        }
        if (style.getLayer(LAYER) == null) {
            val layer = SymbolLayer(LAYER, SRC).withProperties(
                PropertyFactory.iconImage(Expression.concat(Expression.literal("poi-"), Expression.get("c"))),
                PropertyFactory.iconSize(
                    Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(14, 0.55f), Expression.stop(17, 1f)),
                ),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.textField(Expression.step(Expression.zoom(), Expression.literal(""), Expression.stop(14, Expression.get("name")))),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOptional(true),
                PropertyFactory.textColor(textColor),
                PropertyFactory.textHaloColor(haloColor),
                PropertyFactory.textHaloWidth(1.5f),
            )
            layer.minZoom = 14f
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        style.getLayer(LAYER)?.setProperties(PropertyFactory.visibility(if (navigating) Property.NONE else Property.VISIBLE))
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(ImportedPois.visible().map { (c, p, i) ->
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                    addStringProperty("c", c.id)
                    addNumberProperty("i", i)
                    addStringProperty("name", p.name)
                }
            }),
        )
    }
}
