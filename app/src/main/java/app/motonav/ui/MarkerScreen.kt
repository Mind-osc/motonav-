package app.motonav.ui

import android.graphics.Color as AColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.Touch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Χρώματα για βελάκι και γραμμή. null = του θέματος. */
private val PALETTE: List<Pair<Int?, String>> = listOf(
    null to "Θέμα",
    0xFFFF2D2D.toInt() to "Κόκκινο",
    0xFFFF8A1F.toInt() to "Πορτοκαλί",
    0xFFFFD21F.toInt() to "Κίτρινο",
    0xFF2ED573.toInt() to "Πράσινο",
    0xFF1FD1F9.toInt() to "Γαλάζιο",
    0xFF3B82F6.toInt() to "Μπλε",
    0xFF8B5CF6.toInt() to "Μωβ",
    0xFFFF4FA3.toInt() to "Ροζ",
    0xFFFFFFFF.toInt() to "Λευκό",
    0xFF111111.toInt() to "Μαύρο",
)

/** Ρυθμίσεις → Βελάκι και γραμμή: σχήμα, μέγεθος, χρώματα, περίγραμμα, με ζωντανή προεπισκόπηση. */
@Composable
fun MarkerScreen(prefs: MarkerPrefs, onChange: (MarkerPrefs) -> Unit, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customVersion by remember { mutableIntStateOf(0) }
    var pickError by remember { mutableStateOf<String?>(null) }

    // Δική σου εικόνα (PNG με διάφανο φόντο, μύτη προς τα πάνω): αντιγράφεται μέσα στην εφαρμογή.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = android.graphics.BitmapFactory.decodeStream(input) ?: return@use false
                        // έως 512 px, φτάνει για κάθε μέγεθος
                        val s = 512f / maxOf(bmp.width, bmp.height)
                        val scaled = if (s < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
                        PuckArt.customFile(context).outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        true
                    } ?: false
                }.getOrDefault(false)
            }
            if (ok) {
                customVersion++
                pickError = null
                onChange(prefs.copy(shape = PuckShape.CUSTOM))
            } else {
                pickError = "Δεν άνοιξε η εικόνα. Δοκίμασε PNG ή JPG."
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.base.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Space.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RideIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", onBack, size = Touch.min)
                Spacer(Modifier.width(Space.md))
                Text("Βελάκι και γραμμή", style = MotoType.title, color = c.base.text)
            }

            Preview(prefs, customVersion)

            Text("Σχήμα", style = MotoType.title, color = c.base.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PuckShape.entries.forEach { shape ->
                    ShapeChoice(prefs.copy(shape = shape, sizeDp = 48), shape.label, prefs.shape == shape, customVersion) {
                        if (shape == PuckShape.CUSTOM && !PuckArt.customFile(context).exists()) pickImage.launch("image/*")
                        else onChange(prefs.copy(shape = shape))
                    }
                }
            }
            TextChoice("Διάλεξε εικόνα από το κινητό…", selected = false, onClick = { pickImage.launch("image/*") }, modifier = Modifier.fillMaxWidth())
            Text("PNG με διάφανο φόντο, με τη μύτη προς τα πάνω.", style = MotoType.label, color = c.base.textMuted)
            pickError?.let { Text(it, style = MotoType.label, color = c.base.warning) }

            Text("Μέγεθος: ${prefs.sizeDp}", style = MotoType.title, color = c.base.text)
            Slider(
                value = prefs.sizeDp.toFloat(),
                onValueChange = { onChange(prefs.copy(sizeDp = it.toInt())) },
                valueRange = 40f..130f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.base.surface3),
            )

            val isImage = prefs.shape == PuckShape.RED_IMAGE || prefs.shape == PuckShape.CUSTOM
            if (!isImage) {
                Text("Χρώμα βελακιού", style = MotoType.title, color = c.base.text)
                Swatches(prefs.fill) { onChange(prefs.copy(fill = it)) }

                ToggleLine("Περίγραμμα βελακιού", prefs.outline) { onChange(prefs.copy(outline = it)) }
                if (prefs.outline) {
                    Swatches(prefs.outlineColor, allowTheme = false) { onChange(prefs.copy(outlineColor = it ?: AColor.WHITE)) }
                }
            }

            Text("Χρώμα γραμμής διαδρομής", style = MotoType.title, color = c.base.text)
            Swatches(prefs.lineColor) { onChange(prefs.copy(lineColor = it)) }

            Text(
                if (prefs.lineOutlineDp <= 0f) "Περίγραμμα γραμμής: χωρίς" else "Περίγραμμα γραμμής: ${"%.1f".format(prefs.lineOutlineDp)}",
                style = MotoType.title,
                color = c.base.text,
            )
            Slider(
                value = prefs.lineOutlineDp,
                onValueChange = { onChange(prefs.copy(lineOutlineDp = (it * 2).toInt() / 2f)) },
                valueRange = 0f..6f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.base.surface3),
            )
            if (prefs.lineOutlineDp > 0f) {
                Text("Χρώμα περιγράμματος (Θέμα = το φόντο του χάρτη)", style = MotoType.label, color = c.base.textMuted)
                Swatches(prefs.lineOutlineColor) { onChange(prefs.copy(lineOutlineColor = it)) }
            }

            TextChoice("Επαναφορά στα αρχικά", selected = false, onClick = { onChange(MarkerPrefs()) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Δείγμα: κομμάτι διαδρομής με το βελάκι πάνω, σε σκούρο χάρτη. */
@Composable
private fun Preview(prefs: MarkerPrefs, version: Int) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val bmp = remember(prefs, c.accent, version) { PuckArt.bitmap(context, prefs, c.accent.toArgb()).asImageBitmap() }
    val line = Color(prefs.lineColor ?: c.accent.toArgb())
    val edge = Color(prefs.lineOutlineColor ?: c.base.bg.toArgb())
    Surface(shape = RoundedCornerShape(Radius.lg), color = Color(0xFF14161B), border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                // λίγοι δρόμοι για φόντο
                listOf(0.25f, 0.7f).forEach { y ->
                    drawLine(Color(0xFF3A3F48), Offset(0f, size.height * y), Offset(size.width, size.height * (y - 0.15f)), strokeWidth = 6 * density)
                }
                val path = Path().apply {
                    moveTo(size.width * 0.5f, size.height + 20f)
                    lineTo(size.width * 0.5f, size.height * 0.55f)
                    cubicTo(size.width * 0.5f, size.height * 0.3f, size.width * 0.7f, size.height * 0.25f, size.width * 0.8f, -20f)
                }
                val w = 18 * density
                if (prefs.lineOutlineDp > 0f) drawPath(path, edge, style = Stroke(width = w + 2 * prefs.lineOutlineDp * density, cap = StrokeCap.Round))
                drawPath(path, line, style = Stroke(width = w, cap = StrokeCap.Round))
            }
            Image(bmp, contentDescription = "Προεπισκόπηση βελακιού", modifier = Modifier.size((bmp.width / density).dp).padding(top = 40.dp))
        }
    }
}

@Composable
private fun ShapeChoice(sample: MarkerPrefs, label: String, selected: Boolean, version: Int, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val bmp = remember(sample, c.accent, version) { PuckArt.bitmap(context, sample, c.accent.toArgb()).asImageBitmap() }
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) c.accent.copy(alpha = 0.18f) else c.base.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline),
        onClick = onClick,
        modifier = Modifier.width(92.dp),
    ) {
        Column(Modifier.padding(Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).background(Color(0xFF14161B), RoundedCornerShape(Radius.sm)), contentAlignment = Alignment.Center) {
                Image(bmp, contentDescription = null, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MotoType.label, color = c.base.text, maxLines = 2)
        }
    }
}

@Composable
private fun Swatches(selected: Int?, allowTheme: Boolean = true, onPick: (Int?) -> Unit) {
    val c = LocalAppColors.current
    var wheel by remember { mutableStateOf(false) }
    // Το χρώμα δεν είναι κανένα από τα έτοιμα: το δείχνει ο κύκλος.
    val custom = selected != null && PALETTE.none { it.first == selected }
    if (wheel) ColorWheelDialog(selected, onPick = { onPick(it); wheel = false }, onDismiss = { wheel = false })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        WheelSwatch(custom, selected) { wheel = true }
        PALETTE.filter { allowTheme || it.first != null }.forEach { (color, name) ->
            val shown = Color(color ?: c.accent.toArgb())
            val on = selected == color
            ColorSwatch(shown, on, name, onClick = { onPick(color) }) {
                if (color == null) Text("Θ", style = MotoType.label, color = c.onAccent)
                else if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = if (color == 0xFFFFFFFF.toInt() || color == 0xFFFFD21F.toInt()) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MotoType.title, color = c.base.text, modifier = Modifier.weight(1f))
        TextChoice(if (checked) "Ναι" else "Όχι", selected = checked, onClick = { onChange(!checked) })
    }
}
