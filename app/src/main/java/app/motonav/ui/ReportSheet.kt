package app.motonav.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motonav.hazards.Hazard
import app.motonav.hazards.HazardKind
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/** S07 Αναφορά: μεγάλα πλακίδια για γάντια. Ένα πάτημα σημειώνει εκεί που είσαι και κλείνει. */
@Composable
fun ReportSheet(
    onPick: (HazardKind) -> Unit,
    onClose: () -> Unit,
    onCamera: (() -> Unit)? = null,
    /** Στην πλοήγηση: «κλειστός δρόμος μπροστά» και «νέα διαδρομή» (όπως στο Kurviger). */
    onRoadClosed: (() -> Unit)? = null,
    onReroute: (() -> Unit)? = null,
) {
    val c = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = c.base.surface,
            border = BorderStroke(1.dp, c.outline),
            modifier = Modifier.safeDrawingPadding().padding(Space.md).widthIn(max = 520.dp),
        ) {
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Τι υπάρχει εδώ;", style = MotoType.title, color = c.base.text)
                val tap: (() -> Unit) -> Unit = { action -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); action() }
                if (onRoadClosed != null || onReroute != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        onRoadClosed?.let { ReportTile({ Text("⛔", fontSize = 20.sp) }, "Κλειστός δρόμος μπροστά", c.accent, Modifier.weight(1f)) { tap(it) } }
                        onReroute?.let { ReportTile({ Text("↻", fontSize = 22.sp, color = c.accent) }, "Νέα διαδρομή από εδώ", c.accent, Modifier.weight(1f)) { tap(it) } }
                    }
                }
                if (onCamera != null) {
                    ReportTile(
                        {
                            androidx.compose.material3.Icon(
                                ImageVector.vectorResource(app.motonav.R.drawable.ic_camera), contentDescription = null,
                                tint = Color(0xFFFFD23F), modifier = Modifier.size(22.dp),
                            )
                        },
                        "Κάμερα ταχύτητας εδώ", Color(0xFFFFD23F), Modifier.fillMaxWidth(),
                    ) { tap(onCamera) }
                }
                // Ο «κακός καιρός» δεν μπαίνει πια με το χέρι: τον ξέρει η εφαρμογή από την πρόγνωση στη διαδρομή.
                HazardKind.entries.filter { it != HazardKind.WEATHER }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        row.forEach { k ->
                            ReportTile({ Text(k.emoji, fontSize = 20.sp) }, k.label, Color(k.color), Modifier.weight(1f)) { tap { onPick(k) } }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                TextChoice("Άκυρο", selected = false, onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Ήρεμο πλακίδιο (όπως στις Γρήγορες ενέργειες): ουδέτερο φόντο, χρώμα μόνο στον κύκλο του εικονιδίου. Αρκετά ψηλό για γάντια. */
@Composable
private fun ReportTile(icon: @Composable () -> Unit, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = c.base.surface2,
        border = BorderStroke(1.dp, c.outline.copy(alpha = 0.6f)),
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.18f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { icon() }
            Text(label, style = MotoType.label.copy(fontSize = 15.sp), color = c.base.text, maxLines = 2)
        }
    }
}

/** Πορτοκαλί μπάρα για κίνδυνο μπροστά (≤ 500 m). Πάτημα: «δεν υπάρχει πια» → σβήσιμο. */
@Composable
fun HazardBar(hazard: Hazard, distanceM: Double, confirmDelete: Boolean, onClick: () -> Unit) {
    val kc = Color(hazard.kind.color)
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = Color(0xFF1A1206),
        border = BorderStroke(2.dp, kc),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.heightIn(min = 44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val d = if (distanceM < 1000) "${(distanceM / 10).roundToInt() * 10} m" else String.format(java.util.Locale("el", "GR"), "%.1f km", distanceM / 1000)
            Text(
                if (confirmDelete) "Δεν υπάρχει πια; Πάτα ξανά για σβήσιμο." else "${hazard.kind.emoji}  ${hazard.kind.label} σε $d",
                style = MotoType.title.copy(fontSize = 18.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
            )
        }
    }
}

/** Οι αναφορές στον χάρτη: σκούρος κύκλος, χρωματιστό δαχτυλίδι, emoji. */
object HazardLayer {
    private const val SRC = "moto-hazards"
    const val LAYER = "moto-hazards-layer"

    private fun icon(kind: HazardKind, density: Float): Bitmap {
        val size = (32 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val r = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF15171C.toInt()
        cv.drawCircle(r, r, r - density, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * density
        p.color = kind.color
        cv.drawCircle(r, r, r - 2f * density, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f; textAlign = Paint.Align.CENTER }
        val fm = t.fontMetrics
        cv.drawText(kind.emoji, r, r - (fm.ascent + fm.descent) / 2, t)
        return bmp
    }

    fun set(style: Style, list: List<Hazard>, density: Float) {
        if (style.getSource(SRC) == null) style.addSource(GeoJsonSource(SRC))
        if (style.getImage("hz-" + HazardKind.OBJECT.name) == null) {
            HazardKind.entries.forEach { style.addImage("hz-" + it.name, icon(it, density)) }
        }
        if (style.getLayer(LAYER) == null) {
            val layer = SymbolLayer(LAYER, SRC).withProperties(
                PropertyFactory.iconImage(Expression.concat(Expression.literal("hz-"), Expression.get("kind"))),
                PropertyFactory.iconSize(
                    Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(10, 0.6f), Expression.stop(15, 1f)),
                ),
                PropertyFactory.iconAllowOverlap(true),
            )
            layer.minZoom = 10f
            val below = LocationComponentConstants.SHADOW_LAYER
            if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        }
        (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(list.map {
                Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                    addStringProperty("id", it.id)
                    addStringProperty("kind", it.kind.name)
                }
            }),
        )
    }
}
