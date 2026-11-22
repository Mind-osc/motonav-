package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ταχύτητα + πυξίδα σε στυλ Apple Watch: γύρω-γύρω στο κουτί γραμμούλες ανά 10° που κινούνται με την
 * κατεύθυνσή σου (πάνω = προς τα εκεί πας), μικρά Α/Ν/Δ, και ο βορράς μια κόκκινη γραμμίτσα.
 * Από πάνω κολλημένη μπάρα κλίσης, κάτω αριστερά κολλημένο κουτάκι υψομέτρου.
 * Πάτημα = κέντραρε σε μένα· παρατεταμένο = βορράς πάνω / κατεύθυνση πάνω.
 */
@Composable
fun SpeedCompass(
    speedKmh: Float?,
    limitKmh: Int?,
    headingDeg: Float?,
    width: Dp,
    height: Dp,
    speedText: TextStyle,
    showCompass: Boolean,
    altitudeM: Double?,
    gradePct: Double?,
    showAltitude: Boolean,
    lean: Float,
    maxLeft: Float,
    maxRight: Float,
    showLean: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onResetLean: () -> Unit,
) {
    val c = LocalAppColors.current
    val over = limitKmh != null && speedKmh != null && speedKmh > limitKmh + 3
    val tabH = height * 0.62f
    val tabW = tabH * 1.05f
    val barH = 9.dp
    val leftW = if (showAltitude) tabW else 0.dp
    val topH = if (showLean) barH else 0.dp
    val outline = if (over) c.base.danger else c.outline
    val fill = c.base.surface
    Box(
        Modifier
            .size(width = leftW + width, height = topH + height)
            // Ένα ενιαίο σχήμα (μπάρα κλίσης + κουτί + κουτάκι υψομέτρου) με ένα περίγραμμα:
            // χωρίς γραμμές ή διπλά περιγράμματα εκεί που ενώνονται.
            .drawBehind {
                val r = Radius.md.toPx()
                val lw = leftW.toPx()
                val th = topH.toPx()
                val w = width.toPx()
                val h = height.toPx()
                val box = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(lw, th, lw + w, th + h),
                            topLeft = if (th > 0) CornerRadius.Zero else CornerRadius(r),
                            topRight = if (th > 0) CornerRadius.Zero else CornerRadius(r),
                            bottomRight = CornerRadius(r),
                            bottomLeft = if (lw > 0) CornerRadius.Zero else CornerRadius(r),
                        ),
                    )
                }
                var shape = box
                if (th > 0) {
                    val bar = Path().apply {
                        addRoundRect(RoundRect(Rect(lw, 0f, lw + w, th + 1f), topLeft = CornerRadius(r * 0.7f), topRight = CornerRadius(r * 0.7f)))
                    }
                    shape = Path.combine(PathOperation.Union, shape, bar)
                }
                if (lw > 0) {
                    val tt = th + h - tabH.toPx()
                    val tab = Path().apply {
                        addRoundRect(RoundRect(Rect(0f, tt, lw + 1f, th + h), topLeft = CornerRadius(r), bottomLeft = CornerRadius(r)))
                    }
                    shape = Path.combine(PathOperation.Union, shape, tab)
                }
                drawPath(shape, fill)
                drawPath(shape, outline, style = Stroke(width = (if (over) 2.dp else 1.dp).toPx()))
            },
    ) {
        if (showLean) {
            LeanBar(
                lean, maxLeft, maxRight,
                Modifier.align(Alignment.TopEnd).size(width = width, height = barH),
                onResetLean,
            )
        }
        if (showAltitude) {
            Box(Modifier.align(Alignment.BottomStart).size(width = tabW, height = tabH)) {
                AltitudeTab(altitudeM, gradePct)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(width = width, height = height)
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) },
            contentAlignment = Alignment.Center,
        ) {
            if (showCompass) CompassBezel(headingDeg, Modifier.fillMaxSize())
            // Μόνο ο αριθμός, ακριβώς στη μέση (χωρίς τον «αέρα» της γραμματοσειράς). Χωρίς km/h.
            Text(
                speedKmh?.roundToInt()?.toString() ?: "—",
                style = speedText.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    lineHeight = speedText.fontSize,
                ),
                color = if (over) c.base.danger else c.base.text,
            )
            // Όριο: σήμα ΚΟΚ πάνω αριστερά στο κουτί της ταχύτητας.
            if (limitKmh != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-10).dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.5.dp, Color(0xFFD32F2F), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$limitKmh", style = MotoType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Color(0xFF111111))
                }
            }
        }
    }
}

/** Σημείο στην περίμετρο ενός «στρογγυλεμένου τετραγώνου» (υπερέλλειψη) στη γωνία theta (0 = πάνω, δεξιόστροφα). */
private fun squircle(theta: Double, a: Float, b: Float, k: Float): Offset {
    val dx = sin(theta)
    val dy = -cos(theta)
    val n = 5.0
    val t = 1.0 / ((abs(dx) / a).pow(n) + (abs(dy) / b).pow(n)).pow(1.0 / n)
    return Offset((dx * t * k).toFloat(), (dy * t * k).toFloat())
}

@Composable
private fun CompassBezel(headingDeg: Float?, modifier: Modifier) {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    // Ομαλή περιστροφή (από 350° σε 10° από τον κοντινό δρόμο, όχι ανάποδα όλο τον κύκλο).
    val last = androidx.compose.runtime.remember { floatArrayOf(headingDeg ?: 0f) }
    val target = headingDeg?.let { h ->
        val d = ((h - last[0]) % 360f + 540f) % 360f - 180f
        (last[0] + d).also { last[0] = it }
    } ?: last[0]
    val heading by animateFloatAsState(target, tween(350), label = "bezel")
    val letterStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val inset = 3.dp.toPx()
        val a = cx - inset
        val b = cy - inset
        for (deg in 0 until 360 step 10) {
            val theta = Math.toRadians((deg - heading).toDouble())
            val major = deg % 30 == 0
            val outer = squircle(theta, a, b, 1f)
            val inner = squircle(theta, a, b, if (major) 0.86f else 0.91f)
            if (deg == 0) {
                // Βορράς: κόκκινη γραμμίτσα, πιο μακριά και χοντρή.
                val n0 = squircle(theta, a, b, 0.80f)
                drawLine(Color(0xFFFF3B30), Offset(cx + n0.x, cy + n0.y), Offset(cx + outer.x, cy + outer.y), 3.dp.toPx(), StrokeCap.Round)
            } else {
                drawLine(
                    if (major) c.base.text.copy(alpha = 0.75f) else c.base.textMuted.copy(alpha = 0.45f),
                    Offset(cx + inner.x, cy + inner.y), Offset(cx + outer.x, cy + outer.y),
                    (if (major) 1.4f else 1f).dp.toPx(), StrokeCap.Round,
                )
            }
        }
        listOf(90 to "Α", 180 to "Ν", 270 to "Δ").forEach { (deg, letter) ->
            val theta = Math.toRadians((deg - heading).toDouble())
            val p = squircle(theta, a, b, 0.74f)
            val layout = measurer.measure(letter, letterStyle.copy(color = c.base.textMuted))
            drawText(layout, topLeft = Offset(cx + p.x - layout.size.width / 2f, cy + p.y - layout.size.height / 2f))
        }
    }
}

/** Οριζόντια μπάρα κλίσης: από τη μέση γεμίζει αριστερά ή δεξιά. Λεπτές γραμμές = μέγιστα. Πάτημα = μηδενισμός. */
@Composable
private fun LeanBar(lean: Float, maxLeft: Float, maxRight: Float, modifier: Modifier, onReset: () -> Unit) {
    val c = LocalAppColors.current
    val full = 50f
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onReset() }) },
    ) {
        val pad = 4.dp.toPx()
        val w = size.width - 2 * pad
        val mid = size.width / 2f
        val h = 3.5.dp.toPx()
        val y = size.height / 2f + 0.5.dp.toPx()
        drawRoundRect(c.base.surface2, Offset(pad, y - h / 2), Size(w, h), CornerRadius(h / 2))
        // lean < 0 = αριστερά
        val f = (abs(lean) / full).coerceIn(0f, 1f) * w / 2
        val col = if (abs(lean) >= 40f) c.base.warning else c.accent
        if (f > 0.5f) {
            val x0 = if (lean < 0) mid - f else mid
            drawRoundRect(col, Offset(x0, y - h / 2), Size(f, h), CornerRadius(h / 2))
        }
        fun mark(v: Float, left: Boolean) {
            if (v < 2f) return
            val x = if (left) mid - (v / full).coerceIn(0f, 1f) * w / 2 else mid + (v / full).coerceIn(0f, 1f) * w / 2
            drawLine(c.base.textMuted, Offset(x, y - h), Offset(x, y + h), 1.dp.toPx())
        }
        mark(maxLeft, true)
        mark(maxRight, false)
        drawLine(c.base.text.copy(alpha = 0.6f), Offset(mid, y - h), Offset(mid, y + h), 1.dp.toPx())
    }
}

/** Μικρό κουτάκι υψομέτρου, κολλημένο κάτω αριστερά στο κουτί της ταχύτητας. */
@Composable
private fun AltitudeTab(altitudeM: Double?, gradePct: Double?) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val g = gradePct?.roundToInt()
        if (g != null) {
            Text(
                when {
                    g >= 2 -> "↗$g%"
                    g <= -2 -> "↘${-g}%"
                    else -> "→0%"
                },
                style = MotoType.label.copy(fontSize = 10.sp, lineHeight = 11.sp),
                color = if (g >= 8 || g <= -8) c.base.warning else c.accent,
            )
        }
        Text(altitudeM?.roundToInt()?.toString() ?: "—", style = MotoType.title.copy(fontSize = 15.sp, lineHeight = 16.sp), color = c.base.text)
        Text("m", style = MotoType.label.copy(fontSize = 9.sp, lineHeight = 10.sp), color = c.base.textMuted)
    }
}
