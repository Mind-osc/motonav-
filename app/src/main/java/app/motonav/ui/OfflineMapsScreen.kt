package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.motonav.map.MapBus
import app.motonav.map.OfflineMaps
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.Touch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ρυθμίσεις → Χάρτες χωρίς internet. */
@Composable
fun OfflineMapsScreen(onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { OfflineMaps.refresh(context, resume = true) }

    val view = remember { MapBus.visible }
    val areaTiles = view?.let { OfflineMaps.tileCount(it[0], it[1], it[2], it[3], OfflineMaps.AREA_MAX_ZOOM) }
    val greeceTiles = remember {
        val g = OfflineMaps.GREECE
        OfflineMaps.tileCount(g[0], g[1], g[2], g[3], OfflineMaps.GREECE_MAX_ZOOM)
    }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    fun n(v: Long) = String.format(Locale("el", "GR"), "%,d", v)

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
                Text("Χάρτες χωρίς internet", style = MotoType.title, color = c.base.text)
            }
            Text(
                "Κατέβασέ τους με Wi‑Fi. Μετά ο χάρτης φαίνεται και χωρίς σήμα. Η διαδρομή δουλεύει ήδη offline (BRouter). " +
                    "Η αναζήτηση, η κίνηση, ο καιρός, η σκίαση βουνών και οι ισοϋψείς θέλουν internet.",
                style = MotoType.label,
                color = c.base.textMuted,
            )

            TextChoice(
                "Όλη η Ελλάδα · κύριοι δρόμοι και πόλεις (${n(greeceTiles)} κομμάτια)",
                selected = false,
                onClick = { if (!OfflineMaps.busy) OfflineMaps.download(context, "Ελλάδα (κύριοι δρόμοι)", OfflineMaps.GREECE, OfflineMaps.GREECE_MAX_ZOOM) },
                modifier = Modifier.fillMaxWidth(),
            )
            val tooBig = areaTiles == null || areaTiles > OfflineMaps.AREA_MAX_TILES
            TextChoice(
                when {
                    areaTiles == null -> "Η περιοχή που βλέπεις · άνοιξε πρώτα τον χάρτη"
                    tooBig -> "Η περιοχή που βλέπεις είναι πολύ μεγάλη (${n(areaTiles)} κομμάτια). Ζούμαρε πιο κοντά."
                    else -> "Η περιοχή που βλέπεις στον χάρτη · όλα τα στενά (${n(areaTiles)} κομμάτια)"
                },
                selected = !tooBig,
                onClick = {
                    if (!tooBig && !OfflineMaps.busy && view != null) {
                        val stamp = SimpleDateFormat("d/M HH:mm", Locale.US).format(Date())
                        OfflineMaps.download(context, "Περιοχή $stamp", view, OfflineMaps.AREA_MAX_ZOOM)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Για στενά: πήγαινε στον χάρτη, φέρε στην οθόνη την περιοχή που θες (π.χ. όλη την Αττική) και γύρνα εδώ.",
                style = MotoType.label,
                color = c.base.textMuted,
            )

            OfflineMaps.progress?.let {
                Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface, border = BorderStroke(1.dp, c.accent), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.md)) {
                        Text("Κατεβαίνει. Κράτα την εφαρμογή ανοιχτή.", style = MotoType.label, color = c.base.textMuted)
                        Text(it, style = MotoType.body, color = c.base.text)
                    }
                }
            }
            OfflineMaps.error?.let { Text(it, style = MotoType.label, color = c.base.warning) }

            if (OfflineMaps.saved.isNotEmpty()) Text("Στο κινητό", style = MotoType.title, color = c.base.text)
            OfflineMaps.saved.forEach { item ->
                Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MotoType.body, color = c.base.text)
                            Text(
                                "${n(item.tiles)} κομμάτια · ${item.bytes / 1_000_000} MB" + if (item.complete) "" else " · μισό",
                                style = MotoType.label,
                                color = if (item.complete) c.base.textMuted else c.base.warning,
                            )
                        }
                        TextChoice(
                            if (confirmDelete == item.name) "Σίγουρα;" else "Σβήσε",
                            selected = false,
                            onClick = {
                                if (confirmDelete == item.name) { OfflineMaps.delete(context, item); confirmDelete = null } else confirmDelete = item.name
                            },
                        )
                    }
                }
            }
        }
    }
}
