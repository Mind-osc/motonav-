package app.motonav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** AUTO: ανοιχτό τη μέρα, σκούρο τη νύχτα και στα τούνελ. */
enum class ThemeMode { DARK, AMOLED, LIGHT, AUTO }

data class BasePalette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val borderInput: Color,
    val text: Color,
    val textMuted: Color,
    val danger: Color,
    val onDanger: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val isDark: Boolean,
)

data class AccentPreset(val name: String, val dark: Color, val light: Color)

/** accentPreset: θέση στη λίστα Tokens.accents, ή CUSTOM για «Δικό μου». */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    val accentPreset: Int = 0,
    val customHue: Float = 270f,
    /** Δικό μου χρώμα από τον κύκλο (ARGB)· 0 = δεν έχει διαλεχτεί, χρησιμοποιείται το customHue. */
    val customColor: Int = 0,
) {
    companion object {
        const val CUSTOM = -1
    }
}

data class AppColors(
    val base: BasePalette,
    val accent: Color,
    val onAccent: Color,
    /** true όταν το «Δικό μου» χρώμα φωτίστηκε/σκούρυνε για να φαίνεται. */
    val accentAdjusted: Boolean,
    /** Λεπτό γκρι περίγραμμα για σκούρα κουμπιά και πάνελ, ώστε να ξεχωρίζουν πάνω στον χάρτη. */
    val outline: Color = if (base.isDark) Color(0xFF4A505A) else Color(0xFFC4C9D1),
)

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("MotoTheme λείπει") }

object Contrast {
    private val black = Color(0xFF0B0B10)
    private val white = Color.White

    fun ratio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** Μαύρο ή λευκό, ό,τι διαβάζεται καλύτερα πάνω στο χρώμα. */
    fun onColor(c: Color): Color = if (ratio(c, black) >= ratio(c, white)) black else white

    /**
     * Κρατάει την απόχρωση και αλλάζει φωτεινότητα ώσπου το χρώμα να έχει ≥ 3:1
     * πάνω στο φόντο και στις επιφάνειες (WCAG για στοιχεία UI).
     */
    fun fitAccent(raw: Color, base: BasePalette): Pair<Color, Boolean> {
        fun ok(c: Color) = ratio(c, base.bg) >= 3f && ratio(c, base.surface) >= 3f
        if (ok(raw)) return raw to false
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(raw.toArgb(), hsv)
        var c = raw
        repeat(60) {
            if (base.isDark) {
                hsv[2] = (hsv[2] + 0.02f).coerceAtMost(1f)
                if (hsv[2] >= 1f) hsv[1] = (hsv[1] - 0.03f).coerceAtLeast(0f)
            } else {
                hsv[2] = (hsv[2] - 0.02f).coerceAtLeast(0f)
            }
            c = Color(android.graphics.Color.HSVToColor(hsv))
            if (ok(c)) return c to true
        }
        return c to true
    }
}

fun resolveColors(settings: ThemeSettings, autoDark: Boolean = true): AppColors {
    val base = when (settings.mode) {
        ThemeMode.DARK -> Tokens.dark
        ThemeMode.AMOLED -> Tokens.amoled
        ThemeMode.LIGHT -> Tokens.light
        ThemeMode.AUTO -> if (autoDark) Tokens.dark else Tokens.light
    }
    val raw = if (settings.accentPreset == ThemeSettings.CUSTOM ||
        settings.accentPreset !in Tokens.accents.indices
    ) {
        if (settings.customColor != 0) Color(settings.customColor)
        else Color.hsv(settings.customHue.coerceIn(0f, 359.9f), 0.6f, if (base.isDark) 0.97f else 0.7f)
    } else {
        val p = Tokens.accents[settings.accentPreset]
        if (base.isDark) p.dark else p.light
    }
    val (accent, adjusted) = Contrast.fitAccent(raw, base)
    return AppColors(base, accent, Contrast.onColor(accent), adjusted)
}

@Composable
fun MotoTheme(settings: ThemeSettings, autoDark: Boolean = true, content: @Composable () -> Unit) {
    val colors = remember(settings, autoDark) { resolveColors(settings, autoDark) }
    val b = colors.base
    val scheme = if (b.isDark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = b.bg, onBackground = b.text,
            surface = b.surface, onSurface = b.text,
            surfaceVariant = b.surface2, onSurfaceVariant = b.textMuted,
            outline = b.borderInput, error = b.danger, onError = b.onDanger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = b.bg, onBackground = b.text,
            surface = b.surface, onSurface = b.text,
            surfaceVariant = b.surface2, onSurfaceVariant = b.textMuted,
            outline = b.borderInput, error = b.danger, onError = b.onDanger,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = MotoType.material) {
        CompositionLocalProvider(LocalAppColors provides colors) { content() }
    }
}
