package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.motonav.nav.NavSession
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space

/** Μικρή ένδειξη πάνω στον χάρτη όσο καταγράφεις χωρίς πλοήγηση: ● Καταγραφή · ⏸ · ■ */
@Composable
fun RecPill(session: NavSession, modifier: Modifier = Modifier) {
    if (!session.recordingFree) return
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.surface,
        border = BorderStroke(1.dp, c.outline),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (session.recordingPaused) c.base.textMuted else Color(0xFFFF3B30)),
            )
            Text(if (session.recordingPaused) "Σε παύση" else "Καταγραφή", style = MotoType.label, color = c.base.text)
            TextChoice(if (session.recordingPaused) "▶" else "⏸", selected = false, onClick = { session.togglePause() })
            TextChoice("■", selected = false, onClick = { session.stopRecording() })
        }
    }
}

/** Κουμπιά καταγραφής στο Ημερολόγιο διαδρομών. */
@Composable
fun RecordingControls(session: NavSession?) {
    val c = LocalAppColors.current
    if (session == null) return
    if (!session.recording) {
        TextChoice("● Ξεκίνα καταγραφή (χωρίς πλοήγηση)", selected = false, onClick = { session.startRecording() }, modifier = Modifier.fillMaxWidth())
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
        TextChoice(
            if (session.recordingPaused) "▶ Συνέχεια" else "⏸ Παύση",
            selected = session.recordingPaused,
            onClick = { session.togglePause() },
            modifier = Modifier.weight(1f),
        )
        TextChoice(
            if (session.navigating) "■ Τέλος πλοήγησης" else "■ Τέλος καταγραφής",
            selected = false,
            onClick = { session.stopRecording() },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        if (session.recordingPaused) "Σε παύση: τα σημεία δεν γράφονται." else "Γράφεται η διαδρομή σου. Συνεχίζει και με κλειστή οθόνη.",
        style = MotoType.label,
        color = c.base.textMuted,
    )
}

/** Τέλος οδήγησης: «Αποθήκευση διαδρομής;» με όνομα. Την ξαναβρίσκεις στην αναζήτηση → Αποθηκευμένες διαδρομές. */
@Composable
fun SaveRoutePrompt(session: NavSession, modifier: Modifier = Modifier) {
    val p = session.savePrompt ?: return
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by androidx.compose.runtime.remember(p) { androidx.compose.runtime.mutableStateOf(p.suggested) }
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.surface,
        border = BorderStroke(1.dp, c.accent),
        modifier = modifier,
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                "Αποθήκευση της διαδρομής που έκανες; (" + String.format(java.util.Locale("el", "GR"), "%.1f km", p.lengthM / 1000) + ")",
                style = MotoType.body,
                color = c.base.text,
            )
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                TextChoice("Αποθήκευση", selected = true, onClick = {
                    app.motonav.trips.SavedRoutes.saveFromGpx(context, p.gpx, name, p.lengthM)
                    session.savePrompt = null
                }, modifier = Modifier.weight(1f))
                TextChoice("Όχι", selected = false, onClick = { session.savePrompt = null }, modifier = Modifier.weight(1f))
            }
        }
    }
}
