package app.motonav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.motonav.routing.ElevationProfile
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Προφίλ υψομέτρου: γεμάτη περιοχή στο χρώμα του θέματος, και από κάτω ↑ ανάβαση · ↓ κατάβαση · χαμηλό–ψηλό.
 * progressM: πού είσαι τώρα (στην πλοήγηση), με κάθετη γραμμή και κουκίδα.
 */
@Composable
fun ElevationChart(profile: ElevationProfile, progressM: Double? = null, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    val c = LocalAppColors.current
    val accent = c.accent
    val muted = c.base.textMuted
    val text = c.base.text
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val s = profile.samples
            val len = profile.lengthM.takeIf { it > 0 } ?: return@Canvas
            // Τουλάχιστον 80 m εύρος, για να μη φαίνεται «βουνό» ένας λόφος 10 μέτρων.
            val span = maxOf(80.0, profile.maxM - profile.minM)
            val bottom = profile.minM - (span - (profile.maxM - profile.minM)) / 2
            fun x(m: Double) = (m / len * size.width).toFloat()
            fun y(e: Double) = (size.height - (e - bottom) / span * (size.height - 4f) - 2f).toFloat()
            val line = Path().apply {
                moveTo(x(s.first().first), y(s.first().second))
                s.drop(1).forEach { lineTo(x(it.first), y(it.second)) }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(s.last().first), size.height)
                lineTo(x(s.first().first), size.height)
                close()
            }
            val done = progressM?.coerceIn(0.0, len)
            drawPath(fill, accent.copy(alpha = 0.22f))
            drawPath(line, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (done != null) {
                val px = x(done)
                // ό,τι πέρασες πιο σβηστό
                drawRect(muted.copy(alpha = 0.35f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(px, size.height))
                drawLine(text, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.5.dp.toPx())
                val e = s.minByOrNull { kotlin.math.abs(it.first - done) }?.second
                if (e != null) drawCircle(text, 3.5.dp.toPx(), Offset(px, y(e)))
            }
        }
        val el = Locale("el", "GR")
        fun m(v: Double) = String.format(el, "%,d m", v.roundToInt())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val left = if (progressM != null) "μένει ↑ ${m(profile.ascentFrom(progressM))}" else "↑ ${m(profile.ascentM)}  ↓ ${m(profile.descentM)}"
            Text(left, style = MotoType.label, color = text)
            Text("${m(profile.minM)} – ${m(profile.maxM)}", style = MotoType.label, color = muted)
        }
    }
}
