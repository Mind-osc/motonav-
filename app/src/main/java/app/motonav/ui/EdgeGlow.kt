package app.motonav.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Τι ανάβει τις άκρες, με σειρά σπουδαιότητας. */
enum class EdgeAlert(val color: Color, val periodMs: Int, val topOnly: Boolean = false) {
    /** Πέρασες το όριο ταχύτητας: μόνο στο πάνω μέρος. */
    OVERSPEED(Color(0xFFFF2D2D), 550, topOnly = true),
    /** Πάνω από 120 km/h (με διακόπτη): καρό σημαία στο πάνω μέρος. */
    CHECKERED(Color.White, 700, topOnly = true),
    /** Κίνδυνος μπροστά (λάδια, ζώο, έργα…). */
    HAZARD(Color(0xFFFF8A1F), 800),
    /** Κάμερα μπροστά. */
    CAMERA(Color(0xFFFFD23F), 1000),
}

/**
 * Παλλόμενες άκρες οθόνης: φαίνονται με την άκρη του ματιού, χωρίς να κοιτάξεις την οθόνη.
 * Δεν πιάνει αγγίγματα (είναι μόνο ζωγραφιά).
 */
@Composable
fun EdgeGlow(alert: EdgeAlert?, modifier: Modifier = Modifier) {
    if (alert == null) return
    val t = rememberInfiniteTransition(label = "edge")
    val a by t.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(alert.periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "edge-alpha",
    )
    Canvas(modifier.fillMaxSize()) {
        val w = 22.dp.toPx()
        if (alert == EdgeAlert.CHECKERED) {
            // Άσπρα-μαύρα τετραγωνάκια, 3 σειρές, που σβήνουν προς τα κάτω.
            val sq = 8.dp.toPx()
            val cols = (size.width / sq).toInt() + 1
            for (row in 0 until 3) {
                val fade = a * (1f - row * 0.3f)
                for (col in 0 until cols) {
                    val white = (row + col) % 2 == 0
                    drawRect(
                        (if (white) Color.White else Color.Black).copy(alpha = fade),
                        Offset(col * sq, row * sq),
                        Size(sq, sq),
                    )
                }
            }
            return@Canvas
        }
        val col = alert.color.copy(alpha = a)
        val clear = Color.Transparent
        drawRect(Brush.verticalGradient(listOf(col, clear), 0f, w), Offset.Zero, Size(size.width, w))
        if (alert.topOnly) return@Canvas
        drawRect(Brush.verticalGradient(listOf(clear, col), size.height - w, size.height), Offset(0f, size.height - w), Size(size.width, w))
        drawRect(Brush.horizontalGradient(listOf(col, clear), 0f, w), Offset.Zero, Size(w, size.height))
        drawRect(Brush.horizontalGradient(listOf(clear, col), size.width - w, size.width), Offset(size.width - w, 0f), Size(w, size.height))
    }
}
