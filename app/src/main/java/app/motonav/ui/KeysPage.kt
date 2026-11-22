package app.motonav.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.motonav.data.SettingsRepo
import app.motonav.nav.KeyBus
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Space
import kotlinx.coroutines.launch

/** Ρυθμίσεις → Χειριστήριο τιμονιού: ποιο κουμπί κάνει τι. */
@Composable
internal fun KeysPage() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Text(
        "Χειριστήρια στο τιμόνι (Carpe Iter, WunderLINQ, Parrot, Bluetooth remote ή πληκτρολόγιο) στέλνουν πλήκτρα. " +
            "Σύνδεσέ το από τις ρυθμίσεις Bluetooth του κινητού. Δουλεύει όσο φαίνεται ο χάρτης.",
        style = MotoType.label,
        color = c.base.textMuted,
    )
    Group {
        KeyBus.Action.entries.forEach { a ->
            val keys = KeyBus.mapping[a].orEmpty()
            val learning = KeyBus.learning == a
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MotoType.body, color = c.base.text)
                    Text(
                        if (learning) "Πάτα τώρα το κουμπί στο χειριστήριο…"
                        else keys.joinToString(" · ") { KeyBus.keyName(it) }.ifEmpty { "—" },
                        style = MotoType.label,
                        color = if (learning) c.accent else c.base.textMuted,
                    )
                }
                TextChoice(if (learning) "Άκυρο" else "Αλλαγή", selected = learning, onClick = {
                    KeyBus.learning = if (learning) null else a
                })
            }
        }
    }
    TextChoice("Επαναφορά προεπιλογών", selected = false, onClick = {
        KeyBus.reset { text -> scope.launch { SettingsRepo(context.applicationContext).saveKeyMap(text) } }
    }, modifier = Modifier.fillMaxWidth())
    Text(
        "Προεπιλογή: ↑ ζουμ +, ↓ ζουμ −, OK κέντραρε (πατημένο ξανά στην πλοήγηση: όλη η διαδρομή), ← φωνή, → αναφορά κινδύνου.",
        style = MotoType.label,
        color = c.base.textMuted,
    )
}
