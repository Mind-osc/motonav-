package app.motonav.ui

import kotlin.math.roundToInt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motonav.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Γραμματοσειρά της μπάρας κατάστασης.
 * Pointer (Funtype Co., μόνο για προσωπική χρήση): υπάρχει μόνο στο private repo, δεν πάει στο public.
 * Αν λείπει (public build), Barlow Condensed SemiBold (SIL OFL 1.1, licenses/BarlowCondensed-OFL.txt).
 */
@Composable
fun rememberStatusFont(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        @Suppress("DiscouragedApi")
        val pointer = ctx.resources.getIdentifier("pointer", "font", ctx.packageName)
        FontFamily(Font(if (pointer != 0) pointer else R.font.barlow_condensed_semibold))
    }
}

data class BatteryState(val percent: Int = 100, val charging: Boolean = false)

/** Μπαταρία από το Android (ACTION_BATTERY_CHANGED). */
@Composable
fun rememberBattery(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState()) }
    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0 && scale > 0) {
                state = BatteryState(
                    percent = (level * 100 / scale).coerceIn(0, 100),
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read(i)
        }
        read(context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    return state
}

/** Χρώμα ανά στάθμη, όπως στη σειρά που έστειλες: κόκκινο → κίτρινο → λαχανί → πράσινο. */
fun batteryColor(percent: Int): Color = when {
    percent <= 20 -> Color(0xFFE8262A)
    percent <= 40 -> Color(0xFFF5E52D)
    percent <= 60 -> Color(0xFFA6CE39)
    percent <= 80 -> Color(0xFF6DBE45)
    else -> Color(0xFF12B04B)
}

/** Πόσα από τα 5 κομμάτια είναι γεμάτα (τουλάχιστον 1 όσο έχει έστω λίγο). */
fun batterySegments(percent: Int): Int = if (percent <= 0) 0 else ((percent + 19) / 20).coerceIn(1, 5)

/**
 * Οριζόντια μπαταρία με 5 κομμάτια. Φορτίζει: τα κομμάτια γεμίζουν ένα-ένα ξανά και ξανά, με κεραυνό.
 * Κάτω από 10%: αναβοσβήνει απαλά.
 */
@Composable
fun BatteryIcon(b: BatteryState, width: Dp = 40.dp) {
    val t = rememberInfiniteTransition(label = "battery")
    val wave by t.animateFloat(0f, 6f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "wave")
    val blink by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    val col = if (b.charging) Color(0xFF12B04B) else batteryColor(b.percent)
    val alpha = if (!b.charging && b.percent < 10) blink else 1f
    Canvas(Modifier.size(width, width * 0.48f)) {
        val s = size.height / 19f // κλίμακα: όλη η μπαταρία 40×19
        val stroke = 2f * s
        val bodyW = size.width - 4f * s
        val edge = Color.Black.copy(alpha = 0.75f * alpha)
        val e = 1.6f * s // πάχος σκούρου περιγράμματος
        // σκούρο περίγραμμα πρώτα (για να φαίνεται πάνω σε κάθε χάρτη), μετά το χρώμα
        drawRoundRect(edge, Offset(stroke / 2, stroke / 2), Size(bodyW - stroke, size.height - stroke), CornerRadius(4f * s), style = Stroke(stroke + 2 * e))
        drawRoundRect(edge, Offset(bodyW - e, size.height * 0.3f - e), Size(3.2f * s + 2 * e, size.height * 0.4f + 2 * e), CornerRadius(2f * s))
        // κέλυφος και «κεφάλι» δεξιά
        drawRoundRect(col.copy(alpha = alpha), Offset(stroke / 2, stroke / 2), Size(bodyW - stroke, size.height - stroke), CornerRadius(4f * s), style = Stroke(stroke))
        drawRoundRect(col.copy(alpha = alpha), Offset(bodyW, size.height * 0.3f), Size(3.2f * s, size.height * 0.4f), CornerRadius(1.5f * s))
        // 5 κομμάτια
        val pad = 3.4f * s
        val gap = 1.4f * s
        val innerW = bodyW - 2 * pad
        val segW = (innerW - 4 * gap) / 5
        val filled = if (b.charging) maxOf(batterySegments(b.percent), wave.toInt().coerceAtMost(5)) else batterySegments(b.percent)
        for (i in 0 until filled) {
            drawRoundRect(
                edge,
                Offset(pad + i * (segW + gap), pad),
                Size(segW, size.height - 2 * pad),
                CornerRadius(1.2f * s),
                style = Stroke(1.2f * s),
            )
            drawRoundRect(
                col.copy(alpha = alpha),
                Offset(pad + i * (segW + gap), pad),
                Size(segW, size.height - 2 * pad),
                CornerRadius(1.2f * s),
            )
        }
        if (b.charging) {
            val bolt = Path().apply {
                moveTo(bodyW * 0.56f, size.height * 0.08f)
                lineTo(bodyW * 0.32f, size.height * 0.56f)
                lineTo(bodyW * 0.49f, size.height * 0.56f)
                lineTo(bodyW * 0.42f, size.height * 0.92f)
                lineTo(bodyW * 0.68f, size.height * 0.42f)
                lineTo(bodyW * 0.51f, size.height * 0.42f)
                close()
            }
            drawPath(bolt, Color.Black, style = Stroke(2.2f * s, join = StrokeJoin.Round))
            drawPath(bolt, Color.White)
        }
    }
}

/** Μικρός δορυφόρος: σώμα στη μέση και δύο ηλιακά πάνελ, λοξά. */
@Composable
fun SatelliteIcon(color: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 16f
        val edge = Color.Black.copy(alpha = 0.75f)
        rotate(-35f) {
            val c = center
            val o = Stroke(1.6f * u)
            drawRect(edge, Offset(c.x - 2.2f * u, c.y - 2.6f * u), Size(4.4f * u, 5.2f * u), style = o)
            drawRect(edge, Offset(c.x - 7.6f * u, c.y - 1.8f * u), Size(4.2f * u, 3.6f * u), style = o)
            drawRect(edge, Offset(c.x + 3.4f * u, c.y - 1.8f * u), Size(4.2f * u, 3.6f * u), style = o)
            drawRect(color, Offset(c.x - 2.2f * u, c.y - 2.6f * u), Size(4.4f * u, 5.2f * u))
            drawRect(color, Offset(c.x - 7.6f * u, c.y - 1.8f * u), Size(4.2f * u, 3.6f * u))
            drawRect(color, Offset(c.x + 3.4f * u, c.y - 1.8f * u), Size(4.2f * u, 3.6f * u))
            drawLine(color, Offset(c.x - 3.4f * u, c.y), Offset(c.x - 2.2f * u, c.y), strokeWidth = 1.2f * u)
            drawLine(color, Offset(c.x + 2.2f * u, c.y), Offset(c.x + 3.4f * u, c.y), strokeWidth = 1.2f * u)
        }
        // κεραία / σήμα
        drawArc(edge, 200f, 70f, false, Offset(center.x - 1f * u, center.y + 1f * u), Size(7f * u, 7f * u), style = Stroke(2.8f * u))
        drawArc(color, 200f, 70f, false, Offset(center.x - 1f * u, center.y + 1f * u), Size(7f * u, 7f * u), style = Stroke(1.2f * u))
    }
}

/** Κείμενο με λεπτό σκούρο περίγραμμα, για να διαβάζεται πάνω σε ανοιχτό και σκούρο χάρτη. */
@Composable
fun OutlinedText(text: String, style: TextStyle) {
    androidx.compose.foundation.layout.Box {
        Text(text, style = style.copy(color = Color.Black.copy(alpha = 0.8f), shadow = null, drawStyle = Stroke(width = 5f, join = StrokeJoin.Round)))
        Text(text, style = style.copy(shadow = null))
    }
}

/**
 * Πάνω πάνω, στη θέση της γραμμής του κινητού: ώρα αριστερά, δορυφόροι και μπαταρία δεξιά.
 * Μόνο εικονίδια και νούμερα πάνω στον χάρτη, χωρίς φόντο (με σκιά για να διαβάζονται).
 */
@Composable
fun StatusStrip(satellites: Int?, gpsOn: Boolean, modifier: Modifier = Modifier, altitudeM: Double? = null, weather: (@Composable (TextStyle) -> Unit)? = null) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    val battery = rememberBattery()
    val shadow = Shadow(Color.Black, Offset(0f, 1f), 4f)
    val style = TextStyle(fontFamily = rememberStatusFont(), fontSize = 15.sp, color = Color.White, shadow = shadow)
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.Top) {
        Tab {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedText(SimpleDateFormat("HH:mm", Locale.US).format(now), style.copy(fontSize = 17.sp))
                // Υψόμετρο δεξιά από την ώρα.
                if (altitudeM != null) {
                    Spacer(Modifier.width(10.dp))
                    OutlinedText("▲", style.copy(fontSize = 11.sp, color = Color(0xFF9AA0A6)))
                    Spacer(Modifier.width(3.dp))
                    OutlinedText("${altitudeM.roundToInt()}m", style)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Tab {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weather?.let {
                it(style)
                Spacer(Modifier.width(8.dp))
            }
            val satColor = when {
                !gpsOn || satellites == null || satellites == 0 -> Color(0xFF9AA0A6)
                satellites < 4 -> Color(0xFFF5E52D)
                else -> Color.White
            }
            SatelliteIcon(satColor)
            OutlinedText(if (!gpsOn) "off" else (satellites ?: 0).toString(), style.copy(color = satColor))
            Spacer(Modifier.width(8.dp))
            BatteryIcon(battery)
            }
        }
    }
}

/** «Καρτελάκι» που κατεβαίνει από την πάνω άκρη της οθόνης: ίσιο πάνω, στρογγυλό κάτω. */
@Composable
private fun Tab(content: @Composable () -> Unit) {
    val c = app.motonav.ui.theme.LocalAppColors.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxHeight()
            .background(c.base.surface.copy(alpha = 0.92f), shape)
            // Περίγραμμα μόνο αριστερά, κάτω και δεξιά: όχι στην άκρη που ακουμπάει την οθόνη.
            .drawBehind {
                val w = 1.dp.toPx()
                val r = 14.dp.toPx()
                val h = w / 2
                val path = Path().apply {
                    moveTo(h, 0f)
                    lineTo(h, size.height - r)
                    arcTo(androidx.compose.ui.geometry.Rect(h, size.height - 2 * r + h, 2 * r - h, size.height - h), 180f, -90f, false)
                    lineTo(size.width - r, size.height - h)
                    arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * r + h, size.height - 2 * r + h, size.width - h, size.height - h), 90f, -90f, false)
                    lineTo(size.width - h, 0f)
                }
                drawPath(path, c.outline, style = Stroke(w))
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
