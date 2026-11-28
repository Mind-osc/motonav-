package app.motonav.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motonav.cameras.CameraAlerts
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import kotlin.math.roundToInt
import app.motonav.i18n.tr

private val CamYellow = Color(0xFFFFD23F)
private val CamYellowLight = Color(0xFFFFE88A)
private val CamInk = Color(0xFF1A1400)

/**
 * Μικρή κίτρινη μπάρα για κάμερα μπροστά (≤ 300 m): απαλή κίτρινη λάμψη που «ανασαίνει»,
 * απόσταση, όριο (αν είναι γνωστό) και λεπτή γραμμή που γεμίζει όσο πλησιάζεις.
 */
@Composable
fun CameraBar(distanceM: Double, maxspeed: Int?, speedKmh: Float?, title: String = tr("Κάμερα"), onClick: (() -> Unit)? = null) {
    val glowAnim = rememberInfiniteTransition(label = "camera-glow")
    val glow by glowAnim.animateFloat(
        initialValue = 8f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val shape = RoundedCornerShape(Radius.md)
    val over = maxspeed != null && speedKmh != null && speedKmh > maxspeed + 3
    val progress = (1.0 - distanceM / CameraAlerts.RANGE_M).coerceIn(0.0, 1.0).toFloat()

    Box(
        Modifier
            .shadow(glow.dp, shape, clip = false, ambientColor = CamYellow, spotColor = CamYellow)
            .clip(shape)
            .background(CamYellow)
            .border(1.dp, CamYellowLight, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(
                Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraGlyph(Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MotoType.label, color = CamInk)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${((distanceM / 10).roundToInt() * 10).coerceAtLeast(0)} m",
                    style = MotoType.title.copy(fontSize = 18.sp, lineHeight = 20.sp),
                    color = if (over) Color(0xFF8B1E2D) else CamInk,
                )
                if (maxspeed != null) {
                    Spacer(Modifier.width(10.dp))
                    // Σήμα ορίου όπως του ΚΟΚ: λευκός κύκλος, κόκκινο περίγραμμα.
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(3.dp, Color(0xFFD32F2F), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$maxspeed", style = MotoType.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = Color(0xFF111111))
                    }
                }
            }
            // Λεπτή γραμμή: γεμίζει όσο πλησιάζεις.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .drawBehind {
                        drawRect(CamInk.copy(alpha = 0.15f))
                        drawRoundRect(
                            CamInk.copy(alpha = 0.7f),
                            size = Size(size.width * progress, size.height),
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
            )
        }
    }
}

/** Μικρή κάμερα, ζωγραφισμένη από εμάς. */
@Composable
private fun CameraGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(CamInk, topLeft = Offset(w * 0.05f, h * 0.28f), size = Size(w * 0.9f, h * 0.6f), cornerRadius = CornerRadius(w * 0.12f))
        drawRoundRect(CamInk, topLeft = Offset(w * 0.32f, h * 0.16f), size = Size(w * 0.36f, h * 0.18f), cornerRadius = CornerRadius(w * 0.05f))
        drawCircle(CamYellow, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.58f))
        drawCircle(CamInk, radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.58f))
    }
}
