package app.motonav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.motonav.routing.Turn
import kotlin.math.cos
import kotlin.math.sin

/** Βέλος κίνησης, ζωγραφισμένο από εμάς (κανένα εικονίδιο τρίτων). */
@Composable
fun ManeuverIcon(turn: Turn, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f
        val cx = w / 2f
        when (turn) {
            Turn.ROUNDABOUT, Turn.ROUNDABOUT_LEFT -> {
                val r = w * 0.2f
                val c = Offset(cx, h * 0.42f)
                drawCircle(color, r, c, style = Stroke(stroke))
                drawLine(color, Offset(cx, h * 0.95f), Offset(cx, c.y + r), stroke, StrokeCap.Round)
                val sign = if (turn == Turn.ROUNDABOUT_LEFT) -1f else 1f
                arrow(color, Offset(cx + sign * r * 0.7f, c.y - r * 0.7f), 45.0 * sign, w * 0.5f, stroke)
            }
            Turn.ARRIVE -> {
                drawCircle(color, w * 0.3f, Offset(cx, h * 0.5f))
                drawCircle(Color.Black.copy(alpha = 0.25f), w * 0.12f, Offset(cx, h * 0.5f))
            }
            Turn.UTURN -> {
                val r = w * 0.17f
                val xRight = cx + r
                val xLeft = cx - r
                val top = h * 0.38f
                val path = Path().apply {
                    moveTo(xRight, h * 0.92f)
                    lineTo(xRight, top)
                    arcTo(Rect(cx - r, top - r, cx + r, top + r), 0f, -180f, false)
                    lineTo(xLeft, h * 0.6f)
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                arrow(color, Offset(xLeft, h * 0.6f), 180.0, w * 0.5f, stroke)
            }
            else -> {
                val deg = when (turn) {
                    Turn.STRAIGHT -> 0.0
                    Turn.SLIGHT_LEFT -> -45.0
                    Turn.LEFT -> -90.0
                    Turn.SHARP_LEFT -> -135.0
                    Turn.SLIGHT_RIGHT -> 45.0
                    Turn.RIGHT -> 90.0
                    Turn.SHARP_RIGHT -> 135.0
                    Turn.KEEP_LEFT, Turn.EXIT_LEFT -> -30.0
                    Turn.KEEP_RIGHT, Turn.EXIT_RIGHT -> 30.0
                    else -> 0.0
                }
                // Το σχήμα γεμίζει όλο το τετράγωνο από πάνω ως κάτω: η «καμπή» ανεβαίνει όσο πιο πλάγια είναι η στροφή,
                // και ο κορμός μετακινείται αντίθετα από τη στροφή για να χωράει η μύτη στο πλάτος.
                val sharp = turn == Turn.SHARP_LEFT || turn == Turn.SHARP_RIGHT
                val rad = Math.toRadians(deg)
                val head = when {
                    sharp -> w * 0.36f
                    kotlin.math.abs(deg) >= 80 -> w * 0.54f
                    else -> w * 0.48f
                }
                val len = h * 0.3f
                val absDeg = kotlin.math.abs(deg)
                val midY = when {
                    sharp -> h * 0.12f
                    absDeg >= 80 -> h * 0.05f + head * 0.38f
                    absDeg >= 40 -> h * 0.47f
                    absDeg >= 20 -> h * 0.52f
                    else -> h * 0.6f
                }
                val stemX = cx - (sin(rad) * w * 0.2f).toFloat()
                val mid = Offset(stemX, midY)
                drawLine(color, Offset(stemX, h * 0.95f), mid, stroke, StrokeCap.Round)
                val end = Offset(mid.x + (sin(rad) * len).toFloat(), mid.y - (cos(rad) * (if (absDeg < 20) h * 0.4f else len)).toFloat())
                drawLine(color, mid, end, stroke, StrokeCap.Round)
                arrow(color, end, deg, head, stroke)
            }
        }
    }
}

/** Τριγωνική μύτη στο `at`, προς την κατεύθυνση `deg` (0 = πάνω, θετικό = δεξιά). */
private fun DrawScope.arrow(color: Color, at: Offset, deg: Double, size: Float, stroke: Float) {
    val rad = Math.toRadians(deg)
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val px = -dy
    val py = dx
    val head = size * 0.45f
    val half = size * 0.38f
    val tip = Offset(at.x + dx * head * 0.75f, at.y + dy * head * 0.75f)
    val back = Offset(at.x - dx * head * 0.25f, at.y - dy * head * 0.25f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(back.x + px * half, back.y + py * half)
        lineTo(back.x - px * half, back.y - py * half)
        close()
    }
    drawPath(path, color)
    // στρογγυλεύει τη σύνδεση με τη γραμμή
    drawCircle(color, stroke / 2f, at)
}
