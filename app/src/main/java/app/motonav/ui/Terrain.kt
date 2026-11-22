package app.motonav.ui

import android.graphics.Color as AColor
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

/**
 * Φύση στον χάρτη: θάλασσα/λίμνες σκούρο μπλε, δάση πράσινα, γρασίδι/θάμνοι λαδί, βράχια/άμμος καφέ,
 * σκίαση ανάγλυφου στα βουνά και κορυφές με το υψόμετρό τους.
 *
 * Ανάγλυφο: Terrain Tiles (Mapzen/AWS Open Data, δωρεάν, χωρίς κλειδί), κωδικοποίηση terrarium.
 * Κορυφές: layer «mountain_peak» των διανυσματικών πλακιδίων OpenMapTiles του OpenFreeMap.
 */
object Terrain {
    private const val DEM = "moto-dem"
    private const val HILLSHADE = "moto-hillshade"
    private const val LANDCOVER = "moto-landcover"
    private const val PEAKS = "moto-peaks"
    private const val DEM_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"

    private fun c(hex: String) = AColor.parseColor(hex)

    fun apply(style: Style, dark: Boolean, relief: Boolean, maptilerKey: String = "", contours: Boolean = false, dirt: Boolean = true) {
        runCatching { smoothRoads(style) }
        runCatching { dirtRoads(style, dark, dirt) }
        runCatching { contours(style, dark, if (contours) maptilerKey.trim() else "") }
        runCatching { colors(style, dark) }
        runCatching { landcover(style, dark) }
        runCatching { hillshade(style, dark, relief) }
        runCatching { peaks(style, dark, relief) }
    }

    private fun colors(style: Style, dark: Boolean) {
        if (!dark) return
        val water = c("#0E2235")
        style.getLayer("water")?.setProperties(PropertyFactory.fillColor(water))
        style.getLayer("waterway")?.setProperties(PropertyFactory.lineColor(c("#163049")))
        style.getLayer("water_name")?.setProperties(
            PropertyFactory.textColor(c("#7FA6CC")),
            PropertyFactory.textHaloColor(c("#07080B")),
        )
        style.getLayer("landcover_wood")?.setProperties(PropertyFactory.fillColor(c("#11231A")), PropertyFactory.fillOpacity(0.9f))
        style.getLayer("landuse_park")?.setProperties(PropertyFactory.fillColor(c("#10201A")))
    }

    /** Τα είδη εδάφους που το σκούρο στυλ δεν ζωγραφίζει καθόλου. */
    private fun landcover(style: Style, dark: Boolean) {
        if (!dark || style.getLayer(LANDCOVER) != null) return
        val layer = FillLayer(LANDCOVER, "openmaptiles").apply {
            sourceLayer = "landcover"
            setFilter(Expression.match(Expression.get("class"), Expression.literal(false),
                Expression.stop("grass", true), Expression.stop("farmland", true),
                Expression.stop("rock", true), Expression.stop("sand", true)))
            setProperties(
                PropertyFactory.fillColor(
                    Expression.match(Expression.get("class"), Expression.color(c("#1A1C14")),
                        Expression.stop("grass", Expression.color(c("#161F15"))),
                        Expression.stop("farmland", Expression.color(c("#1A1C14"))),
                        Expression.stop("rock", Expression.color(c("#231D17"))),
                        Expression.stop("sand", Expression.color(c("#272118"))))
                ),
                PropertyFactory.fillOpacity(0.85f),
                PropertyFactory.fillAntialias(false),
            )
        }
        if (style.getLayer("water") != null) style.addLayerBelow(layer, "water") else style.addLayer(layer)
    }

    private fun hillshade(style: Style, dark: Boolean, relief: Boolean) {
        if (!relief) {
            style.getLayer(HILLSHADE)?.let { style.removeLayer(it) }
            return
        }
        if (style.getSource(DEM) == null) {
            val ts = TileSet("2.2.0", DEM_URL).apply {
                encoding = "terrarium"
                maxZoom = 14f
                attribution = "Terrain Tiles: Mapzen, AWS Open Data"
            }
            style.addSource(RasterDemSource(DEM, ts, 256))
        }
        if (style.getLayer(HILLSHADE) == null) {
            val layer = HillshadeLayer(HILLSHADE, DEM).withProperties(
                PropertyFactory.hillshadeShadowColor(if (dark) c("#000000") else c("#5A4A3A")),
                PropertyFactory.hillshadeHighlightColor(if (dark) c("#3A3225") else c("#FFFFFF")),
                PropertyFactory.hillshadeAccentColor(if (dark) c("#1E1912") else c("#6B5B4B")),
                PropertyFactory.hillshadeExaggeration(if (dark) 0.55f else 0.35f),
            )
            // Πάνω από το έδαφος (δάση, γρασίδι), κάτω από ποτάμια, κτίρια, δρόμους και ονόματα.
            val above = listOf("waterway", "building").firstOrNull { style.getLayer(it) != null }
            if (above != null) style.addLayerBelow(layer, above) else style.addLayer(layer)
        }
    }

    /**
     * Οι δρόμοι του στυλ τελειώνουν «κομμένοι» (line-cap butt): στις ράμπες και εκεί που ένας δρόμος
     * γίνεται άλλος φαίνονται σκαλοπάτια και τρύπες. Στρογγυλές άκρες και ενώσεις τα κλείνουν.
     */
    private fun smoothRoads(style: Style) {
        style.layers.forEach { l ->
            if (l is LineLayer && l.sourceLayer == "transportation") {
                l.setProperties(
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
            }
        }
    }

    private const val DIRT = "moto-dirt-roads"

    /** Χωματόδρομοι (class=track ή surface=unpaved): καφέ-πορτοκαλί διακεκομμένη, από ζουμ 10. */
    private fun dirtRoads(style: Style, dark: Boolean, show: Boolean) {
        if (!show) {
            style.getLayer(DIRT)?.let { style.removeLayer(it) }
            return
        }
        if (style.getLayer(DIRT) != null) return
        val layer = LineLayer(DIRT, "openmaptiles").apply {
            sourceLayer = "transportation"
            minZoom = 10f
            setFilter(
                Expression.all(
                    Expression.neq(Expression.get("class"), Expression.literal("path")),
                    Expression.neq(Expression.get("brunnel"), Expression.literal("tunnel")),
                    Expression.any(
                        Expression.eq(Expression.get("class"), Expression.literal("track")),
                        Expression.eq(Expression.get("surface"), Expression.literal("unpaved")),
                    ),
                )
            )
            setProperties(
                PropertyFactory.lineColor(if (dark) c("#C08A4E") else c("#9A6A3A")),
                PropertyFactory.lineWidth(
                    Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 0.8f), Expression.stop(14, 2.0f), Expression.stop(17, 4.0f))
                ),
                PropertyFactory.lineDasharray(arrayOf(2.5f, 1.5f)),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineOpacity(0.95f),
            )
        }
        val anchor = listOf("highway_minor", "road_service_track", "road_minor").firstOrNull { style.getLayer(it) != null }
        if (anchor != null) style.addLayerAbove(layer, anchor) else style.addLayer(layer)
    }

    private const val CONTOUR_SRC = "moto-contours"
    private const val CONTOUR_LINE = "moto-contour-line"
    private const val CONTOUR_LABEL = "moto-contour-label"
    private var contourKey = ""

    /** Ισοϋψείς από MapTiler (contours-v2, layer «contour»: height σε m, nth_line 1/2/5/10 = κάθε πόσο κύρια). */
    private fun contours(style: Style, dark: Boolean, key: String) {
        val haveSource = style.getSource(CONTOUR_SRC) != null
        if (key.isBlank() || (haveSource && key != contourKey)) {
            style.getLayer(CONTOUR_LABEL)?.let { style.removeLayer(it) }
            style.getLayer(CONTOUR_LINE)?.let { style.removeLayer(it) }
            style.getSource(CONTOUR_SRC)?.let { style.removeSource(it) }
            if (key.isBlank()) return
        }
        contourKey = key
        if (style.getSource(CONTOUR_SRC) == null) {
            style.addSource(VectorSource(CONTOUR_SRC, "https://api.maptiler.com/tiles/contours-v2/tiles.json?key=$key"))
        }
        val major = Expression.gte(Expression.toNumber(Expression.get("nth_line")), Expression.literal(5))
        val lineColor = if (dark) c("#4A4033") else c("#B89A74")
        if (style.getLayer(CONTOUR_LINE) == null) {
            val line = LineLayer(CONTOUR_LINE, CONTOUR_SRC).apply {
                sourceLayer = "contour"
                minZoom = 11f
                setProperties(
                    PropertyFactory.lineColor(lineColor),
                    PropertyFactory.lineOpacity(if (dark) 0.85f else 0.7f),
                    PropertyFactory.lineWidth(Expression.switchCase(major, Expression.literal(1.1f), Expression.literal(0.5f))),
                )
            }
            val above = listOf("waterway", "building").firstOrNull { style.getLayer(it) != null }
            if (above != null) style.addLayerBelow(line, above) else style.addLayer(line)
        }
        if (style.getLayer(CONTOUR_LABEL) == null) {
            val label = SymbolLayer(CONTOUR_LABEL, CONTOUR_SRC).apply {
                sourceLayer = "contour"
                minZoom = 12f
                setFilter(major)
                setProperties(
                    PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                    PropertyFactory.textField(Expression.concat(Expression.toString(Expression.get("height")), Expression.literal(" m"))),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(11.5f),
                    PropertyFactory.textColor(if (dark) c("#CDB78E") else c("#6E4F2C")),
                    PropertyFactory.textHaloColor(if (dark) c("#07080B") else c("#FFFFFF")),
                    PropertyFactory.textHaloWidth(1.8f),
                )
            }
            // Τα νούμερα πάνω από δρόμους και ονόματα, για να διαβάζονται (η διαδρομή και το βελάκι μένουν από πάνω).
            onTop(style, label)
        }
    }

    private fun peaks(style: Style, dark: Boolean, relief: Boolean) {
        if (!relief) {
            style.getLayer(PEAKS)?.let { style.removeLayer(it) }
            return
        }
        if (style.getLayer(PEAKS) != null) return
        val ele = Expression.get("ele")
        val layer = SymbolLayer(PEAKS, "openmaptiles").apply {
            sourceLayer = "mountain_peak"
            minZoom = 9f
            setFilter(Expression.has("ele"))
            setProperties(
                PropertyFactory.textField(
                    Expression.concat(
                        Expression.literal("▲ "),
                        Expression.coalesce(Expression.get("name:el"), Expression.get("name"), Expression.literal("")),
                        Expression.literal("\n"),
                        Expression.toString(ele),
                        Expression.literal(" m"),
                    )
                ),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textColor(if (dark) c("#E6D2A6") else c("#5A3F20")),
                PropertyFactory.textHaloColor(if (dark) c("#07080B") else c("#FFFFFF")),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.symbolSortKey(Expression.product(Expression.literal(-1), ele)),
            )
        }
        onTop(style, layer)
    }

    /** Πάνω από όλο τον χάρτη (δρόμους, ονόματα), αλλά κάτω από το βελάκι· η διαδρομή μπαίνει μετά, άρα κι αυτή από πάνω. */
    private fun onTop(style: Style, layer: org.maplibre.android.style.layers.Layer) {
        // Αν υπάρχουν ήδη δικά μας στρώματα (διαδρομή, κάμερες, μέρη, παρέα), μπαίνουμε κάτω από το πρώτο τους.
        val ours = style.layers.firstOrNull { l ->
            l.id.startsWith("moto-") && listOf(DEM, HILLSHADE, LANDCOVER, PEAKS, DIRT, CONTOUR_LINE, CONTOUR_LABEL).none { l.id == it }
        }?.id
        val puck = org.maplibre.android.location.LocationComponentConstants.SHADOW_LAYER
        when {
            ours != null -> style.addLayerBelow(layer, ours)
            style.getLayer(puck) != null -> style.addLayerBelow(layer, puck)
            else -> style.addLayer(layer)
        }
    }
}
