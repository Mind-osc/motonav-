package app.motonav.ui

import androidx.compose.ui.graphics.toArgb
import app.motonav.geo.LatLon
import app.motonav.routing.Route
import app.motonav.ui.theme.AppColors
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.TileSet
import app.motonav.traffic.TomTomClient
import app.motonav.traffic.RouteJam
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Γραμμή διαδρομής και σημάδι προορισμού στον χάρτη, στα χρώματα του θέματος. */
object RouteLayers {
    private const val SRC_ROUTE = "moto-route"
    private const val SRC_DEST = "moto-dest"
    private const val L_CASING = "moto-route-casing"
    private const val L_LINE = "moto-route-line"
    private const val L_DEST = "moto-dest-point"

    /** Χρώματα/πάχος της γραμμής από τις ρυθμίσεις «Βελάκι και γραμμή». */
    var marker = MarkerPrefs()

    fun install(style: Style, c: AppColors) {
        if (style.getSource(SRC_ROUTE) == null) style.addSource(GeoJsonSource(SRC_ROUTE))
        if (style.getSource(SRC_DEST) == null) style.addSource(GeoJsonSource(SRC_DEST))
        if (style.getLayer(L_CASING) == null) {
            addBelowPuck(
                style,
                LineLayer(L_CASING, SRC_ROUTE).withProperties(
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineWidth(26f),
                ),
            )
        }
        if (style.getLayer(L_LINE) == null) {
            addBelowPuck(
                style,
                LineLayer(L_LINE, SRC_ROUTE).withProperties(
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineWidth(18f),
                ),
            )
        }
        if (style.getLayer(L_DEST) == null) {
            addBelowPuck(
                style,
                CircleLayer(L_DEST, SRC_DEST).withProperties(
                    PropertyFactory.circleRadius(11f),
                    PropertyFactory.circleStrokeWidth(4f),
                ),
            )
        }
        // Ένα στρώμα ανά επίπεδο κίνησης: το line-gradient δεν δέχεται χρώμα από τα δεδομένα.
        var below = L_LINE
        JAM_COLORS.forEachIndexed { i, color ->
            val src = SRC_JAM + (i + 1)
            val id = L_JAM + (i + 1)
            if (style.getSource(src) == null) style.addSource(GeoJsonSource(src, GeoJsonOptions().withLineMetrics(true)))
            if (style.getLayer(id) == null) {
                val layer = LineLayer(id, src).withProperties(
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineWidth(18f),
                    PropertyFactory.lineGradient(fade(color)),
                ).withFilter(
                    // Από μακριά, τα μικρά κομμάτια βγαίνουν «μπαλώματα»: δείχνουμε μόνο όσα φαίνονται σαν γραμμή.
                    Expression.gte(
                        Expression.get("len"),
                        Expression.step(Expression.zoom(), Expression.literal(1500), Expression.stop(11, 900), Expression.stop(12.5, 450), Expression.stop(14, 150), Expression.stop(15.5, 0)),
                    ),
                )
                if (style.getLayer(below) != null) style.addLayerAbove(layer, below) else addBelowPuck(style, layer)
            }
            below = id
        }
        // Βελάκια κατεύθυνσης πάνω στη γραμμή (όπως στο Kurviger): φαίνονται από ζουμ 12 και μετά.
        style.addImage(ARROW, arrow())
        if (style.getLayer(L_ARROWS) == null) {
            val layer = SymbolLayer(L_ARROWS, SRC_ROUTE).withProperties(
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.symbolSpacing(110f),
                PropertyFactory.iconImage(ARROW),
                PropertyFactory.iconSize(0.55f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
            )
            layer.minZoom = 12f
            if (style.getLayer(below) != null) style.addLayerAbove(layer, below) else addBelowPuck(style, layer)
        }
        // Αριθμοί πάνω στις στάσεις (1, 2, 3…).
        if (style.getLayer(L_STOP_NUM) == null) {
            val layer = SymbolLayer(L_STOP_NUM, SRC_DEST).withProperties(
                PropertyFactory.textField(Expression.get("n")),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            ).withFilter(Expression.has("n"))
            if (style.getLayer(L_DEST) != null) style.addLayerAbove(layer, L_DEST) else addBelowPuck(style, layer)
        }
        installAlternatives(style, c)
        recolor(style, c)
    }

    private const val L_ARROWS = "moto-route-arrows"
    private const val L_STOP_NUM = "moto-stop-num"
    private const val ARROW = "moto-route-arrow"

    /** Σεβρόν «›» που δείχνει προς τα πάνω (το MapLibre το στρίβει με τη γραμμή). */
    private fun arrow(): android.graphics.Bitmap {
        val w = 40
        val h = 40
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC1B1B27.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        // Η γραμμή πάει «προς τα δεξιά» στη θέση του συμβόλου: το βελάκι κοιτάει δεξιά.
        val path = android.graphics.Path().apply {
            moveTo(12f, 8f)
            lineTo(28f, 20f)
            lineTo(12f, 32f)
        }
        cv.drawPath(path, p)
        return bmp
    }

    private const val SRC_JAM = "moto-jam-src-"
    private const val L_JAM = "moto-jam-line-"
    const val JAM_SLOW = 0xFFF29900.toInt()     // πορτοκαλί: αργά
    const val JAM_HEAVY = 0xFFE53935.toInt()    // κόκκινο: πολύ αργά
    const val JAM_STOPPED = 0xFF8E1B1B.toInt()  // σκούρο κόκκινο: σταματημένα / κλειστό
    private val JAM_COLORS = listOf(JAM_SLOW, JAM_HEAVY, JAM_STOPPED)

    /** Το χρώμα «σβήνει» στις άκρες και δένει με τη γραμμή της διαδρομής. */
    private fun fade(color: Int): Expression {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        return Expression.interpolate(
            Expression.linear(), Expression.lineProgress(),
            Expression.stop(0.0, Expression.rgba(r, g, b, 0f)),
            Expression.stop(0.3, Expression.rgba(r, g, b, 0.9f)),
            Expression.stop(0.7, Expression.rgba(r, g, b, 0.9f)),
            Expression.stop(1.0, Expression.rgba(r, g, b, 0f)),
        )
    }

    /** Η γραμμή μας βάφεται εκεί που έχει κίνηση, όπως στο Google Maps. Μόνο μπροστά από το fromM. */
    fun setTraffic(style: Style, route: Route?, jams: List<RouteJam>, fromM: Double) {
        val merged = if (route == null) emptyList() else RouteJam.merge(jams)
        for (level in 1..3) {
            val src = style.getSource(SRC_JAM + level) as? GeoJsonSource ?: continue
            val features = merged.filter { it.level == level }.mapNotNull { j ->
                val a = maxOf(j.fromM, fromM)
                if (j.toM - a < 5.0) return@mapNotNull null
                val pts = route!!.slice(a, j.toM)
                if (pts.size < 2) return@mapNotNull null
                Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                    addNumberProperty("len", j.toM - a)
                }
            }
            src.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    fun recolor(style: Style, c: AppColors) {

        (style.getLayer(L_ALT_LABEL) as? SymbolLayer)?.setProperties(PropertyFactory.textColor(c.base.text.toArgb()))
        style.addImage(BUBBLE, bubble(c))
        val line = marker.lineColor ?: c.accent.toArgb()
        val edge = marker.lineOutlineColor ?: c.base.bg.toArgb()
        (style.getLayer(L_CASING) as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(edge),
            PropertyFactory.lineWidth(18f + 2 * marker.lineOutlineDp),
            PropertyFactory.lineOpacity(if (marker.lineOutlineDp <= 0f) 0f else 1f),
        )
        (style.getLayer(L_LINE) as? LineLayer)?.setProperties(PropertyFactory.lineColor(line))
        // Εναλλακτικές: συμπαγές «ξεθωριασμένο» χρώμα (38% γραμμή + 62% φόντο), όχι διαφάνεια.
        // Με διαφάνεια, όπου πέφτουν η μία πάνω στην άλλη (ή στις γωνίες) το χρώμα διπλασιάζεται και «ασπρίζει».
        (style.getLayer(L_ALT) as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(blend(line, c.base.bg.toArgb(), 0.38f)),
            PropertyFactory.lineOpacity(1f),
        )
        (style.getLayer(L_DEST) as? CircleLayer)?.setProperties(
            PropertyFactory.circleColor(c.accent.toArgb()),
            PropertyFactory.circleStrokeColor(c.base.bg.toArgb()),
        )
        (style.getLayer(L_STOP_NUM) as? SymbolLayer)?.setProperties(PropertyFactory.textColor(android.graphics.Color.WHITE))
    }

    fun setRoute(style: Style, route: Route?) {
        val src = style.getSource(SRC_ROUTE) as? GeoJsonSource ?: return
        if (route == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        } else {
            val line = LineString.fromLngLats(route.points.map { Point.fromLngLat(it.lon, it.lat) })
            src.setGeoJson(Feature.fromGeometry(line))
        }
    }

    /** Μόνο ό,τι μένει: από το σημείο σου πάνω στη γραμμή ως τον προορισμό. */
    fun setRemaining(style: Style, route: Route, segIndex: Int, snapped: LatLon?) {
        val src = style.getSource(SRC_ROUTE) as? GeoJsonSource ?: return
        val from = (segIndex + 1).coerceIn(0, route.points.size)
        val pts = buildList {
            if (snapped != null) add(snapped)
            addAll(route.points.subList(from, route.points.size))
        }
        if (pts.size < 2) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            return
        }
        src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })))
    }

    private const val SRC_ALT = "moto-alt"
    private const val SRC_ALT_LABEL = "moto-alt-label"
    const val L_ALT = "moto-alt-line"
    const val L_ALT_LABEL = "moto-alt-bubble"
    private const val BUBBLE = "moto-bubble"

    /** Εναλλακτικές: ξεθωριασμένη γραμμή κάτω από την κύρια, και «συννεφάκι» με τη διαφορά χρόνου. */
    private fun installAlternatives(style: Style, c: AppColors) {
        if (style.getSource(SRC_ALT) == null) style.addSource(GeoJsonSource(SRC_ALT))
        if (style.getSource(SRC_ALT_LABEL) == null) style.addSource(GeoJsonSource(SRC_ALT_LABEL))
        style.addImage(BUBBLE, bubble(c))
        if (style.getLayer(L_ALT) == null) {
            val layer = LineLayer(L_ALT, SRC_ALT).withProperties(
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineWidth(12f),
            )
            if (style.getLayer(L_CASING) != null) style.addLayerBelow(layer, L_CASING) else addBelowPuck(style, layer)
        }
        if (style.getLayer(L_ALT_LABEL) == null) {
            addBelowPuck(
                style,
                SymbolLayer(L_ALT_LABEL, SRC_ALT_LABEL).withProperties(
                    PropertyFactory.textField(Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(15f),
                    PropertyFactory.iconImage(BUBBLE),
                    PropertyFactory.iconTextFit(Property.ICON_TEXT_FIT_BOTH),
                    PropertyFactory.iconTextFitPadding(arrayOf(5f, 10f, 5f, 10f)),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.textIgnorePlacement(true),
                ),
            )
        }
    }

    /** a·t + b·(1−t), αδιαφανές. */
    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x * t + y * (1 - t)).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(
            ch(android.graphics.Color.red(a), android.graphics.Color.red(b)),
            ch(android.graphics.Color.green(a), android.graphics.Color.green(b)),
            ch(android.graphics.Color.blue(a), android.graphics.Color.blue(b)),
        )
    }

    /** Στρογγυλεμένο κουτάκι στα χρώματα του θέματος (τεντώνεται γύρω από το κείμενο). */
    private fun bubble(c: AppColors): android.graphics.Bitmap {
        val w = 96
        val h = 56
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = c.base.surface.toArgb() }
        val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = c.outline.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
        }
        val rect = android.graphics.RectF(2f, 2f, w - 2f, h - 2f)
        cv.drawRoundRect(rect, 18f, 18f, fill)
        cv.drawRoundRect(rect, 18f, 18f, stroke)
        return bmp
    }

    fun setAlternatives(style: Style, alts: List<app.motonav.routing.Alternative>, mainS: Double?) {
        val lines = style.getSource(SRC_ALT) as? GeoJsonSource ?: return
        val labels = style.getSource(SRC_ALT_LABEL) as? GeoJsonSource ?: return
        lines.setGeoJson(FeatureCollection.fromFeatures(alts.mapIndexed { i, a ->
            Feature.fromGeometry(LineString.fromLngLats(a.route.points.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                addNumberProperty("idx", i)
            }
        }))
        labels.setGeoJson(FeatureCollection.fromFeatures(if (mainS == null) emptyList() else alts.mapIndexed { i, a ->
            Feature.fromGeometry(Point.fromLngLat(a.labelAt.lon, a.labelAt.lat)).apply {
                addNumberProperty("idx", i)
                addStringProperty("label", app.motonav.routing.Alternatives.deltaLabel(a.route.durationS, mainS))
            }
        }))
    }

    fun setDestination(style: Style, dest: LatLon?, stops: List<LatLon> = emptyList()) {
        val src = style.getSource(SRC_DEST) as? GeoJsonSource ?: return
        val numbered = stops.size >= 1
        val features = stops.mapIndexed { i, it ->
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply { if (numbered) addStringProperty("n", "${i + 1}") }
        } + listOfNotNull(dest).map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun addBelowPuck(style: Style, layer: Layer) {
        val below = LocationComponentConstants.SHADOW_LAYER
        if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }
}

/**
 * Κίνηση: διανυσματικά πλακίδια ροής TomTom (layer «Traffic flow», traffic_level 0 = σταματημένα, 1 = κανονικά).
 * Ζωγραφίζουμε μόνο ό,τι κινείται αργά, ως λεπτή γραμμή πάνω στην άκρη του δρόμου:
 * κάθε κατεύθυνση είναι ξεχωριστή γραμμή, και με θετικό offset πέφτει στη δική της πλευρά.
 * Κόκκινο < 40% της κανονικής ταχύτητας, πορτοκαλί < 75%.
 */
object TrafficLayer {
    private const val SRC = "moto-traffic"
    private const val LAYER = "moto-traffic-layer"

    fun set(style: Style, key: String?, dark: Boolean) {
        remove(style)
        if (key.isNullOrBlank()) return
        style.addSource(VectorSource(SRC, TileSet("2.2.0", TomTomClient.flowVectorTileUrl(key))))
        val zoom = Expression.zoom()
        val layer = LineLayer(LAYER, SRC)
            .withSourceLayer("Traffic flow")
            .withFilter(Expression.lt(Expression.get("traffic_level"), 0.75))
            .withProperties(
                PropertyFactory.lineColor(
                    Expression.step(
                        Expression.get("traffic_level"),
                        Expression.color(android.graphics.Color.parseColor(if (dark) "#FF4D4D" else "#D32F2F")),
                        Expression.stop(0.4, Expression.color(android.graphics.Color.parseColor(if (dark) "#FFA23A" else "#EF6C00"))),
                    )
                ),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(), zoom,
                        Expression.stop(10, 1.0f), Expression.stop(14, 1.8f), Expression.stop(18, 3.0f),
                    )
                ),
                // Λίγο δεξιά από τον άξονα, μέσα στον δρόμο (≈ 1/3 του πλάτους του): κάθε κατεύθυνση στη δική της
                // λωρίδα. Οι δρόμοι του TomTom και του OpenStreetMap διαφέρουν μερικά μέτρα, οπότε μεγαλύτερο
                // offset έβγαζε τη γραμμή έξω από τον δρόμο.
                PropertyFactory.lineOffset(
                    Expression.interpolate(
                        Expression.exponential(1.3f), zoom,
                        Expression.stop(10, 0.5f), Expression.stop(14, 1.2f), Expression.stop(16, 2.2f), Expression.stop(18, 3.8f), Expression.stop(20, 6.0f),
                    )
                ),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.95f),
            )
        val below = MapContrast.firstLabelLayerId(style) ?: "moto-route-casing"
        if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    fun remove(style: Style) {
        style.getLayer(LAYER)?.let { style.removeLayer(it) }
        style.getSource(SRC)?.let { style.removeSource(it) }
    }
}

/** Παλιά διαδρομή από το ημερολόγιο: γαλάζια διακεκομμένη γραμμή. */
object TrackLayer {
    private const val SRC = "moto-track"
    private const val LAYER = "moto-track-layer"

    fun set(style: Style, pts: List<LatLon>?) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getLayer(LAYER) == null) {
            val layer = LineLayer(LAYER, SRC).withProperties(
                PropertyFactory.lineColor(android.graphics.Color.parseColor("#8AB4F8")),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 1.2f)),
            )
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        val src = style.getSource(SRC) as? GeoJsonSource ?: return
        if (pts == null || pts.size < 2) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        } else {
            src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })))
        }
    }
}

/** Κάμερες στον χάρτη: μικροί κίτρινοι κύκλοι με σκούρο περίγραμμα (από ζουμ 10). */
object CameraLayer {
    private const val SRC = "moto-cameras"
    const val LAYER = "moto-cameras-layer"

    fun set(style: Style, cams: List<app.motonav.cameras.SpeedCamera>) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getLayer(LAYER) == null) {
            val layer = CircleLayer(LAYER, SRC).withProperties(
                PropertyFactory.circleRadius(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 2.5f), Expression.stop(15, 5.5f), Expression.stop(18, 8f),
                    )
                ),
                PropertyFactory.circleColor(android.graphics.Color.parseColor("#FFD23F")),
                PropertyFactory.circleStrokeColor(android.graphics.Color.parseColor("#07080B")),
                PropertyFactory.circleStrokeWidth(2f),
            )
            layer.minZoom = 10f
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(cams.map {
                Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                    addStringProperty("id", it.id)
                    addStringProperty("src", it.source)
                }
            })
        )
    }
}
