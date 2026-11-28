package app.motonav.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.motonav.group.GroupCodes
import app.motonav.group.GroupLink
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.Touch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import app.motonav.i18n.tr
import app.motonav.i18n.trf

private val FRIEND_COLORS = listOf(
    0xFFFF8A1F, 0xFFFF2D2D, 0xFFFFD21F, 0xFF2ED573, 0xFF1FD1F9, 0xFF3B82F6, 0xFF8B5CF6, 0xFFFF4FA3, 0xFFFFFFFF,
).map { it.toInt() }

fun qrBitmap(text: String, size: Int = 640): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** Ρυθμίσεις → Παρέα: όνομα, χρώμα, νέα παρέα / μπες σε παρέα, QR, μέλη, έξοδος. */
@Composable
fun GroupScreen(onScan: () -> Unit, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var nameText by remember { mutableStateOf(GroupLink.name) }
    var codeText by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.base.borderInput,
        cursorColor = c.accent,
        focusedTextColor = c.base.text,
        unfocusedTextColor = c.base.text,
    )

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
                RideIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Πίσω"), onBack, size = Touch.min)
                Spacer(Modifier.width(Space.md))
                Text(tr("Παρέα"), style = MotoType.title, color = c.base.text)
            }
            if (!GroupLink.available) {
                Text(
                    tr("Η παρέα δεν είναι ενεργή σε αυτό το build: λείπει η σύνδεση με το Firebase (google-services.json)."),
                    style = MotoType.body,
                    color = c.base.warning,
                )
                return@Column
            }
            if (!GroupLink.enabled) {
                Text(
                    tr("Βλέπετε ο ένας τον άλλον στον χάρτη και μιλάτε σαν walkie-talkie, με 2–5 φίλους."),
                    style = MotoType.body,
                    color = c.base.text,
                )
                Text(
                    tr("Είναι κλειστή: η εφαρμογή δεν συνδέεται πουθενά για την Παρέα. Αν την ανοίξεις, θέση, όνομα και φωνή ") +
                        tr("κρυπτογραφούνται στο κινητό σου και τα βλέπουν μόνο όσοι έχουν τον κωδικό της παρέας σας. Την κλείνεις ξανά όποτε θες."),
                    style = MotoType.label,
                    color = c.base.textMuted,
                )
                TextChoice(tr("Άνοιγμα Παρέας"), selected = true, onClick = { GroupLink.enable() }, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            Text(
                tr("Βλέπετε ο ένας τον άλλον στον χάρτη και μιλάτε σαν walkie-talkie. Τη θέση σου τη βλέπουν μόνο όσοι είναι στην ίδια παρέα."),
                style = MotoType.label,
                color = c.base.textMuted,
            )

            // Όνομα και χρώμα
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it.take(20); GroupLink.setProfile(nameText, GroupLink.color) },
                singleLine = true,
                label = { Text(tr("Το όνομά σου")) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(Radius.md),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FRIEND_COLORS.forEach { col ->
                    val on = GroupLink.color == col
                    ColorSwatch(Color(col), on, tr("Χρώμα"), onClick = { GroupLink.setProfile(nameText, col) }) {
                        if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = if (col == 0xFFFFFFFF.toInt() || col == 0xFFFFD21F.toInt()) Color.Black else Color.White)
                    }
                }
            }

            val code = GroupLink.code
            if (code == null) {
                TextChoice(tr("Νέα παρέα"), selected = true, onClick = { GroupLink.create() }, modifier = Modifier.fillMaxWidth())
                TextChoice(tr("Σκάναρε QR φίλου"), selected = false, onClick = onScan, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it.take(12); codeError = false },
                        singleLine = true,
                        label = { Text(tr("Κωδικός (π.χ. ABCD-EFGH)")) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        isError = codeError,
                        shape = RoundedCornerShape(Radius.md),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    TextChoice(tr("Μπες"), selected = false, onClick = { codeError = !GroupLink.join(codeText) })
                }
                if (codeError) Text(tr("Ο κωδικός θέλει 8 γράμματα/αριθμούς."), style = MotoType.label, color = c.base.warning)
            } else {
                // QR και κωδικός για να μπουν οι άλλοι
                val qr = remember(code) { qrBitmap(GroupCodes.link(code)).asImageBitmap() }
                Surface(shape = RoundedCornerShape(Radius.lg), color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Image(qr, contentDescription = tr("QR της παρέας"), modifier = Modifier.size(220.dp).padding(Space.sm))
                }
                Text(GroupCodes.pretty(code), style = MotoType.title, color = c.base.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                TextChoice(
                    tr("Στείλε τον κωδικό"),
                    selected = false,
                    onClick = {
                        val text = trf("Μπες στην παρέα μου στο Streetwolf: {0}\n{1}", GroupCodes.pretty(code), GroupCodes.link(code))
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("Κωδικός παρέας")))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("Πώς μιλάς"), style = MotoType.title, color = c.base.text)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                    TextChoice(tr("Με τη φωνή"), selected = GroupLink.voxMode, onClick = { GroupLink.setVoice(true, GroupLink.voxSensitivity) }, modifier = Modifier.weight(1f))
                    TextChoice(tr("Με κουμπί"), selected = !GroupLink.voxMode, onClick = { GroupLink.setVoice(false, GroupLink.voxSensitivity) }, modifier = Modifier.weight(1f))
                }
                if (GroupLink.voxMode) {
                    Text(
                        tr("Πατάς το κουμπί του μικροφώνου στον χάρτη για να ανοίξει (μωβ) και ξανά για να κλείσει. Ξεκινάει πάντα κλειστό. Μετά μιλάς και στέλνεται μόνο του (1–3 s καθυστέρηση). Θέλει μικρόφωνο κράνους."),
                        style = MotoType.label,
                        color = c.base.textMuted,
                    )
                    Text(trf("Ευαισθησία: {0} (1 = μόνο δυνατά, 5 = και σιγανά)", GroupLink.voxSensitivity), style = MotoType.label, color = c.base.text)
                    androidx.compose.material3.Slider(
                        value = GroupLink.voxSensitivity.toFloat(),
                        onValueChange = { GroupLink.setVoice(true, it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.base.surface3),
                    )
                    // μετρητής: δες αν ο αέρας/η μηχανή το ανοίγει χωρίς να μιλάς
                    var lvl by remember { mutableStateOf(0f) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        while (true) { lvl = GroupLink.voxLevel; kotlinx.coroutines.delay(100) }
                    }
                    val thr = ((app.motonav.group.VoxDetector.thresholdFor(GroupLink.voxSensitivity) + 60.0) / 60.0).toFloat()
                    Box(Modifier.fillMaxWidth().height(14.dp).background(c.base.surface3, RoundedCornerShape(7.dp))) {
                        Box(Modifier.fillMaxWidth(lvl.coerceIn(0f, 1f)).height(14.dp).background(if (lvl >= thr) c.accent else c.base.textMuted, RoundedCornerShape(7.dp)))
                    }
                    Text(tr("Η μπάρα γίνεται χρωματιστή όταν θα έστελνε."), style = MotoType.label, color = c.base.textMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Δείχνω τη θέση μου"), style = MotoType.body, color = c.base.text, modifier = Modifier.weight(1f))
                    TextChoice(if (GroupLink.sharing) tr("Ναι") else tr("Όχι"), selected = GroupLink.sharing, onClick = { GroupLink.changeSharing(!GroupLink.sharing) })
                }

                Text(trf("Στην παρέα ({0})", GroupLink.members.size), style = MotoType.title, color = c.base.text)
                val now = GroupLink.now()
                GroupLink.members.forEach { m ->
                    Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Box(Modifier.size(14.dp).background(Color(m.color), CircleShape))
                            Text(m.name + if (m.uid == GroupLink.myUid) tr(" (εσύ)") else "", style = MotoType.body, color = c.base.text, modifier = Modifier.weight(1f))
                            Text(
                                listOfNotNull(m.speedKmh?.let { "${it.toInt()} km/h" }, FriendsLayer.subtitle(m, now) ?: tr("τώρα")).joinToString(" · "),
                                style = MotoType.label,
                                color = c.base.textMuted,
                            )
                        }
                    }
                }
                TextChoice(
                    if (confirmLeave) tr("Σίγουρα; Πάτα ξανά") else tr("Έξοδος από την παρέα"),
                    selected = false,
                    onClick = { if (confirmLeave) { GroupLink.leave(); confirmLeave = false } else confirmLeave = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GroupLink.error?.let { Text(it, style = MotoType.label, color = c.base.warning) }
            Spacer(Modifier.height(Space.lg))
            TextChoice(
                tr("Κλείσιμο Παρέας"),
                selected = false,
                onClick = { GroupLink.disable(); confirmLeave = false },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(tr("Βγαίνεις από την παρέα και η εφαρμογή δεν συνδέεται πια για την Παρέα."), style = MotoType.label, color = c.base.textMuted)
        }
    }
}
