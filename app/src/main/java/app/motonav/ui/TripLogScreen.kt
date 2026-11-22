package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.motonav.geo.LatLon
import app.motonav.trips.TrackPoint
import app.motonav.trips.TripRepo
import app.motonav.trips.TripSummary
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.Touch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

/** S09 Ημερολόγιο διαδρομών: λίστα και λεπτομέρεια. */
@Composable
fun TripLogScreen(onShowOnMap: (List<LatLon>) -> Unit, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val repo = remember { TripRepo(context) }
    var trips by remember { mutableStateOf<List<TripSummary>?>(null) }
    var selected by remember { mutableStateOf<TripSummary?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Επιλογή πολλών για διαγραφή.
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var confirmBulk by remember { mutableStateOf(false) }

    LaunchedEffect(reload) { trips = withContext(Dispatchers.IO) { repo.list() } }

    // Άνοιγμα αρχείου GPX από το κινητό: δείχνεται στον χάρτη, και από εκεί «Ακολούθησε».
    val scope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }
    val openGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val pts = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        ?.let { app.motonav.trips.Gpx.parse(it).map { p -> p.position } }
                }.getOrNull()
            }
            if (pts != null && pts.size >= 2) onShowOnMap(pts)
            else openError = "Δεν βρήκα διαδρομή μέσα στο αρχείο (θέλει GPX με trkpt ή rtept)."
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.base.bg)
    ) {
        val sel = selected
        if (sel != null) {
            TripDetail(sel, repo, onShowOnMap, onDeleted = { selected = null; reload++ }, onBack = { selected = null })
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Header("Ημερολόγιο διαδρομών", onBack)
                RecordingControls(app.motonav.car.CarBridge.phoneSession)
                TextChoice("Άνοιγμα αρχείου GPX", selected = false, onClick = { openError = null; openGpx.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth())
                openError?.let { Text(it, style = MotoType.label, color = c.base.warning) }
                val list = trips
                when {
                    list == null -> Text("Φορτώνω…", style = MotoType.body, color = c.base.textMuted)
                    list.isEmpty() -> Text(
                        "Δεν υπάρχουν ακόμα διαδρομές. Κάθε πλοήγηση καταγράφεται αυτόματα· ή πάτα «Ξεκίνα καταγραφή» για βόλτα χωρίς πλοήγηση.",
                        style = MotoType.body,
                        color = c.base.textMuted,
                    )
                    else -> {
                        val totalKm = list.sumOf { it.distanceM } / 1000
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text(
                                if (selecting) "${picked.size} επιλεγμένες" else "${list.size} διαδρομές · ${totalKm.roundToInt()} km συνολικά",
                                style = MotoType.label,
                                color = c.base.textMuted,
                                modifier = Modifier.weight(1f),
                            )
                            TextChoice(
                                if (selecting) "Άκυρο" else "Επιλογή",
                                selected = false,
                                onClick = { selecting = !selecting; picked = emptySet(); confirmBulk = false },
                            )
                        }
                        if (selecting) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                                val all = picked.size == list.size
                                TextChoice(
                                    if (all) "Καμία" else "Όλες",
                                    selected = all,
                                    onClick = { picked = if (all) emptySet() else list.map { it.id }.toSet(); confirmBulk = false },
                                    modifier = Modifier.weight(1f),
                                )
                                TextChoice(
                                    if (confirmBulk) "Σίγουρα; (${picked.size})" else "Διαγραφή (${picked.size})",
                                    selected = confirmBulk,
                                    onClick = {
                                        if (picked.isEmpty()) return@TextChoice
                                        if (!confirmBulk) { confirmBulk = true; return@TextChoice }
                                        val ids = picked
                                        scope.launch {
                                            withContext(Dispatchers.IO) { ids.forEach { repo.delete(it) } }
                                            picked = emptySet(); selecting = false; confirmBulk = false; reload++
                                        }
                                    },
                                    modifier = Modifier.weight(1.4f),
                                )
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            items(list, key = { it.id }) { t ->
                                TripRow(t, checked = if (selecting) t.id in picked else null) {
                                    if (selecting) {
                                        picked = if (t.id in picked) picked - t.id else picked + t.id
                                        confirmBulk = false
                                    } else selected = t
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        RideIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", onBack, size = Touch.min)
        Spacer(Modifier.width(Space.lg))
        Text(title, style = MotoType.street, color = c.base.text)
    }
}

private val dateFmt = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale("el", "GR"))

@Composable
private fun TripRow(t: TripSummary, checked: Boolean? = null, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = c.base.surface,
        border = BorderStroke(1.dp, c.outline),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (checked != null) {
                // Κουτάκι επιλογής: γεμάτος κύκλος με ✓ όταν είναι επιλεγμένη.
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (checked) c.accent else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                        .border(2.dp, if (checked) c.accent else c.base.textMuted, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) Text("✓", style = MotoType.label, color = c.onAccent)
                }
                Spacer(Modifier.width(Space.md))
            }
            Column {
                Text(dateFmt.format(Date(t.startMs)), style = MotoType.label, color = c.base.textMuted)
                Text(
                    "${km(t.distanceM)} · ${duration(t.movingS)} · max ${t.maxSpeedKmh.roundToInt()} km/h",
                    style = MotoType.body,
                    color = c.base.text,
                )
            }
        }
    }
}

@Composable
private fun TripDetail(
    t: TripSummary,
    repo: TripRepo,
    onShowOnMap: (List<LatLon>) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalAppColors.current
    var track by remember(t.id) { mutableStateOf<List<TrackPoint>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(t.id) { track = withContext(Dispatchers.IO) { repo.track(t.id) } }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Header(dateFmt.format(Date(t.startMs)), onBack)

        // Σχήμα της διαδρομής
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = c.base.surface,
            border = BorderStroke(1.dp, c.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            if (track.size >= 2) TrackSketch(track)
        }

        // Στατιστικά
        Surface(shape = RoundedCornerShape(Radius.lg), color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                StatRow("Απόσταση", km(t.distanceM), "Χρόνος σε κίνηση", duration(t.movingS))
                StatRow("Μέση ταχύτητα", "${t.avgMovingKmh.roundToInt()} km/h", "Μέγιστη ταχύτητα", "${t.maxSpeedKmh.roundToInt()} km/h")
                StatRow("Μέγιστη κλίση αριστερά", "${t.maxLeanLeft.roundToInt()}°", "Μέγιστη κλίση δεξιά", "${t.maxLeanRight.roundToInt()}°")
                StatRow(
                    "Υψόμετρο",
                    if (t.minAltM != null && t.maxAltM != null) "${t.minAltM.roundToInt()}–${t.maxAltM.roundToInt()} m" else "—",
                    "Ανάβαση",
                    "${t.ascentM.roundToInt()} m",
                )
            }
        }

        // Προφίλ υψομέτρου
        val ele = track.mapNotNull { it.ele }
        if (ele.size >= 2) {
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = c.base.surface,
                border = BorderStroke(1.dp, c.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
            ) { ElevationSparkline(ele) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
            TextChoice(
                if (confirmDelete) "Σίγουρα;" else "Διαγραφή",
                selected = false,
                onClick = {
                    if (confirmDelete) {
                        repo.delete(t.id)
                        onDeleted()
                    } else {
                        confirmDelete = true
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TextChoice(
                "Δείξε στον χάρτη",
                selected = true,
                onClick = { if (track.size >= 2) onShowOnMap(track.map { it.position }) },
                modifier = Modifier.weight(1.4f),
            )
        }
        val ctx = LocalContext.current
        TextChoice(
            "Κοινοποίηση GPX",
            selected = false,
            onClick = {
                runCatching {
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", repo.file(t.id))
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("application/gpx+xml")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(android.content.Intent.createChooser(send, "Κοινοποίηση διαδρομής"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Αρχείο GPX: Android/data/app.motonav/files/trips/${t.id}.gpx", style = MotoType.label, color = c.base.textMuted)
    }
}

@Composable
private fun StatRow(l1: String, v1: String, l2: String, v2: String) {
    Row(Modifier.fillMaxWidth()) {
        Stat2(l1, v1, Modifier.weight(1f))
        Stat2(l2, v2, Modifier.weight(1f))
    }
}

@Composable
private fun Stat2(label: String, value: String, modifier: Modifier) {
    val c = LocalAppColors.current
    Column(modifier) {
        Text(label, style = MotoType.label, color = c.base.textMuted)
        Text(value, style = MotoType.title, color = c.base.text)
    }
}

/** Το σχήμα της διαδρομής, χωρίς χάρτη από κάτω (ισομετρική προβολή, με αρχή και τέλος). */
@Composable
private fun TrackSketch(track: List<TrackPoint>) {
    val c = LocalAppColors.current
    Canvas(Modifier.fillMaxSize().padding(Space.lg)) {
        val lat0 = track.map { it.position.lat }.average()
        val k = cos(Math.toRadians(lat0))
        val xs = track.map { it.position.lon * k }
        val ys = track.map { it.position.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-9)
        val spanY = (maxY - minY).coerceAtLeast(1e-9)
        val scale = minOf(size.width / spanX, size.height / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun pt(i: Int) = Offset(
            (offX + (xs[i] - minX) * scale).toFloat(),
            (offY + (maxY - ys[i]) * scale).toFloat(),
        )
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until track.size) lineTo(pt(i).x, pt(i).y)
        }
        drawPath(path, c.accent, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.base.success, 9f, pt(0))
        drawCircle(c.base.danger, 9f, pt(track.lastIndex))
    }
}

@Composable
private fun ElevationSparkline(ele: List<Double>) {
    val c = LocalAppColors.current
    Canvas(Modifier.fillMaxSize().padding(Space.md)) {
        val min = ele.min()
        val max = ele.max()
        val span = (max - min).coerceAtLeast(1.0)
        val step = size.width / (ele.size - 1)
        val line = Path()
        val fill = Path()
        ele.forEachIndexed { i, e ->
            val x = i * step
            val y = (size.height - (e - min) / span * size.height).toFloat()
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, c.accent.copy(alpha = 0.18f))
        drawPath(line, c.accent, style = Stroke(width = 3f))
    }
}

private fun km(m: Double) = if (m < 1000) "${m.roundToInt()} m" else String.format(Locale("el", "GR"), "%.1f km", m / 1000)

private fun duration(s: Double): String {
    val min = (s / 60).roundToInt()
    return if (min < 60) "$min λεπτά" else "${min / 60} ώ ${min % 60} λ"
}
