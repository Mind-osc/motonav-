package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.weather.Sky
import app.motonav.weather.WeatherCodes
import app.motonav.weather.WeatherPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import app.motonav.i18n.tr
import app.motonav.i18n.trf

/**
 * Μίνιμαλ καιρός: εικονίδιο + θερμοκρασία εκεί που είσαι, και προειδοποίηση για το πρώτο
 * «κακό» σημείο μπροστά (βροχή ≥ 50%, καταιγίδα, χιόνι, δυνατός άνεμος, παγωνιά).
 * Πάτημα: λίστα με όλα τα σημεία της διαδρομής.
 */
@Composable
fun WeatherPill(points: List<WeatherPoint>, progressM: Double, startExpanded: Boolean = false, onClose: (() -> Unit)? = null) {
    if (points.isEmpty()) return
    val c = LocalAppColors.current
    var expanded by remember { mutableStateOf(startExpanded) }
    val ahead = points.filter { it.atMeters >= progressM - 2_000 }.ifEmpty { listOf(points.last()) }
    val here = ahead.first()
    val warn = ahead.drop(1).firstOrNull { it.isBad } ?: here.takeIf { it.isBad && ahead.size == 1 }

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = c.base.surface.copy(alpha = 0.94f), border = BorderStroke(1.dp, c.outline),
        onClick = { if (onClose != null) onClose() else expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(here.sky, Modifier.size(22.dp))
                Spacer(Modifier.width(Space.xs))
                Text("${here.tempC.roundToInt()}°", style = MotoType.title, color = c.base.text)
                if (warn != null && warn !== here) {
                    Spacer(Modifier.width(Space.sm))
                    WeatherIcon(warn.sky, Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        trf("{0} σε {1} km", warnText(warn), ((warn.atMeters - progressM).coerceAtLeast(0.0) / 1000).roundToInt()),
                        style = MotoType.label,
                        color = c.base.warning,
                    )
                }
            }
            if (expanded && points.size > 1) {
                Column(Modifier.padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ahead.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${((p.atMeters - progressM).coerceAtLeast(0.0) / 1000).roundToInt()} km",
                                style = MotoType.label,
                                color = c.base.textMuted,
                                modifier = Modifier.width(52.dp),
                            )
                            Text(timeOf(p.etaEpochS), style = MotoType.label, color = c.base.textMuted, modifier = Modifier.width(48.dp))
                            WeatherIcon(p.sky, Modifier.size(16.dp))
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                "${p.tempC.roundToInt()}° · ${p.rainProb}% · ${p.windKmh.roundToInt()} km/h",
                                style = MotoType.label,
                                color = if (p.isBad) c.base.warning else c.base.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun warnText(p: WeatherPoint): String = when {
    p.sky == Sky.STORM -> tr("καταιγίδα")
    p.sky == Sky.SNOW -> tr("χιόνι")
    p.rainProb >= 50 -> trf("βροχή {0}%", p.rainProb)
    p.windKmh >= 45 -> trf("αέρας {0}", p.windKmh.roundToInt())
    p.tempC <= 2 -> tr("παγωνιά")
    else -> WeatherCodes.label(p.sky)
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private fun timeOf(epochS: Long): String = Instant.ofEpochSecond(epochS).atZone(ZoneId.systemDefault()).format(hhmm)

/** Καιρός μέσα στο πάνω καρτελάκι: εικονίδιο + θερμοκρασία (+ εικονίδιο προειδοποίησης). */
@Composable
fun WeatherInline(points: List<WeatherPoint>, progressM: Double, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    if (points.isEmpty()) return
    val ahead = points.filter { it.atMeters >= progressM - 2_000 }.ifEmpty { listOf(points.last()) }
    val here = ahead.first()
    val warn = ahead.drop(1).firstOrNull { it.isBad }
    Row(
        Modifier.clickable(onClickLabel = tr("Καιρός στη διαδρομή"), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherIcon(here.sky, Modifier.size(20.dp))
        Spacer(Modifier.width(3.dp))
        OutlinedText("${here.tempC.roundToInt()}°", style)
        if (warn != null) {
            Spacer(Modifier.width(4.dp))
            WeatherIcon(warn.sky, Modifier.size(16.dp))
        }
    }
}

/** Δικά μας απλά εικονίδια καιρού (χωρίς emoji, στο χρώμα του θέματος). */
@Composable
fun WeatherIcon(sky: Sky, modifier: Modifier) {
    val c = LocalAppColors.current
    val sun = c.base.warning
    val cloud = c.base.textMuted
    val water = c.base.info
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = w * 0.09f
        fun drawSun(center: Offset, r: Float) {
            drawCircle(sun, r, center)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val p1 = Offset(center.x + (Math.cos(a) * r * 1.45).toFloat(), center.y + (Math.sin(a) * r * 1.45).toFloat())
                val p2 = Offset(center.x + (Math.cos(a) * r * 1.9).toFloat(), center.y + (Math.sin(a) * r * 1.9).toFloat())
                drawLine(sun, p1, p2, s * 0.7f, StrokeCap.Round)
            }
        }
        fun drawCloud(color: Color, dy: Float = 0f) {
            drawCircle(color, w * 0.18f, Offset(w * 0.34f, h * 0.52f + dy))
            drawCircle(color, w * 0.24f, Offset(w * 0.55f, h * 0.42f + dy))
            drawCircle(color, w * 0.16f, Offset(w * 0.76f, h * 0.55f + dy))
            drawRect(color, Offset(w * 0.34f, h * 0.5f + dy), androidx.compose.ui.geometry.Size(w * 0.42f, h * 0.21f))
        }
        when (sky) {
            Sky.CLEAR -> drawSun(Offset(w / 2, h / 2), w * 0.2f)
            Sky.PARTLY -> {
                drawSun(Offset(w * 0.38f, h * 0.36f), w * 0.15f)
                drawCloud(cloud, dy = h * 0.12f)
            }
            Sky.CLOUDY -> drawCloud(cloud)
            Sky.FOG -> for (k in 0 until 3) {
                val y = h * (0.35f + k * 0.18f)
                drawLine(cloud, Offset(w * 0.15f, y), Offset(w * 0.85f, y), s, StrokeCap.Round)
            }
            Sky.DRIZZLE, Sky.RAIN -> {
                drawCloud(cloud, dy = -h * 0.12f)
                val n = if (sky == Sky.RAIN) 3 else 2
                for (k in 0 until n) {
                    val x = w * (0.35f + k * 0.17f)
                    drawLine(water, Offset(x, h * 0.72f), Offset(x - w * 0.06f, h * 0.92f), s * 0.8f, StrokeCap.Round)
                }
            }
            Sky.SNOW -> {
                drawCloud(cloud, dy = -h * 0.12f)
                for (k in 0 until 3) drawCircle(Color.White, s * 0.7f, Offset(w * (0.35f + k * 0.17f), h * 0.84f))
            }
            Sky.STORM -> {
                drawCloud(cloud, dy = -h * 0.14f)
                val bolt = Path().apply {
                    moveTo(w * 0.55f, h * 0.6f); lineTo(w * 0.42f, h * 0.8f); lineTo(w * 0.54f, h * 0.8f); lineTo(w * 0.46f, h * 1.0f)
                }
                drawPath(bolt, sun, style = Stroke(s * 0.9f, cap = StrokeCap.Round))
            }
        }
    }
}
