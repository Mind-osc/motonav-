package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motonav.data.DisplayPrefs
import app.motonav.net.OsmNotes
import app.motonav.routing.RoutePoi
import app.motonav.routing.RoutePoiKind
import app.motonav.trips.TripMeter
import app.motonav.trips.TripMeterData
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val ORANGE = Color(0xFFF5A623)
private val BLUE = Color(0xFF3D9BFF)

/** «12,4 km» / «850 m». */
private fun km(m: Double): String =
    if (m < 1000) "${m.toInt()} m" else String.format(Locale("el", "GR"), "%.1f km", m / 1000)

private fun hms(s: Double): String {
    val t = s.toLong().coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
}

/**
 * Μετρητής ταξιδιού A/B (στυλ ταμπλό): ετικέτες αριστερά με μικρά γκρι κεφαλαία, τιμές δεξιά με έντονα νούμερα,
 * λεπτές γραμμές ανάμεσα, και από κάτω τρία στρογγυλά κουμπιά: παύση, μηδενισμός, A↔B.
 * Σειρά: απόσταση → χρόνοι (συνολικός = ταξιδιού + στάσης) → ταχύτητες → υψομετρικά.
 */
@Composable
fun TripMeterCard(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val useB = TripMeter.showB
    val m: TripMeterData = if (useB) TripMeter.b else TripMeter.a
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(if (useB) "Trip B" else "Trip A", style = MotoType.title.copy(fontWeight = FontWeight.Bold), color = c.base.text)
            Text(
                if (useB) "μηδενίζεται μόνο από σένα" else "η τωρινή διαδρομή · ξεκινά μόνο του με το «Ξεκίνα»",
                style = MotoType.label.copy(fontWeight = FontWeight.Normal), color = c.base.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (m.paused) Text("ΠΑΥΣΗ", style = MotoType.label, color = ORANGE)
        }
        Surface(shape = RoundedCornerShape(Radius.lg), color = c.base.surface2) {
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                val rows = listOf(
                    "ΑΠΟΣΤΑΣΗ" to String.format(Locale("el", "GR"), "%.2f km", m.distanceM / 1000),
                    "ΣΥΝΟΛΙΚΟΣ ΧΡΟΝΟΣ" to hms(m.totalS(now)),
                    "ΧΡΟΝΟΣ ΤΑΞΙΔΙΟΥ" to hms(m.movingS),
                    "ΔΙΑΡΚΕΙΑ ΣΤΑΣΗΣ" to hms(m.stoppedS(now)),
                    "ΜΕΣΗ ΚΙΝΗΣΗΣ" to "${m.avgMovingKmh.toInt()} km/h",
                    "ΣΥΝΟΛΙΚΗ ΜΕΣΗ" to "${m.avgTotalKmh(now).toInt()} km/h",
                    "ΜΕΓ. ΤΑΧΥΤΗΤΑ" to "${m.maxKmh.toInt()} km/h",
                    "ΑΝΟΔΟΣ" to "${m.ascentM.toInt()} m",
                    "ΚΑΤΑΒΑΣΗ" to "${m.descentM.toInt()} m",
                )
                rows.forEachIndexed { i, (label, value) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MotoType.label.copy(letterSpacing = 1.5.sp), color = c.base.textMuted, modifier = Modifier.weight(1f))
                        Text(value, style = MotoType.title.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"), color = c.base.text)
                    }
                    if (i < rows.lastIndex) HorizontalDivider(color = c.outline.copy(alpha = 0.5f), thickness = 1.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(if (m.paused) Glyph.PLAY else Glyph.PAUSE, if (m.paused) "Συνέχεια" else "Παύση", ORANGE) { TripMeter.togglePause() }
            RoundAction(Glyph.RESET, "Μηδενισμός", BLUE) { TripMeter.reset() }
            RoundAction(Glyph.SWAP, if (useB) "Trip A" else "Trip B", BLUE) { TripMeter.swap() }
        }
    }
}

private enum class Glyph { PAUSE, PLAY, RESET, SWAP, DOT }

/** Εικονίδια ζωγραφισμένα με το ίδιο πάχος και μέγεθος (όλα ίδια οπτικά, κανένα «τεράστιο»). */
@Composable
private fun GlyphIcon(g: Glyph, color: Color, size: androidx.compose.ui.unit.Dp = 24.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.11f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (g) {
            Glyph.PAUSE -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.36f, w * 0.22f), androidx.compose.ui.geometry.Offset(w * 0.36f, w * 0.78f), sw * 1.3f, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.64f, w * 0.22f), androidx.compose.ui.geometry.Offset(w * 0.64f, w * 0.78f), sw * 1.3f, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            Glyph.PLAY -> {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.32f, w * 0.2f); lineTo(w * 0.8f, w * 0.5f); lineTo(w * 0.32f, w * 0.8f); close() }
                drawPath(p, color, style = stroke)
            }
            Glyph.RESET -> {
                drawArc(color, 40f, 290f, false, androidx.compose.ui.geometry.Offset(w * 0.2f, w * 0.2f), androidx.compose.ui.geometry.Size(w * 0.6f, w * 0.6f), style = stroke)
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.80f, w * 0.18f); lineTo(w * 0.80f, w * 0.42f); lineTo(w * 0.56f, w * 0.42f) }
                drawPath(p, color, style = stroke)
            }
            Glyph.SWAP -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.2f, w * 0.36f), androidx.compose.ui.geometry.Offset(w * 0.8f, w * 0.36f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.64f, w * 0.2f); lineTo(w * 0.8f, w * 0.36f); lineTo(w * 0.64f, w * 0.52f) }, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.8f, w * 0.66f), androidx.compose.ui.geometry.Offset(w * 0.2f, w * 0.66f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.36f, w * 0.5f); lineTo(w * 0.2f, w * 0.66f); lineTo(w * 0.36f, w * 0.82f) }, color, style = stroke)
            }
            Glyph.DOT -> drawCircle(color, w * 0.3f)
        }
    }
}

@Composable
private fun RoundAction(glyph: Glyph, label: String, color: Color, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Surface(
            shape = CircleShape,
            color = c.base.surface2,
            border = BorderStroke(1.dp, c.outline),
            onClick = onClick,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { GlyphIcon(glyph, color, 26.dp) }
        }
        Text(label, style = MotoType.label.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp), color = c.base.textMuted)
    }
}

/** Μικρό, ήρεμο πλακίδιο: ουδέτερο φόντο, μόνο το εικονίδιο έχει χρώμα. */
@Composable
private fun ActionTile(icon: @Composable () -> Unit, title: String, subtitle: String?, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = c.base.surface2,
        border = BorderStroke(1.dp, c.outline.copy(alpha = 0.6f)),
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp),
    ) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tint) { icon() }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MotoType.label.copy(fontWeight = FontWeight.SemiBold), color = c.base.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MotoType.label.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp), color = c.base.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MIcon(v: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) =
    androidx.compose.material3.Icon(v, null, tint = tint, modifier = Modifier.size(20.dp))

@Composable
private fun SectionTitle(text: String) {
    val c = LocalAppColors.current
    Text(text.uppercase(Locale("el", "GR")), style = MotoType.label.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Normal), color = c.base.textMuted, modifier = Modifier.padding(top = Space.sm))
}

/**
 * «Γρήγορες ενέργειες»: πλαϊνή στήλη στο οριζόντιο (ο χάρτης μένει ορατός), από κάτω στο όρθιο.
 * Πάνω ο μετρητής ταξιδιού, μετά πλακίδια, μετά οι διακόπτες που αλλάζεις στον δρόμο.
 */
@Composable
fun QuickActionsPanel(
    landscape: Boolean,
    display: DisplayPrefs,
    onDisplayChange: (DisplayPrefs) -> Unit,
    recording: Boolean,
    recordingPaused: Boolean,
    muted: Boolean,
    onRecord: () -> Unit,
    onMute: () -> Unit,
    onHazard: () -> Unit,
    onMapReport: () -> Unit,
    onSaveHere: () -> Unit,
    onClose: () -> Unit,
    themeMode: app.motonav.ui.theme.ThemeMode = app.motonav.ui.theme.ThemeMode.DARK,
    onTheme: (app.motonav.ui.theme.ThemeMode) -> Unit = {},
    onToggleNorth: () -> Unit = {},
) {
    val c = LocalAppColors.current
    val backdrop = if (landscape) Modifier else Modifier
        .background(Color.Black.copy(alpha = 0.35f))
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose)
    Box(Modifier.fillMaxSize().then(backdrop)) {
        Surface(
            shape = if (landscape) RoundedCornerShape(Radius.lg) else RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
            color = c.base.surface,
            border = BorderStroke(1.dp, c.outline),
            modifier = if (landscape) {
                Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(Space.sm).width(400.dp).fillMaxHeight()
            } else {
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 720.dp).safeDrawingPadding()
            },
        ) {
            Column {
                // Επικεφαλίδα: πίσω, τίτλος
                Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Surface(shape = CircleShape, color = c.base.surface2, onClick = onClose, modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 34.sp, color = c.base.text, fontWeight = FontWeight.Bold) }
                    }
                    Text("Γρήγορες ενέργειες", style = MotoType.title.copy(fontWeight = FontWeight.Bold), color = c.base.text)
                }
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    TripMeterCard()
                    SectionTitle("Ενέργειες")
                    val red = Color(0xFFE5534B); val green = Color(0xFF4CAF50); val amber = Color(0xFFE0A23A); val orange = Color(0xFFEF7D32)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ActionTile({ GlyphIcon(if (recording && !recordingPaused) Glyph.PAUSE else Glyph.DOT, red, 18.dp) }, "Καταγραφή",
                            when { !recording -> "Έναρξη"; recordingPaused -> "Σε παύση"; else -> "Γράφει…" }, red, Modifier.weight(1f), onRecord)
                        ActionTile({ MIcon(androidx.compose.material.icons.Icons.Filled.Place, green) }, "Αποθήκευση θέσης", "Στα Αγαπημένα", green, Modifier.weight(1f), onSaveHere)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ActionTile({ MIcon(androidx.compose.material.icons.Icons.Filled.Edit, amber) }, "Αναφορά χάρτη", "Λείπει δρόμος…", amber, Modifier.weight(1f), onMapReport)
                        ActionTile({ MIcon(androidx.compose.material.icons.Icons.Filled.Warning, orange) }, "Κίνδυνος / κάμερα", "Για όλους", orange, Modifier.weight(1f), onHazard)
                    }
                    SectionTitle("Θέμα")
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        listOf(app.motonav.ui.theme.ThemeMode.DARK to "Σκούρο", app.motonav.ui.theme.ThemeMode.LIGHT to "Φωτεινό", app.motonav.ui.theme.ThemeMode.AUTO to "Αυτόματο").forEach { (mode, label) ->
                            TextChoice(label, themeMode == mode || (mode == app.motonav.ui.theme.ThemeMode.DARK && themeMode == app.motonav.ui.theme.ThemeMode.AMOLED), { onTheme(mode) }, Modifier.weight(1f))
                        }
                    }
                    SectionTitle("Γρήγοροι διακόπτες")
                    Surface(shape = RoundedCornerShape(Radius.lg), color = c.base.surface) {
                        Column {
                            SwitchRow("Φωνή πλοήγησης", null, !muted) { onMute() }
                            SwitchRow("Φωνή για κάμερες", null, display.cameraVoice) { onDisplayChange(display.copy(cameraVoice = it)) }
                            SwitchRow("Βορράς πάνω", "Αλλιώς η κατεύθυνσή σου πάνω", CameraPrefs.northUp) { if (it != CameraPrefs.northUp) onToggleNorth() }
                            SwitchRow("Λιγότερη κλίση χάρτη", "Στην πλοήγηση: 40° αντί για 56°", display.navTilt < 50) { onDisplayChange(display.copy(navTilt = if (it) 40 else 56)) }
                            SwitchRow("Χρώμα κλίσης στη γραμμή", "Κίτρινο 4–7 %, πορτοκαλί 7–10 %, κόκκινο > 10 %", display.slopeColors) {
                                onDisplayChange(display.copy(slopeColors = it))
                            }
                            SwitchRow("Φωτισμός όταν περνάς το όριο", null, display.overspeedGlow) { onDisplayChange(display.copy(overspeedGlow = it)) }
                            SwitchRow("Μέρη για μηχανόβιους", null, display.showSpots) { onDisplayChange(display.copy(showSpots = it)) }
                            SwitchRow("Ανάγλυφο βουνών", null, display.showRelief) { onDisplayChange(display.copy(showRelief = it)) }
                            SwitchRow("Εξοικονόμηση ενέργειας", null, display.powerSave) { onDisplayChange(display.copy(powerSave = it)) }
                            SwitchRow("Αυτοκίνητο", "Διαδρομές για αυτοκίνητο", display.car) { onDisplayChange(display.copy(car = it)) }
                        }
                    }
                    SectionTitle("Επόμενα στη διαδρομή")
                    Text("Τι να δείχνει στην πλοήγηση, με την απόσταση ως το επόμενο (και χωρίς internet).", style = MotoType.label.copy(fontWeight = FontWeight.Normal), color = c.base.textMuted)
                    RoutePoiKind.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            row.forEach { k ->
                                val on = k.name in display.nextPois
                                TextChoice("${k.emoji} ${k.label}", on, {
                                    onDisplayChange(display.copy(nextPois = if (on) display.nextPois - k.name else display.nextPois + k.name))
                                }, Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.heightIn(min = Space.lg))
                }
            }
        }
    }
}

/** Στην πλοήγηση: «⛽ 12 km · ☕ 3,4 km …». Πάτημα: στάση εκεί. */
@Composable
fun NextPoisBar(next: List<RoutePoi>, progressM: Double, onPick: (RoutePoi) -> Unit, modifier: Modifier = Modifier) {
    if (next.isEmpty()) return
    val c = LocalAppColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        next.forEach { p ->
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = c.base.surface,
                border = BorderStroke(1.dp, c.outline),
                onClick = { onPick(p) },
            ) {
                Row(Modifier.heightIn(min = 40.dp).padding(horizontal = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.kind.emoji, fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(km(p.alongM - progressM), style = MotoType.label.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = c.base.text)
                }
            }
        }
    }
}

/** «Αναφορά χάρτη»: σημείωση στο OpenStreetMap εκεί που είσαι. */
@Composable
fun MapReportSheet(lat: Double?, lon: Double?, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf<OsmNotes.Kind?>(null) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
            color = c.base.surface,
            border = BorderStroke(1.dp, c.outline),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Αναφορά χάρτη", style = MotoType.title.copy(fontWeight = FontWeight.Bold), color = c.base.text)
                Text(
                    "Πάει ως σημείωση στο OpenStreetMap, στο σημείο που είσαι. Οι εθελοντές της περιοχής διορθώνουν τον χάρτη, και η διόρθωση έρχεται και στο MotoNav.",
                    style = MotoType.label.copy(fontWeight = FontWeight.Normal), color = c.base.textMuted,
                )
                if (lat == null || lon == null) {
                    Text("Χρειάζεται στίγμα GPS.", style = MotoType.body, color = c.base.text)
                } else {
                    OsmNotes.Kind.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            row.forEach { k -> TextChoice(k.label, kind == k, { kind = k }, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(500) },
                        label = { Text("Λεπτομέρειες (π.χ. όνομα δρόμου), προαιρετικά") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    TextChoice("Άκυρο", false, onClose, Modifier.weight(1f))
                    TextChoice(if (busy) "Αποστολή…" else "Αποστολή", kind != null && lat != null, {
                        val k = kind ?: return@TextChoice
                        if (busy || lat == null || lon == null) return@TextChoice
                        busy = true
                        scope.launch {
                            val r = runCatching { OsmNotes.create(lat, lon, k, text) }
                            busy = false
                            val msg = if (r.isSuccess) "Ευχαριστούμε! Η σημείωση στάλθηκε στο OpenStreetMap." else "Δεν στάλθηκε (χρειάζεται internet). Δοκίμασε ξανά."
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            if (r.isSuccess) onClose()
                        }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}
