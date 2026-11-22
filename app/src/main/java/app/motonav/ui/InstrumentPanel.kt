package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Μικρό κλισιόμετρο. Πάτημα = μηδενισμός μέγιστων. */
@Composable
fun LeanWidget(lean: Float, maxLeft: Float, maxRight: Float, width: Dp, onResetMax: () -> Unit) {
    val c = LocalAppColors.current
    Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface, border = BorderStroke(1.dp, c.outline), onClick = onResetMax) {
        Column(
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LeanGauge(lean, maxLeft, maxRight, Modifier.size(width = width - 8.dp, height = (width - 8.dp) * 0.5f))
            Text(leanLabel(lean), style = MotoType.label, color = c.base.text)
            Text(
                "${maxLeft.roundToInt()}° · ${maxRight.roundToInt()}°",
                style = MotoType.label.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = c.base.textMuted,
            )
        }
    }
}

/** Στρογγυλή πυξίδα: ο δίσκος γυρίζει, το σημάδι πάνω είναι η κατεύθυνσή σου, οι μοίρες στη μέση. */
@Composable
fun CompassDial(headingDeg: Float?, size: Dp) {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val letterStyle = TextStyle(fontSize = maxOf(size.value * 0.16f, 9f).sp, fontWeight = FontWeight.Bold)
    Surface(shape = CircleShape, color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val r = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val heading = headingDeg ?: 0f
                // Δίσκος: γυρίζει ανάποδα από την κατεύθυνση, ώστε το Β να δείχνει πάντα τον βορρά.
                rotate(-heading, center) {
                    for (deg in 0 until 360 step 30) {
                        val rad = Math.toRadians(deg.toDouble())
                        val outer = r * 0.92f
                        val inner = if (deg % 90 == 0) r * 0.80f else r * 0.84f
                        drawLine(
                            c.base.textMuted,
                            Offset(center.x + (sin(rad) * inner).toFloat(), center.y - (cos(rad) * inner).toFloat()),
                            Offset(center.x + (sin(rad) * outer).toFloat(), center.y - (cos(rad) * outer).toFloat()),
                            strokeWidth = r * 0.04f,
                            cap = StrokeCap.Round,
                        )
                    }
                    listOf(0 to "Β", 90 to "Α", 180 to "Ν", 270 to "Δ").forEach { (deg, letter) ->
                        val rad = Math.toRadians(deg.toDouble())
                        val pos = Offset(center.x + (sin(rad) * r * 0.62f).toFloat(), center.y - (cos(rad) * r * 0.62f).toFloat())
                        val layout = measurer.measure(letter, letterStyle.copy(color = if (deg == 0) c.base.danger else c.base.text))
                        // τα γράμματα μένουν όρθια
                        rotate(heading, pos) {
                            drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
                        }
                    }
                }
                // Σταθερό σημάδι πάνω: προς τα εκεί πας.
                val mark = Path().apply {
                    moveTo(center.x, center.y - r * 0.99f)
                    lineTo(center.x - r * 0.11f, center.y - r * 0.78f)
                    lineTo(center.x + r * 0.11f, center.y - r * 0.78f)
                    close()
                }
                drawPath(mark, c.accent)
                // Κυκλάκι στη μέση
                drawCircle(c.base.surface2, r * 0.36f, center)
                drawCircle(c.base.border, r * 0.36f, center, style = Stroke(r * 0.03f))
            }
            Text(
                headingDeg?.let { "${it.roundToInt() % 360}°" } ?: "—",
                style = MotoType.label.copy(fontSize = maxOf(size.value * 0.17f, 10f).sp, lineHeight = maxOf(size.value * 0.19f, 11f).sp),
                color = c.base.text,
            )
        }
    }
}

/** Στρογγυλό υψόμετρο. */
@Composable
fun AltitudeBadge(altitudeM: Double?, size: Dp, gradePct: Double? = null) {
    val c = LocalAppColors.current
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(app.motonav.ui.theme.Radius.md), color = c.base.surface2, border = BorderStroke(1.dp, c.outline), modifier = Modifier.size(size)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            // Κλίση δρόμου: ↗ ανηφόρα, ↘ κατηφόρα, → σχεδόν επίπεδο.
            val g = gradePct?.roundToInt()
            Text(
                when {
                    g == null -> "— %"
                    g >= 2 -> "↗$g%"
                    g <= -2 -> "↘${-g}%"
                    else -> "→0%"
                },
                style = MotoType.label.copy(fontSize = maxOf(size.value * 0.17f, 10f).sp, lineHeight = maxOf(size.value * 0.19f, 11f).sp),
                color = when {
                    g == null -> c.base.textMuted
                    g >= 8 || g <= -8 -> c.base.warning
                    else -> c.accent
                },
            )
            Text(
                altitudeM?.roundToInt()?.toString() ?: "—",
                style = MotoType.title.copy(fontSize = (size.value * 0.27f).sp, lineHeight = (size.value * 0.29f).sp),
                color = c.base.text,
            )
            Text("m", style = MotoType.label.copy(fontSize = (size.value * 0.13f).sp, lineHeight = (size.value * 0.15f).sp), color = c.base.textMuted)
        }
    }
}

@Composable
private fun LeanGauge(lean: Float, maxLeft: Float, maxRight: Float, modifier: Modifier) {
    val c = LocalAppColors.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = h * 0.12f
        val r = minOf(w / 2f, h) - stroke
        val center = Offset(w / 2f, h - stroke / 2f)
        drawArc(
            color = c.base.border,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        fun point(deg: Float, radius: Float): Offset {
            val rad = Math.toRadians(deg.toDouble())
            return Offset(center.x + (sin(rad) * radius).toFloat(), center.y - (cos(rad) * radius).toFloat())
        }
        listOf(-maxLeft, maxRight).filter { abs(it) > 1f }.forEach {
            drawLine(c.base.textMuted, point(it, r - stroke), point(it, r + stroke * 0.6f), stroke * 0.5f, StrokeCap.Round)
        }
        drawLine(c.accent, center, point(lean.coerceIn(-70f, 70f), r), stroke * 0.8f, StrokeCap.Round)
        drawCircle(c.accent, stroke * 0.7f, center)
    }
}

private val cardinals = listOf("Β", "ΒΑ", "Α", "ΝΑ", "Ν", "ΝΔ", "Δ", "ΒΔ")

fun cardinal(deg: Float): String = cardinals[(((deg % 360f) + 360f) % 360f / 45f).roundToInt() % 8]

fun leanLabel(lean: Float): String {
    val v = abs(lean).roundToInt()
    return when {
        v == 0 -> "0°"
        lean < 0 -> "$v° Α"
        else -> "$v° Δ"
    }
}
