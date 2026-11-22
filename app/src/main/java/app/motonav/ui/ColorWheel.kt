package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Μικρό κυκλάκι-ουράνιο τόξο: ανοίγει τον κύκλο χρωμάτων. */
@Composable
fun WheelSwatch(selected: Boolean, current: Int?, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val rainbow = remember { Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)) }
    ColorSwatch(Color.Transparent, selected, "Όποιο χρώμα θες", onClick = onClick) {
        Canvas(Modifier.size(40.dp)) {
            drawCircle(rainbow)
            if (selected && current != null) drawCircle(Color(current), radius = size.minDimension * 0.24f)
        }
    }
}

/**
 * Κύκλος χρωμάτων: γύρω-γύρω η απόχρωση, από το κέντρο προς τα έξω το πόσο έντονο (κέντρο = λευκό),
 * και από κάτω η φωτεινότητα (τέρμα αριστερά = μαύρο).
 */
@Composable
fun ColorWheelDialog(initial: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial ?: c.accent.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

    fun pick(o: Offset, w: Float, h: Float) {
        val r = min(w, h) / 2f
        val dx = o.x - w / 2f
        val dy = o.y - h / 2f
        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
        sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(Radius.lg), color = c.base.surface, border = BorderStroke(1.dp, c.outline)) {
            Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Διάλεξε χρώμα", style = MotoType.title, color = c.base.text)
                val hues = remember {
                    Brush.sweepGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })
                }
                Box(
                    Modifier
                        .size(260.dp)
                        .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
                        .pointerInput(Unit) { detectDragGestures { ch, _ -> pick(ch.position, size.width.toFloat(), size.height.toFloat()) } },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val r = size.minDimension / 2f
                        drawCircle(hues, r)
                        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
                        // φωτεινότητα: σκοτεινιάζει όλο τον κύκλο
                        drawCircle(Color.Black.copy(alpha = 1f - value), r)
                        val a = Math.toRadians(hue.toDouble())
                        val p = Offset(center.x + (cos(a) * sat * r).toFloat(), center.y + (sin(a) * sat * r).toFloat())
                        drawCircle(Color.Black, 13.dp.toPx(), p, style = Stroke(5.dp.toPx()))
                        drawCircle(Color.White, 13.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                    }
                }
                Text("Φωτεινότητα", style = MotoType.label, color = c.base.textMuted)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextChoice("Λευκό", selected = false, onClick = { sat = 0f; value = 1f })
                    TextChoice("Μαύρο", selected = false, onClick = { value = 0f })
                    Box(Modifier.weight(1f).height(44.dp).background(color, RoundedCornerShape(Radius.md)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                    TextChoice("Άκυρο", selected = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextChoice("OK", selected = true, onClick = { onPick(color.toArgb()) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
