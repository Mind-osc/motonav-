package app.motonav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import app.motonav.ui.theme.Contrast
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.ThemeMode
import app.motonav.ui.theme.ThemeSettings
import app.motonav.ui.theme.Tokens
import app.motonav.ui.theme.Touch

/** S11 Ρυθμίσεις → Εμφάνιση. Οι αλλαγές φαίνονται αμέσως και αποθηκεύονται. */
@Composable
fun AppearanceScreen(
    settings: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
    onBack: () -> Unit,
) {
    var wheelOpen by remember { mutableStateOf(false) }
    if (wheelOpen) ColorWheelDialog(
        if (settings.customColor != 0) settings.customColor else null,
        onPick = { onChange(settings.copy(accentPreset = ThemeSettings.CUSTOM, customColor = it)); wheelOpen = false },
        onDismiss = { wheelOpen = false },
    )
    val c = LocalAppColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.base.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RideIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Πίσω στον χάρτη",
                    onClick = onBack,
                    size = Touch.min,
                )
                Spacer(Modifier.width(Space.lg))
                Text("Εμφάνιση", style = MotoType.street, color = c.base.text)
            }

            PreviewCard()

            Section("Φόντο") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    val modes = listOf(
                        ThemeMode.DARK to "Σκούρο",
                        ThemeMode.AMOLED to "Μαύρο",
                        ThemeMode.LIGHT to "Ανοιχτό",
                    )
                    modes.forEach { (mode, label) ->
                        TextChoice(
                            text = label,
                            selected = settings.mode == mode,
                            onClick = { onChange(settings.copy(mode = mode)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                TextChoice(
                    text = "Αυτόματο: μέρα ανοιχτό, νύχτα και τούνελ σκούρο",
                    selected = settings.mode == ThemeMode.AUTO,
                    onClick = { onChange(settings.copy(mode = ThemeMode.AUTO)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Το «Μαύρο» είναι καθαρό μαύρο: σε οθόνες OLED τρώει λιγότερη μπαταρία.",
                    style = MotoType.label,
                    color = c.base.textMuted,
                )
            }

            Section("Χρώμα") {
                val all = Tokens.accents.indices.toList() + ThemeSettings.CUSTOM
                all.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        row.forEach { index ->
                            val selected = settings.accentPreset == index
                            if (index == ThemeSettings.CUSTOM) {
                                CustomSwatch(selected) {
                                    onChange(settings.copy(accentPreset = ThemeSettings.CUSTOM))
                                    wheelOpen = true
                                }
                            } else {
                                val p = Tokens.accents[index]
                                val color = if (c.base.isDark) p.dark else p.light
                                ColorSwatch(
                                    color = color,
                                    selected = selected,
                                    description = p.name,
                                    onClick = { onChange(settings.copy(accentPreset = index)) },
                                ) {
                                    if (selected) Icon(Icons.Filled.Check, null, tint = Contrast.onColor(color))
                                }
                            }
                        }
                    }
                }
                val name = if (settings.accentPreset == ThemeSettings.CUSTOM) "Δικό μου"
                else Tokens.accents.getOrNull(settings.accentPreset)?.name ?: ""
                Text(name, style = MotoType.title, color = c.base.text)
            }

            if (settings.accentPreset == ThemeSettings.CUSTOM) {
                Section("Δικό μου χρώμα") {
                    // Όλο το φάσμα: απόχρωση, ένταση, φωτεινότητα (και άσπρο/μαύρο), όπως στο βελάκι.
                    Surface(
                        onClick = { wheelOpen = true },
                        shape = RoundedCornerShape(Radius.md),
                        color = c.base.surface2,
                        modifier = Modifier.fillMaxWidth().height(Touch.min),
                    ) {
                        Row(Modifier.padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).background(c.accent, RoundedCornerShape(50)))
                            Spacer(Modifier.width(Space.md))
                            Text("Διάλεξε από τον κύκλο χρωμάτων", style = MotoType.body, color = c.base.text)
                        }
                    }
                    if (c.accentAdjusted) {
                        Text(
                            "Το χρώμα φωτίστηκε λίγο για να φαίνεται και στον ήλιο.",
                            style = MotoType.label,
                            color = c.base.textMuted,
                        )
                    }
                }
            }

            Text(
                "Το κόκκινο για υπέρβαση ορίου και κινδύνους μένει πάντα κόκκινο.",
                style = MotoType.label,
                color = c.base.textMuted,
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(title, style = MotoType.title, color = c.base.text)
        content()
    }
}

/** Μικρογραφία: γραμμή διαδρομής, ταχύτητα, κουμπί, στα τρέχοντα χρώματα. */
@Composable
private fun PreviewCard() {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.bg,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, c.base.border),
    ) {
        Box {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 1.05f)
                    cubicTo(w * 0.2f, h * 0.55f, w * 0.65f, h * 0.75f, w * 0.6f, h * 0.4f)
                    cubicTo(w * 0.56f, h * 0.1f, w * 0.85f, h * 0.1f, w * 0.95f, -h * 0.05f)
                }
                drawLine(c.base.surface3, Offset(0f, h * 0.3f), Offset(w, h * 0.45f), strokeWidth = 18f)
                drawPath(path, c.base.bg, style = Stroke(width = 40f, cap = StrokeCap.Round))
                drawPath(path, c.accent, style = Stroke(width = 26f, cap = StrokeCap.Round))
            }
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = c.base.surface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Space.md),
            ) {
                Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("68", style = MotoType.maneuver, color = c.base.text)
                    Text("km/h", style = MotoType.label, color = c.base.textMuted)
                }
            }
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = c.accent,
                contentColor = c.onAccent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Space.md)
                    .size(Touch.min),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null) }
            }
        }
    }
}

@Composable
private fun CustomSwatch(selected: Boolean, onClick: () -> Unit) {
    val rainbow = Brush.sweepGradient(
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    )
    val c = LocalAppColors.current
    ColorSwatch(
        color = Color.Transparent,
        selected = selected,
        description = "Δικό μου χρώμα",
        onClick = onClick,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(rainbow),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(50), color = c.base.bg.copy(alpha = 0.7f)) {
                Icon(
                    if (selected) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = null,
                    tint = c.base.text,
                    modifier = Modifier.padding(4.dp).size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun HueSlider(hue: Float, onHue: (Float) -> Unit) {
    val c = LocalAppColors.current
    val rainbow = Brush.horizontalGradient(
        (0..6).map { Color.hsv(it * 60f % 360f, 0.6f, 0.97f) }
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(rainbow, RoundedCornerShape(Radius.sm))
        )
        Slider(
            value = hue,
            onValueChange = onHue,
            valueRange = 0f..359f,
            modifier = Modifier.height(Touch.min),
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.base.surface3,
            ),
        )
    }
}
