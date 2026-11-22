package app.motonav.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import app.motonav.R
import java.io.File

/** Σχήματα για το βελάκι θέσης. Όλα δείχνουν προς τα πάνω (το MapLibre τα γυρίζει με την κατεύθυνση). */
enum class PuckShape(val label: String) {
    CLASSIC("Κλασικό"),
    GLOSSY("Γυαλιστερό"),
    SHARP("Μυτερό"),
    CIRCLE("Κύκλος"),
    CHEVRON("Σιρίτι"),
    RED_IMAGE("Κόκκινο (εικόνα)"),
    CUSTOM("Δική σου εικόνα"),
}

/** Τι διαλέγεις για το βελάκι και τη γραμμή. Χρώμα null = το χρώμα του θέματος. */
data class MarkerPrefs(
    val shape: PuckShape = PuckShape.CLASSIC,
    val sizeDp: Int = 84,
    val fill: Int? = null,
    val outline: Boolean = true,
    val outlineColor: Int = Color.WHITE,
    val lineColor: Int? = null,
    val lineOutlineColor: Int? = null,
    /** Πάχος περιγράμματος της γραμμής σε dp, σε κάθε πλευρά (0 = χωρίς). */
    val lineOutlineDp: Float = 2f,
)

object PuckArt {
    /** Η δική σου εικόνα, αντιγραμμένη μέσα στην εφαρμογή. */
    fun customFile(context: Context) = File(context.filesDir, "puck_custom.png")

    private fun darker(c: Int, f: Float) = Color.rgb((Color.red(c) * (1 - f)).toInt(), (Color.green(c) * (1 - f)).toInt(), (Color.blue(c) * (1 - f)).toInt())
    private fun lighter(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt(),
    )

    /** Σχήμα σε κουτί 0..100, μύτη πάνω. */
    private fun shapePath(shape: PuckShape): Path = Path().apply {
        when (shape) {
            PuckShape.GLOSSY -> {
                // φαρδύ βέλος με καμπύλη εγκοπή, σαν την εικόνα που έστειλες
                moveTo(50f, 6f); lineTo(94f, 90f)
                quadTo(72f, 74f, 50f, 74f); quadTo(28f, 74f, 6f, 90f); close()
            }
            PuckShape.SHARP -> {
                moveTo(50f, 4f); lineTo(84f, 94f); lineTo(50f, 76f); lineTo(16f, 94f); close()
            }
            PuckShape.CHEVRON -> {
                moveTo(50f, 10f); lineTo(92f, 70f); lineTo(76f, 84f); lineTo(50f, 48f)
                lineTo(24f, 84f); lineTo(8f, 70f); close()
            }
            else -> {
                // κλασικό: ίδιο με το puck_arrow.xml (viewport 26 → 100)
                val k = 100f / 26f
                moveTo(13f * k, 2.5f * k); lineTo(21.5f * k, 22.5f * k)
                quadTo(21.8f * k, 23.4f * k, 20.9f * k, 23f * k); lineTo(13f * k, 18.8f * k)
                lineTo(5.1f * k, 23f * k); quadTo(4.2f * k, 23.4f * k, 4.5f * k, 22.5f * k); close()
            }
        }
    }

    /**
     * @param stretchY Ο χάρτης «ξαπλώνει» το βελάκι πάνω στο έδαφος όταν η κάμερα γέρνει (πλοήγηση),
     * οπότε φαίνεται πατημένο. Το τεντώνουμε κατακόρυφα κατά 1/cos(κλίση) για να φαίνεται όπως στην προεπισκόπηση.
     */
    fun bitmap(context: Context, p: MarkerPrefs, accent: Int, stretchY: Float = 1f): Bitmap {
        val d = context.resources.displayMetrics.density
        val px = (p.sizeDp.coerceIn(30, 140) * d).toInt()
        val out = Bitmap.createBitmap(px, (px * stretchY).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.scale(1f, stretchY)
        val fill = p.fill ?: darker(accent, 0.4f)

        // Εικόνες: όπως είναι, μέσα στο τετράγωνο.
        if (p.shape == PuckShape.RED_IMAGE || p.shape == PuckShape.CUSTOM) {
            val src = if (p.shape == PuckShape.RED_IMAGE) {
                BitmapFactory.decodeResource(context.resources, R.drawable.puck_user_red)
            } else {
                customFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            }
            if (src != null) {
                val scale = px.toFloat() / maxOf(src.width, src.height)
                val w = src.width * scale
                val h = src.height * scale
                cv.drawBitmap(src, null, RectF((px - w) / 2, (px - h) / 2, (px + w) / 2, (px + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                return out
            }
        }

        val k = px / 100f
        cv.save()
        cv.scale(k, k)
        val strokeOuter = 9f * d / k
        val strokeInner = 5.5f * d / k
        // λίγο μικρότερο, για να χωράει το περίγραμμα
        val inset = if (p.outline) 0.84f else 0.94f
        cv.scale(inset, inset, 50f, 50f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND }

        if (p.shape == PuckShape.CIRCLE) {
            if (p.outline) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(200, 0, 0, 0); cv.drawCircle(50f, 50f, 46f + strokeOuter / 4, paint)
                paint.color = p.outlineColor; cv.drawCircle(50f, 50f, 46f, paint)
            }
            paint.color = fill
            paint.shader = RadialGradient(42f, 40f, 44f, lighter(fill, 0.35f), fill, Shader.TileMode.CLAMP)
            cv.drawCircle(50f, 50f, if (p.outline) 39f else 46f, paint)
            paint.shader = null
            // μικρό βελάκι μέσα για την κατεύθυνση
            paint.color = if (p.outline) p.outlineColor else Color.WHITE
            cv.drawPath(Path().apply { moveTo(50f, 20f); lineTo(70f, 64f); lineTo(50f, 54f); lineTo(30f, 64f); close() }, paint)
            cv.restore()
            return out
        }

        val path = shapePath(p.shape)
        val rounded = if (p.shape == PuckShape.GLOSSY) CornerPathEffect(9f) else null
        if (p.outline) {
            paint.style = Paint.Style.STROKE
            paint.pathEffect = rounded
            paint.color = Color.argb(200, 0, 0, 0); paint.strokeWidth = strokeOuter; cv.drawPath(path, paint)
            paint.color = p.outlineColor; paint.strokeWidth = strokeInner; cv.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        paint.pathEffect = rounded
        paint.color = fill
        if (p.shape == PuckShape.GLOSSY) {
            // γυαλάδα: πιο ανοιχτό πάνω αριστερά, πιο σκούρο κάτω δεξιά
            paint.shader = LinearGradient(20f, 10f, 85f, 90f, lighter(fill, 0.25f), darker(fill, 0.25f), Shader.TileMode.CLAMP)
        }
        cv.drawPath(path, paint)
        paint.shader = null
        cv.restore()
        return out
    }
}
