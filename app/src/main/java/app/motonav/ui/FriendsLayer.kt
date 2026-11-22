package app.motonav.ui

import android.content.Context
import app.motonav.group.GroupLink
import app.motonav.group.Member
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Η παρέα στον χάρτη: βελάκι στο χρώμα του καθενός, όνομα από κάτω, και η διαδρομή του αν πλοηγείται. */
object FriendsLayer {
    private const val SRC = "moto-friends"
    private const val SRC_ROUTES = "moto-friends-routes"
    const val L_FRIENDS = "moto-friends-layer"
    private const val L_ROUTES = "moto-friends-routes-layer"
    private const val L_DEST = "moto-friends-dest"

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    /** «πριν 4′» όταν η θέση είναι παλιά, αλλιώς «12′» ως τον προορισμό, αλλιώς τίποτα. */
    fun subtitle(m: Member, nowMs: Long): String? {
        val age = (nowMs - m.t) / 1000
        return when {
            age >= 3600 -> "πριν ${age / 3600} ώ"
            age >= 60 -> "πριν ${age / 60}′"
            m.etaMs != null && m.etaMs > nowMs -> "άφιξη " + SimpleDateFormat("HH:mm", Locale.US).format(Date(m.etaMs))
            else -> null
        }
    }

    fun set(context: Context, style: Style, members: List<Member>, myUid: String?) {
        val others = members.filter { it.uid != myUid }
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getSource(SRC_ROUTES) == null) style.addSource(GeoJsonSource(SRC_ROUTES))
        if (style.getLayer(L_ROUTES) == null) {
            val l = LineLayer(L_ROUTES, SRC_ROUTES).withProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineDasharray(arrayOf(1.2f, 1.2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            )
            if (style.getLayer("moto-route-casing") != null) style.addLayerBelow(l, "moto-route-casing") else style.addLayer(l)
        }
        if (style.getLayer(L_DEST) == null) {
            style.addLayer(
                CircleLayer(L_DEST, SRC_ROUTES).withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2f),
                ).apply { setFilter(Expression.eq(Expression.geometryType(), Expression.literal("Point"))) },
            )
        }
        if (style.getLayer(L_FRIENDS) == null) {
            style.addLayer(
                SymbolLayer(L_FRIENDS, SRC).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconRotate(Expression.get("brg")),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconOpacity(Expression.get("alpha")),
                    PropertyFactory.textField(Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(13f),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textOffset(arrayOf(0f, 2.0f)),
                    PropertyFactory.textColor("#FFFFFF"),
                    PropertyFactory.textHaloColor("#000000"),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.textAllowOverlap(true),
                ),
            )
        }
        // ένα βελάκι ανά χρώμα
        others.map { it.color }.distinct().forEach { col ->
            val name = "moto-friend-${hex(col)}"
            if (style.getImage(name) == null) {
                style.addImage(name, PuckArt.bitmap(context, MarkerPrefs(shape = PuckShape.CLASSIC, sizeDp = 50, fill = col, outline = true), col))
            }
        }
        val now = GroupLink.now()
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(others.map { m ->
            Feature.fromGeometry(Point.fromLngLat(m.position.lon, m.position.lat)).apply {
                addStringProperty("uid", m.uid)
                addStringProperty("icon", "moto-friend-${hex(m.color)}")
                addNumberProperty("brg", m.bearing ?: 0f)
                // παλιά θέση (πάνω από 2 λεπτά): ξεθωριασμένη
                addNumberProperty("alpha", if (now - m.t > 120_000) 0.45f else 1f)
                addStringProperty("label", listOfNotNull(m.name, subtitle(m, now)).joinToString("\n"))
            }
        }))
        (style.getSource(SRC_ROUTES) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(others.flatMap { m ->
            val c = hex(m.color)
            buildList {
                if (m.route.size >= 2) add(Feature.fromGeometry(LineString.fromLngLats(m.route.map { Point.fromLngLat(it.lon, it.lat) })).apply { addStringProperty("color", c) })
                m.dest?.let { add(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply { addStringProperty("color", c) }) }
            }
        }))
    }
}
