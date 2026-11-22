package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.motonav.nav.NavSession
import app.motonav.nav.Simulator
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space

/**
 * Στην πλοήγηση με στάσεις: «① Όνομα σε 12 km». Πάτημα: λίστα με όλες τις στάσεις,
 * και «Πήγαινε εδώ» για να παραλείψεις όσες είναι πριν (όπως το «skip waypoint» του Kurviger).
 */
@Composable
fun NextStopPill(session: NavSession, open: Boolean, onToggle: () -> Unit) {
    val (next, dist) = session.nextStop() ?: return
    val c = LocalAppColors.current
    if (!open) {
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = c.base.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, c.outline),
            onClick = onToggle,
        ) {
            Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    "📍 Στάση 1${next.name?.let { ": $it" } ?: ""} σε ${fuelKm(dist)}",
                    style = MotoType.label,
                    color = c.base.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                )
            }
        }
        return
    }
    val dists = session.stopDistances()
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.surface,
        border = BorderStroke(1.dp, c.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Στάσεις", style = MotoType.title, color = c.base.text, modifier = Modifier.weight(1f))
                TextChoice("✕", selected = false, onClick = onToggle)
            }
            session.stops.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        "${i + 1}. ${s.name ?: "Στάση"}" + (dists.getOrNull(i)?.let { " · ${fuelKm(it)}" } ?: ""),
                        style = MotoType.body,
                        color = c.base.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Η πρώτη είναι ήδη η επόμενη: για τις άλλες, «πήγαινε εδώ» = παράλειψη όσων είναι πριν.
                    if (i > 0) TextChoice("Πήγαινε εδώ", selected = false, onClick = { session.skipTo(i); onToggle() })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextChoice("Παράλειψη επόμενης", selected = true, onClick = { session.skipTo(1); onToggle() }, modifier = Modifier.weight(1f))
                TextChoice("Κατευθείαν στο τέλος", selected = false, onClick = { session.skipTo(session.stops.size); onToggle() }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** «Προσομοίωση» πάνω στον χάρτη: ταχύτητα 1×/2×/4× και τέλος. */
@Composable
fun SimulationPill(onStop: () -> Unit) {
    if (!Simulator.active) return
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.surface,
        border = BorderStroke(1.dp, c.accent),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text("▶ Προσομοίωση", style = MotoType.label, color = c.base.text)
            TextChoice("${Simulator.speedUp}×", selected = true, onClick = {
                Simulator.speedUp = when (Simulator.speedUp) { 1 -> 2; 2 -> 4; else -> 1 }
            })
            TextChoice("Τέλος", selected = false, onClick = onStop)
        }
    }
}
