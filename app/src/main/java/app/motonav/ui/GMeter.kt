package app.motonav.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motonav.sensors.Instruments
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.Radius
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/**
 * Όργανο επιτάχυνσης: ψηλή λεπτή λωρίδα στη δεξιά άκρη, με κέντρο στη μέση.
 * - Γκάζι: πράσινο από τη μέση προς τα κάτω (πίσω), όσο πιο δυνατά τόσο μεγαλώνει.
 * - Φρένο: κόκκινο από τη μέση προς τα πάνω (μπρος).
 * Γραμμούλες στα μέγιστα. Πάνω, ενωμένο με τη λωρίδα προς τα αριστερά, κουτάκι με g, μέγιστα και 0–100.
 * Πάτημα στο κουτάκι = μηδενισμός.
 */
@Composable
fun GBar(i: Instruments, onReset: () -> Unit, totalWidth: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, portrait: Boolean = false) {
    val c = LocalAppColors.current
    val green = c.base.success
    val red = c.base.danger
    // Όλο το όργανο (κουτάκι + λωρίδα) έχει το φάρδος των άλλων: κουμπιού στο κάθετο, πυξίδας στο οριζόντιο.
    val barW = if (portrait) 9.dp else 12.dp
    val panelW = totalWidth - barW
    val panelH = if (portrait) 78.dp else 56.dp
    val full = 1.0f // μισή λωρίδα = 1 g
    val g by animateFloatAsState(
        i.gLon.coerceIn(-full, full),
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "g",
    )
    val maxA by animateFloatAsState(i.maxAccel.coerceAtMost(full), label = "maxA")
    val maxB by animateFloatAsState(i.maxBrake.coerceAtMost(full), label = "maxB")

    Box(
        modifier
            .width(panelW + barW)
            .fillMaxHeight()
            .drawBehind {
                val bw = barW.toPx()
                val pw = panelW.toPx()
                val ph = panelH.toPx()
                val r = Radius.md.toPx()
                val h = size.height
                val x0 = size.width - bw
                // Ένα ενιαίο σχήμα: λωρίδα + κουτάκι κάτω αριστερά, με ένα περίγραμμα.
                val bar = Path().apply { addRoundRect(RoundRect(Rect(x0, 0f, size.width, h), CornerRadius(bw / 2))) }
                val panel = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(x0 - pw, 0f, x0 + bw / 2, ph),
                            topLeft = CornerRadius(r), bottomLeft = CornerRadius(r),
                            topRight = CornerRadius.Zero, bottomRight = CornerRadius.Zero,
                        ),
                    )
                }
                val shape = Path.combine(PathOperation.Union, bar, panel)
                drawPath(shape, c.base.surface.copy(alpha = 0.92f))

                // Γέμισμα της λωρίδας (μέσα στη λωρίδα, λίγο πιο στενό).
                val inset = 3.dp.toPx()
                val top = ph + inset // η λωρίδα «παίζει» κάτω από το κουτάκι
                val bottom = h - inset
                val mid = (top + bottom) / 2
                val half = (bottom - top) / 2
                val fx = x0 + inset
                val fw = bw - inset * 2
                val track = Path().apply { addRoundRect(RoundRect(Rect(fx, top, fx + fw, bottom), CornerRadius(fw / 2))) }
                clipPath(track) {
                    drawRect(c.base.surface2, Offset(fx, top), Size(fw, bottom - top))
                    if (g > 0.005f) {
                        val len = g / full * half
                        drawRect(
                            Brush.verticalGradient(listOf(green.copy(alpha = 0.55f), green), startY = mid, endY = mid + len),
                            Offset(fx, mid), Size(fw, len),
                        )
                    } else if (g < -0.005f) {
                        val len = -g / full * half
                        drawRect(
                            Brush.verticalGradient(listOf(red, red.copy(alpha = 0.55f)), startY = mid - len, endY = mid),
                            Offset(fx, mid - len), Size(fw, len),
                        )
                    }
                }
                // Κλίμακα: 0,5 g και 1 g (λεπτές), κέντρο (πιο έντονο).
                val scale = c.base.textMuted.copy(alpha = 0.45f)
                for (k in listOf(-1f, -0.5f, 0.5f, 1f)) {
                    val y = mid + k * half
                    drawLine(scale, Offset(x0 + 2f, y), Offset(x0 + bw * 0.35f, y), 1.5f)
                }
                drawLine(c.base.text.copy(alpha = 0.8f), Offset(x0 + 1f, mid), Offset(size.width - 1f, mid), 2.dp.toPx())
                // Μέγιστα.
                if (maxA > 0.02f) {
                    val y = mid + maxA / full * half
                    drawLine(green, Offset(x0 - 3f, y), Offset(size.width + 1f, y), 2.dp.toPx())
                }
                if (maxB > 0.02f) {
                    val y = mid - maxB / full * half
                    drawLine(red, Offset(x0 - 3f, y), Offset(size.width + 1f, y), 2.dp.toPx())
                }
                drawPath(shape, c.outline, style = Stroke(1.dp.toPx()))
            },
    ) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .size(panelW, panelH)
                .pointerInput(Unit) { detectTapGestures(onTap = { onReset() }) }
                .padding(start = if (portrait) 5.dp else 8.dp, end = 1.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val now = i.gLon
            Text(
                (if (now >= 0) "+" else "−") + fmt(now) + (if (portrait) "" else " g"),
                fontSize = if (portrait) 12.sp else 15.sp, lineHeight = if (portrait) 14.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = when {
                    now > 0.05f -> green
                    now < -0.05f -> red
                    else -> c.base.text
                },
            )
            if (portrait) {
                Text("▼${fmt(i.maxAccel)}", fontSize = 10.sp, lineHeight = 12.sp, color = c.base.textMuted, maxLines = 1)
                Text("▲${fmt(i.maxBrake)}", fontSize = 10.sp, lineHeight = 12.sp, color = c.base.textMuted, maxLines = 1)
            } else {
                Text("▼ ${fmt(i.maxAccel)} ▲ ${fmt(i.maxBrake)}", fontSize = 10.sp, lineHeight = 13.sp, color = c.base.textMuted)
            }
            ZeroTo100(i, portrait)
        }
    }
}

@Composable
private fun ZeroTo100(i: Instruments, portrait: Boolean) {
    val c = LocalAppColors.current
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    val start = i.runStartMs
    LaunchedEffect(start) {
        while (start != null) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    val last = i.last0to100
    val text = when {
        start != null -> (if (portrait) "0–100\n" else "0–100 ") + sec((now - start) / 1000f)
        last != null -> (if (portrait) "0–100\n" else "0–100 ") + sec(last) +
            (if (portrait) "" else i.best0to100?.takeIf { abs(it - last) > 0.05f }?.let { " · ${sec(it)}" } ?: "")
        else -> if (portrait) "0–100\n–" else "0–100 –"
    }
    Text(
        text, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2,
        fontWeight = if (start != null) FontWeight.Bold else FontWeight.Normal,
        color = if (start != null) c.accent else c.base.textMuted,
    )
}

private fun fmt(g: Float) = String.format(Locale.US, "%.2f", abs(g))
private fun sec(s: Float) = String.format(Locale.US, "%.1f″", s.coerceAtLeast(0f))
