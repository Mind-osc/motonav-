package app.motonav.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.i18n.tr

/**
 * «Τι νέο υπάρχει»: βγαίνει μία φορά μετά από ενημέρωση της εφαρμογής.
 * Κάθε νέα έκδοση με αλλαγές που φαίνονται → νέα εγγραφή ΣΤΗΝ ΑΡΧΗ της λίστας, με μεγαλύτερο id.
 */
object WhatsNew {
    data class Entry(val id: Int, val title: String, val added: List<String>, val fixed: List<String> = emptyList())

    val entries = listOf(
        Entry(
            14, tr("Η εφαρμογή και στα αγγλικά 🇬🇧"),
            added = listOf(
                tr("Ρυθμίσεις → Γλώσσα · Language: Κινητό, Ελληνικά ή English"),
                tr("«Κινητό»: αγγλικά όταν το κινητό δεν είναι στα ελληνικά"),
                tr("Και η φωνή πλοήγησης ακολουθεί τη γλώσσα, εκτός αν τη διάλεξες χωριστά"),
            ),
        ),
        Entry(
            13, tr("Επιτάχυνση g και 0–100 (δοκιμαστικό)"),
            added = listOf(
                tr("Νέα λωρίδα επιτάχυνσης στη δεξιά άκρη: πράσινο προς τα κάτω στο γκάζι, κόκκινο προς τα πάνω στο φρένο, με τα μέγιστα"),
                tr("0–100: ξεκινάει μόνο του όταν φεύγεις από στάση και κρατάει το καλύτερο. Πάτημα πάνω στο όργανο = μηδενισμός"),
                tr("Κλείνει από Ρυθμίσεις → Όργανα"),
            ),
        ),
        Entry(
            12, tr("Εφαρμογές και μουσική στον χάρτη 🎵"),
            added = listOf(
                tr("Νέο κουμπί «Εφαρμογές» στον χάρτη: πρόσθεσε όποιες εφαρμογές θες (π.χ. Spotify) και άνοιξέ τες από εκεί"),
                tr("Εφαρμογή μουσικής: βγαίνει λεπτό pill πάνω στο κέντρο με ⏮ ▶ ⏭. Πατάς play και ανεβαίνει μόνο του· μένει ένα μικρό κουμπάκι για να το ξανακατεβάσεις"),
                tr("Πάτημα στον χάρτη: το pill κρύβεται πάνω. Θέλει μία φορά «Πρόσβαση στις ειδοποιήσεις»"),
            ),
        ),
        Entry(
            11, tr("Νέο όνομα: Streetwolf 🐺"),
            added = listOf(
                tr("Το Moto Nav λέγεται πλέον Streetwolf, με νέο εικονίδιο. Οι ρυθμίσεις και τα δεδομένα σου μένουν όπως ήταν"),
                tr("Κατηγορίες (π.χ. καφέ, βενζίνη): πάτα τον χάρτη και τα κοντινά σημεία ως 3 km μένουν πάνω του με το όνομά τους. Πάτα ένα για «Ξεκίνα»"),
                tr("Αναζήτηση μαγαζιού (π.χ. «coffee island»): πρώτα τα κοντινά ως 5 km"),
            ),
        ),
        Entry(
            10, tr("Γρήγορες ενέργειες και μετρητής ταξιδιού"),
            added = listOf(
                tr("Κουμπί ⚡ στον χάρτη: «Γρήγορες ενέργειες» με μετρητή ταξιδιού A/B, καταγραφή, αποθήκευση θέσης, αναφορά χάρτη και διακόπτες"),
                tr("Trip A/B: απόσταση, χρόνοι κίνησης και στάσης, μέσες, μέγιστη, άνοδος/κάθοδος. Το A ξεκινάει μόνο του νέο, το B το μηδενίζεις εσύ"),
                tr("Στην πλοήγηση: απόσταση ως το επόμενο ⛽ ☕ 🍴 💧 στη διαδρομή (χωρίς internet). Πάτημα = στάση εκεί"),
                tr("Αναφορά χάρτη: λείπει δρόμος, λάθος όνομα κ.λπ. πάει ως σημείωση στο OpenStreetMap για να διορθωθεί"),
                tr("Χρώμα κλίσης στη γραμμή: κίτρινο, πορτοκαλί, κόκκινο στις απότομες ανηφόρες και κατηφόρες"),
                tr("Εισαγωγή σημείων: ένα ZIP ομάδας (π.χ. 4GPS) γίνεται μία κατηγορία, με διεύθυνση και τηλέφωνο. Αρχεία καμερών πάνε στις ειδοποιήσεις καμερών με όριο και κατεύθυνση"),
            ),
            fixed = listOf(tr("Η ταμπέλα στροφής στο οριζόντιο δεν «φουσκώνει» πια")),
        ),
        Entry(
            9, tr("Διασταυρώσεις, δικά σου σημεία, αυτοκίνητο"),
            added = listOf(
                tr("Αναζήτηση διασταύρωσης: γράψε «Λιοσίων & Πραΐδου» (ή «και», «/»), βρίσκει τη γωνία και χωρίς internet"),
                tr("Δικά σου σημεία: Ρυθμίσεις → Χάρτης → «Εισαγωγή σημείων» (GPX, KML, CSV, OV2, ZIP). Κάθε αρχείο γίνεται κατηγορία στον χάρτη και στην αναζήτηση"),
                tr("Αυτοκίνητο: Ρυθμίσεις → Χάρτης. Διαδρομές και κίνηση για αυτοκίνητο, χωρίς «Χώμα» και κλισιόμετρο"),
                tr("Εξοικονόμηση ενέργειας: Ρυθμίσεις → Χάρτης. Λιγότερο ζέσταμα και μπαταρία στα μεγάλα ταξίδια"),
            ),
        ),
        Entry(
            8, tr("Φωνή με λιγότερα λόγια"),
            added = listOf(
                tr("Μιλάει μόνο όταν χρειάζεται: μία φορά πριν τη στροφή και ένα σύντομο «στρίψε δεξιά» πάνω της"),
                tr("Η προειδοποίηση από μακριά μόνο σε μεγάλους δρόμους (όχι σε κάθε στροφή της πόλης)"),
                tr("Δεν λέει πια «συνέχισε ευθεία» και δεν επαναλαμβάνει το «νέα διαδρομή»"),
            ),
        ),
        Entry(
            7, tr("Αναζήτηση"),
            added = listOf(
                tr("Διακόπτης στις Ρυθμίσεις → Εμφάνιση: το πληκτρολόγιο ανοίγει μόνο όταν πατήσεις εκεί που γράφεις"),
            ),
            fixed = listOf(
                tr("Βενζινάδικα, καφέ και οι άλλες κατηγορίες βγάζουν πάντα αποτελέσματα, άμεσα και χωρίς κλειδί TomTom"),
            ),
        ),
        Entry(
            6, tr("Φωνή και μέρη"),
            added = listOf(
                tr("Οδηγίες και στα αγγλικά: Ρυθμίσεις → Φωνή πλοήγησης → Γλώσσα οδηγιών → English"),
                tr("Μέρη για μηχανόβιους: διαλέγεις τι φαίνεται (θέες, νερό, αυχένες, στέκια, συνεργεία, πλυντήρια, πικνίκ)"),
                tr("Πλοήγηση: βλέπεις περισσότερο δρόμο μπροστά (βελάκι πιο χαμηλά, πιο ανοιχτή εικόνα)"),
            ),
            fixed = listOf(
                tr("ElevenLabs: φαίνονται μόνο οι δωρεάν φωνές, για να μη ζητάει συνδρομή"),
                tr("Το βελάκι δεν ξεκολλάει πια από τη γραμμή όταν κινείσαι"),
                tr("Αν μπεις σε άλλο στενό, βγάζει νέα διαδρομή πιο γρήγορα (~3″ αντί για 6–8″)"),
            ),
        ),
        Entry(
            5, tr("Κέρασέ με έναν καφέ"),
            added = listOf(tr("☕ Αν σου αρέσει η εφαρμογή: Ρυθμίσεις → «Κέρασέ με έναν καφέ» (PayPal). Η εφαρμογή μένει δωρεάν, χωρίς διαφημίσεις.")),
        ),
        Entry(
            4, tr("Ιδέες από το Kurviger"),
            added = listOf(
                tr("Αποφυγές: πλοία, κύριοι δρόμοι, πολύ στενοί δρόμοι, χωματόδρομοι (Ρυθμίσεις → Χάρτης)"),
                tr("Στην πλοήγηση, κουμπί ⚠: «Κλειστός δρόμος μπροστά» (σε πάει από αλλού) και «Νέα διαδρομή από εδώ»"),
                tr("Στάσεις: «Στάση 1 σε 12 km» κάτω από τις οδηγίες· πάτα για παράλειψη ή «πήγαινε εδώ»"),
                tr("Αριθμοί πάνω στις στάσεις και βελάκια κατεύθυνσης πάνω στη γραμμή"),
                tr("Προσομοίωση: κράτα πατημένο το «Ξεκίνα» (1×/2×/4×)"),
                tr("Αποθηκευμένη διαδρομή «⇅ Ανάποδα»"),
            ),
        ),
        Entry(
            3, tr("Όργανα, βενζίνη και «Τι νέο»"),
            added = listOf(
                tr("Αυτό το παράθυρο: μετά από κάθε ενημέρωση βλέπεις τι άλλαξε"),
                tr("Διακόπτης για τον κόκκινο φωτισμό όταν περνάς το όριο (Ρυθμίσεις → Κάμερες και ειδοποιήσεις)"),
                tr("Όρθια οθόνη: το ηχείο τέρμα κάτω αριστερά, μεγαλύτερα νούμερα στην άφιξη"),
            ),
            fixed = listOf(
                tr("Το σήμα ορίου δεν σκεπάζει πια την πυξίδα"),
                tr("Πάτημα στην πυξίδα: δείχνει σωστά όλη τη διαδρομή που μένει"),
                tr("Τα βενζινάδικα δεν γεμίζουν τον χάρτη: πάτα τη γραμμή ⛽ στην κάρτα για να φανούν· στην πλοήγηση μόνο το επόμενο"),
            ),
        ),
        Entry(
            2, tr("Ιδέες από το Cruiser"),
            added = listOf(
                tr("Χειριστήριο τιμονιού (κουμπιά Bluetooth)"),
                tr("Περιοχές αποφυγής (⊘ στην κάρτα)"),
                tr("«Πολλές στροφές» στις Στροφές"),
                tr("Κυκλική βόλτα: διαφορετική κάθε φορά"),
                tr("Εισαγωγή GeoJSON και ITN"),
            ),
        ),
        Entry(
            1, tr("Αποθηκευμένες διαδρομές"),
            added = listOf(
                tr("Αποθήκευση διαδρομής στο τέλος της οδήγησης και «Ακολούθησε» από όπου είσαι"),
                tr("Ειδοποίηση νέας έκδοσης με εγκατάσταση"),
            ),
            fixed = listOf(tr("Η λήψη της ενημέρωσης δεν κολλάει πια")),
        ),
    )

    private const val PREFS = "whats_new"
    private const val SEEN = "seen"

    /** Οι εγγραφές που δεν έχεις δει (άδειο σε πρώτη εγκατάσταση). */
    fun pending(context: Context): List<Entry> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val latest = entries.maxOf { it.id }
        if (!p.contains(SEEN)) {
            // Πρώτη εγκατάσταση: τίποτα. Ενημέρωση από παλιά έκδοση (χωρίς αυτό το παράθυρο): όλα, από το πιο νέο.
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val fresh = info == null || info.firstInstallTime == info.lastUpdateTime
            if (fresh) { markSeen(context); return emptyList() }
            return entries
        }
        // Όλες οι αλλαγές από την έκδοση που είχες ως αυτή που κατέβασες (π.χ. από την 13 στην 17: 14, 15, 16, 17).
        val seen = p.getInt(SEEN, latest)
        return entries.filter { it.id > seen }
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(SEEN, entries.maxOf { it.id }).apply()
    }
}

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun WhatsNewOverlay() {
    val context = LocalContext.current
    val items = remember { WhatsNew.pending(context) }
    if (items.isEmpty()) return
    val shown = remember { MutableTransitionState(false) }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(600); opened = true; shown.targetState = true }
    val done = opened && !shown.targetState && !shown.currentState && shown.isIdle
    // Μόλις κλείσει (τέλος του «μαζέματος»), δεν ξαναβγαίνει.
    LaunchedEffect(done) { if (done) WhatsNew.markSeen(context) }
    if (done) return
    val c = LocalAppColors.current
    val close = { shown.targetState = false }

    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(300, delayMillis = 200)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close)
                .safeDrawingPadding()
                .padding(Space.lg),
            contentAlignment = Alignment.Center,
        ) {
            // Το κουτάκι «πετάγεται» με ελατήριο και «μαζεύει» προς το κουμπί όταν το κλείνεις.
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = c.base.surface,
                    border = BorderStroke(1.dp, c.accent.copy(alpha = 0.6f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .animateEnterExit(
                            enter = scaleIn(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow), initialScale = 0.6f) + fadeIn(tween(200)),
                            exit = scaleOut(tween(380), targetScale = 0.12f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                                shrinkVertically(tween(380), shrinkTowards = Alignment.Bottom) +
                                fadeOut(tween(300, delayMillis = 80)),
                        )
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        Text(tr("Τι νέο υπάρχει"), style = MotoType.title, color = c.base.text)
                        Column(
                            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Space.md),
                        ) {
                            items.forEach { e ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(e.title, style = MotoType.label.copy(fontWeight = FontWeight.Bold), color = c.accent)
                                    if (e.added.isNotEmpty()) Section(tr("Νέα"), e.added, c.base.text)
                                    if (e.fixed.isNotEmpty()) Section(tr("Διορθώθηκαν"), e.fixed, c.base.text)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextChoice(tr("Κλείσιμο"), selected = true, onClick = close, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
        }
    }
}

@Composable
private fun Section(label: String, lines: List<String>, color: Color) {
    val c = LocalAppColors.current
    Text(label, style = MotoType.label, color = c.base.textMuted)
    lines.forEach { Text("•  $it", style = MotoType.body, color = color) }
}
