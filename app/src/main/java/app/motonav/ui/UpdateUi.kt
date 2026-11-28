package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.motonav.update.Updater
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import kotlinx.coroutines.launch
import app.motonav.i18n.tr
import app.motonav.i18n.trf

/** «Νέα έκδοση» πάνω στον χάρτη (όχι όσο πλοηγείσαι). */
@Composable
fun UpdatePill(modifier: Modifier = Modifier) {
    val a = Updater.available ?: return
    if (Updater.dismissed) return
    val c = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.surface,
        border = BorderStroke(1.dp, c.accent),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(trf("Νέα έκδοση ({0})", a.tag), style = MotoType.label, color = c.base.text)
                TextChoice(
                    Updater.downloading ?: if (a.apkUrl != null) tr("Εγκατάσταση") else tr("Άνοιγμα"),
                    selected = true,
                    onClick = { if (Updater.downloading == null) scope.launch { Updater.install(context) } },
                )
                TextChoice("✕", selected = false, onClick = { Updater.dismissed = true })
            }
            Updater.error?.let { Text(it, style = MotoType.label, color = c.base.warning) }
        }
    }
}
