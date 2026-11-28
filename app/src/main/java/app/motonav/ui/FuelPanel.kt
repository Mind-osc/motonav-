package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.motonav.fuel.FuelOnRouteStop
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import java.util.Locale
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.math.roundToInt
import app.motonav.i18n.tr
import app.motonav.i18n.trf

private val el = Locale("el", "GR")

internal fun fuelKm(m: Double): String = when {
    m < 1000 -> "${(m / 50.0).roundToInt() * 50} m"
    m < 100_000 -> String.format(el, "%.1f km", m / 1000.0)
    else -> "${(m / 1000.0).roundToInt()} km"
}

/** «⛽ BP σε 12 km» στην πλοήγηση. Πάτημα: λίστα με τα επόμενα. */
@Composable
fun FuelPill(next: FuelOnRouteStop?, progressM: Double, loading: Boolean, failed: Boolean = false, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val text = when {
        next != null -> trf("⛽ {0} σε {1}", next.station.name, fuelKm(next.alongM - progressM))
        loading -> tr("⛽ ψάχνω βενζινάδικα…")
        failed -> tr("⛽ δεν φόρτωσαν τα βενζινάδικα · πάτα για ξανά")
        else -> tr("⛽ κανένα βενζινάδικο μέχρι τον προορισμό")
    }
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = c.base.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, if (next == null && !loading) c.base.warning else c.outline),
        onClick = onClick,
    ) {
        Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MotoType.label,
                color = c.base.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            )
        }
    }
}

/** Τα επόμενα βενζινάδικα πάνω στη διαδρομή, με «Στάση» για το καθένα. */
@Composable
fun FuelList(ahead: List<FuelOnRouteStop>, progressM: Double, onAddStop: (FuelOnRouteStop) -> Unit, onClose: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.surface,
        border = BorderStroke(1.dp, c.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Βενζίνη στη διαδρομή"), style = MotoType.title, color = c.base.text, modifier = Modifier.weight(1f))
                TextChoice("✕", selected = false, onClick = onClose)
            }
            if (ahead.isEmpty()) {
                Text(tr("Δεν βρέθηκε βενζινάδικο ως τον προορισμό (έως 700 m από τη γραμμή)."), style = MotoType.body, color = c.base.textMuted)
            }
            ahead.take(6).forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(f.station.name, style = MotoType.body, color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val off = if (f.offM < 60) tr("πάνω στον δρόμο") else trf("+{0} παράκαμψη", fuelKm(f.detourM))
                        Text(
                            listOfNotNull(trf("σε {0}", fuelKm(f.alongM - progressM)), off, f.station.hours?.let { trf("ωράριο {0}", it) }).joinToString(" · "),
                            style = MotoType.label,
                            color = c.base.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextChoice(tr("Στάση"), selected = false, onClick = { onAddStop(f) })
                }
            }
        }
    }
}

/** Τα βενζινάδικα της διαδρομής στον χάρτη: ⛽ σε σκούρο κύκλο· το επόμενο μεγαλύτερο, με όνομα και πράσινο δαχτυλίδι. */
object FuelLayer {
    private const val SRC = "moto-fuel"
    const val LAYER = "moto-fuel-layer"
    private const val NORMAL = "fuel-normal"
    private const val NEXT = "fuel-next"

    private fun icon(density: Float, ring: Int, sizeDp: Int): android.graphics.Bitmap {
        val size = (sizeDp * density).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val r = size / 2f
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF15171C.toInt()
        cv.drawCircle(r, r, r - density, p)
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = 3f * density
        p.color = ring
        cv.drawCircle(r, r, r - 2f * density, p)
        val t = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f; textAlign = android.graphics.Paint.Align.CENTER }
        val fm = t.fontMetrics
        cv.drawText("⛽", r, r - (fm.ascent + fm.descent) / 2, t)
        return bmp
    }

    fun set(style: org.maplibre.android.maps.Style, stops: List<FuelOnRouteStop>, nextId: String?, density: Float, textColor: Int, haloColor: Int) {
        if (style.getSource(SRC) == null) style.addSource(org.maplibre.android.style.sources.GeoJsonSource(SRC))
        if (style.getImage(NORMAL) == null) {
            style.addImage(NORMAL, icon(density, 0xFF9AA0A6.toInt(), 28))
            style.addImage(NEXT, icon(density, 0xFF5FB12E.toInt(), 38))
        }
        if (style.getLayer(LAYER) == null) {
            val layer = org.maplibre.android.style.layers.SymbolLayer(LAYER, SRC).withProperties(
                PropertyFactory.iconImage(Expression.get("img")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.symbolSortKey(Expression.get("sort")),
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textOffset(arrayOf(0f, 1.7f)),
                PropertyFactory.textAnchor(org.maplibre.android.style.layers.Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOptional(true),
                PropertyFactory.textColor(textColor),
                PropertyFactory.textHaloColor(haloColor),
                PropertyFactory.textHaloWidth(1.5f),
            )
            layer.minZoom = 9f
            val below = org.maplibre.android.location.LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        (style.getSource(SRC) as? org.maplibre.android.style.sources.GeoJsonSource)?.setGeoJson(
            org.maplibre.geojson.FeatureCollection.fromFeatures(stops.map {
                val next = it.station.id == nextId
                org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(it.station.lon, it.station.lat)).apply {
                    addStringProperty("id", it.station.id)
                    addStringProperty("img", if (next) NEXT else NORMAL)
                    addStringProperty("label", if (next) it.station.name else "")
                    addNumberProperty("sort", if (next) 1 else 0)
                }
            }),
        )
    }
}
