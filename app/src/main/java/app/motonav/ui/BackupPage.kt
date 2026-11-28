package app.motonav.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.motonav.data.Backup
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.motonav.i18n.tr
import app.motonav.i18n.trf

/** Ρυθμίσεις → Αντίγραφο ασφαλείας: όλα σε ένα αρχείο, και επαναφορά με ένα πάτημα. */
@Composable
internal fun BackupPage() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun kb(b: Long) = if (b < 1024 * 1024) "${b / 1024} KB" else "%.1f MB".format(b / 1048576.0)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = tr("Αποθήκευση…"); error = false
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.export(context, it) } }
            }
            r.onSuccess { status = trf("Έτοιμο: {0} αρχεία, {1}. Κράτα το αρχείο κάπου ασφαλές.", it.files, kb(it.bytes)) }
                .onFailure { status = trf("Δεν αποθηκεύτηκε: {0}", it.message); error = true }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = tr("Επαναφορά…"); error = false
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.import(context, it) } }
            }
            r.onSuccess {
                status = trf("Έγινε: {0} αρχεία. Η εφαρμογή ξανανοίγει…", it.files)
                delay(1200)
                Backup.restart(context)
            }.onFailure { status = trf("Δεν έγινε επαναφορά: {0}", it.message); error = true }
        }
    }

    Group {
        LinkRow(tr("Αποθήκευση αντιγράφου"), tr("Ρυθμίσεις, κλειδιά, αγαπημένα, φωνή, παρέα, κάμερες, αναφορές, ημερολόγιο")) {
            save.launch(Backup.fileName())
        }
        LinkRow(tr("Επαναφορά από αντίγραφο"), tr("Διάλεξε το αρχείο .zip. Τα τωρινά αντικαθίστανται και η εφαρμογή ξανανοίγει.")) {
            restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
    }
    Column(Modifier.padding(horizontal = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        status?.let {
            Text(it, style = MotoType.body, color = if (error) c.base.danger else c.base.text)
        }
        Text(
            tr("Το αρχείο έχει μέσα τα κλειδιά σου (TomTom, ElevenLabs, MapTiler) και τον κωδικό της παρέας. Μην το μοιράζεσαι. ") +
                tr("Δεν έχει τους offline χάρτες: τους ξανακατεβάζεις."),
            style = MotoType.label,
            color = c.base.textMuted,
        )
    }
}
