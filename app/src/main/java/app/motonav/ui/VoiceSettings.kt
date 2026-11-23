package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.motonav.nav.ElevenClient
import app.motonav.nav.ElevenModel
import app.motonav.nav.ElevenSettings
import app.motonav.nav.ElevenStatus
import app.motonav.nav.ElevenStore
import app.motonav.nav.ElevenVoice
import app.motonav.nav.VoiceGuide
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val SAMPLE_GR = "Σε 300 μέτρα, στρίψε δεξιά στη Λεωφόρο Συγγρού."
private const val SAMPLE_EN = "In 300 metres, turn right onto Leoforos Syngrou."
private val SAMPLE get() = if (app.motonav.nav.VoiceLang.english) SAMPLE_EN else SAMPLE_GR

/** Ρυθμίσεις → Φωνή πλοήγησης: ElevenLabs (όλες οι φωνές και ρυθμίσεις του λογαριασμού) ή η φωνή του κινητού. */
@Composable
fun VoicePage() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(ElevenStore.load(context)) }
    fun update(n: ElevenSettings) {
        s = n
        ElevenStore.save(context, n)
    }
    var voices by remember { mutableStateOf<List<ElevenVoice>>(emptyList()) }
    var library by remember { mutableStateOf<List<ElevenVoice>>(emptyList()) }
    var models by remember { mutableStateOf<List<ElevenModel>>(emptyList()) }
    var loading by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var cacheBytes by remember { mutableStateOf(ElevenStore.cacheSizeBytes(context)) }
    // Τι έγινε στη «Δοκιμή» και στη βιβλιοθήκη: δείχνεται ακριβώς κάτω από το κουμπί, όχι πάνω πάνω.
    var testStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var libStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Δοκιμή φωνής και ακρόαση δειγμάτων.
    val tester = remember { VoiceGuide(context) }
    val preview = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            tester.shutdown()
            preview.value?.release()
        }
    }
    fun playPreview(url: String) {
        preview.value?.release()
        preview.value = android.media.MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            setDataSource(url) // http(s) ή αρχείο στο κινητό
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ -> message = "Δεν παίζει το δείγμα (internet;)"; true }
            prepareAsync()
        }
    }
    fun loadAll() {
        if (s.key.isBlank()) { message = "Βάλε πρώτα το κλειδί."; return }
        scope.launch {
            loading = "Φορτώνω φωνές και μοντέλα…"
            message = null
            try {
                val (v, m) = withContext(Dispatchers.IO) { ElevenClient.voices(s.key) to runCatching { ElevenClient.models(s.key) }.getOrDefault(emptyList()) }
                voices = v
                models = m
                if (v.isEmpty()) message = "Ο λογαριασμός δεν έχει φωνές."
            } catch (e: Exception) {
                message = e.message ?: e.toString()
            } finally {
                loading = null
            }
        }
    }

    var startPhrase by remember { mutableStateOf(ElevenStore.startPhrase(context)) }
    var english by remember { mutableStateOf(ElevenStore.english(context)) }
    Group {
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Γλώσσα οδηγιών", style = MotoType.body, color = c.base.text)
            Text(
                "Ελληνικά ή English (φωνή του κινητού ή ElevenLabs). Στα αγγλικά τα ονόματα δρόμων λέγονται με λατινικούς χαρακτήρες. " +
                    "Αν η αγγλική φωνή δεν ακούγεται, κατέβασέ την: Ρυθμίσεις κινητού → Μετατροπή κειμένου σε ομιλία.",
                style = MotoType.label,
                color = c.base.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextChoice("Ελληνικά", selected = !english, onClick = { english = false; ElevenStore.setEnglish(context, false) }, modifier = Modifier.weight(1f))
                TextChoice("English", selected = english, onClick = { english = true; ElevenStore.setEnglish(context, true) }, modifier = Modifier.weight(1f))
            }
            TextChoice("Άκουσέ τη", selected = false, onClick = { tester.say(SAMPLE) }, modifier = Modifier.fillMaxWidth())
        }
    }
    Group {
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Φράση στο «Ξεκίνα»", style = MotoType.body, color = c.base.text)
            Text("Π.χ. «Πάμε!», «Καλό ταξίδι.» ή «Πάμε για {προορισμός}.» (μπαίνει το όνομα του προορισμού).", style = MotoType.label, color = c.base.textMuted)
            KeyFieldPlain(startPhrase, ElevenStore.DEFAULT_START) {
                startPhrase = it
                ElevenStore.setStartPhrase(context, it)
            }
            TextChoice("Άκουσέ την", selected = false, onClick = {
                tester.say(startPhrase.ifBlank { ElevenStore.DEFAULT_START }.replace("{προορισμός}", "Λεωφόρο Συγγρού"))
            }, modifier = Modifier.fillMaxWidth())
        }
    }
    Group {
        SwitchRow(
            "Φωνή ElevenLabs",
            if (s.usable) "Φωνή: ${s.voiceName}" else "Κλειστό: μιλάει η φωνή του κινητού",
            s.enabled,
        ) { update(s.copy(enabled = it)) }
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Κλειδί API από elevenlabs.io → Developers → API Keys (με δικαίωμα Text to Speech και Voices). Μένει μόνο στο κινητό.", style = MotoType.label, color = c.base.textMuted)
            KeyField(s.key) { update(s.copy(key = it)) }
        }
        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TextChoice("Φόρτωσε φωνές", selected = false, onClick = { loadAll() }, modifier = Modifier.weight(1f))
            TextChoice("Δοκιμή", selected = true, onClick = {
                // Η δοκιμή πάει κατευθείαν στο ElevenLabs (χωρίς τη φωνή του κινητού ως εφεδρεία),
                // για να φαίνεται αμέσως αν φταίει το κλειδί, η φωνή ή το μοντέλο.
                val why = when {
                    s.key.isBlank() -> "Λείπει το κλειδί."
                    s.voiceId.isBlank() -> "Διάλεξε πρώτα φωνή (Φόρτωσε φωνές → πάτα μία)."
                    else -> null
                }
                if (why != null) {
                    testStatus = false to why
                    tester.say(SAMPLE) // ακούς τη φωνή του κινητού
                } else {
                    val now = s
                    scope.launch {
                        testStatus = true to "Φτιάχνω τη φράση με «${now.voiceName}»…"
                        try {
                            val f = withContext(Dispatchers.IO) { ElevenClient.speech(context, now, SAMPLE) }
                            playPreview(f.path)
                            cacheBytes = ElevenStore.cacheSizeBytes(context)
                            testStatus = true to "✓ ElevenLabs · ${now.voiceName} · ${now.modelId}"
                        } catch (e: Exception) {
                            testStatus = false to "✗ ${e.message ?: e}"
                        }
                    }
                }
            }, modifier = Modifier.weight(1f))
        }
        testStatus?.let { (ok, text) ->
            Text(
                text,
                style = MotoType.body,
                color = if (ok) c.base.success else c.base.danger,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
            )
        }
        loading?.let { InfoRow("", it) }
        (message ?: ElevenStatus.lastError)?.let { InfoRow("Μήνυμα", it) }
        InfoRow(
            "Χωρίς internet",
            "Κάθε φράση που ακούστηκε μία φορά μένει στο κινητό (τώρα ${"%.1f".format(Locale.US, cacheBytes / 1_048_576.0)} MB). " +
                "Νέες φράσεις χωρίς internet ή αν τελειώσουν οι χαρακτήρες: η φωνή του κινητού.",
        )
    }

    if (models.isNotEmpty()) {
        Group {
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Μοντέλο", style = MotoType.body, color = c.base.text)
                models.sortedByDescending { it.greek }.forEach { m ->
                    Choice(
                        title = m.name,
                        subtitle = (if (m.greek) "ελληνικά ✓ · " else "χωρίς ελληνικά · ") + m.id,
                        selected = s.modelId == m.id,
                    ) { update(s.copy(modelId = m.id)) }
                }
            }
        }
    } else {
        Group { InfoRow("Μοντέλο", s.modelId + " (πάτα «Φόρτωσε φωνές» για όλα)") }
    }

    Group {
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Ρυθμίσεις φωνής (όπως στο site)", style = MotoType.body, color = c.base.text)
            SettingSlider("Ταχύτητα", s.speed, 0.7f..1.2f, "%.2f×") { update(s.copy(speed = it)) }
            SettingSlider("Σταθερότητα (Stability)", s.stability, 0f..1f, "%.0f%%", 100f) { update(s.copy(stability = it)) }
            SettingSlider("Ομοιότητα (Similarity)", s.similarity, 0f..1f, "%.0f%%", 100f) { update(s.copy(similarity = it)) }
            SettingSlider("Ύφος (Style)", s.style, 0f..1f, "%.0f%%", 100f) { update(s.copy(style = it)) }
        }
        SwitchRow("Speaker boost", "Πιο καθαρή και πιο κοντά στην αρχική φωνή", s.speakerBoost) { update(s.copy(speakerBoost = it)) }
        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TextChoice("Προεπιλογές", selected = false, onClick = {
                update(s.copy(stability = 0.5f, similarity = 0.75f, style = 0f, speakerBoost = true, speed = 1f))
            }, modifier = Modifier.weight(1f))
            TextChoice("Σβήσε αποθηκευμένες", selected = false, onClick = {
                ElevenStore.clearCache(context)
                cacheBytes = 0
            }, modifier = Modifier.weight(1f))
        }
    }

    // Επιλεγμένη φωνή που θέλει συνδρομή (π.χ. από τη βιβλιοθήκη): προειδοποίηση, γιατί αλλιώς «σιωπή» και ζητάει subscribe.
    val chosen = voices.firstOrNull { it.id == s.voiceId }
    if (chosen != null && chosen.category != "premade" && chosen.category != "generated") {
        Group { InfoRow("Προσοχή", "Η «${chosen.name}» θέλει συνδρομή στο ElevenLabs. Διάλεξε μία από τις δωρεάν παρακάτω.") }
    }
    if (voices.isNotEmpty()) {
        Group {
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                val free = voices.filter { it.category == "premade" || it.category == "generated" }
                Text("Δωρεάν φωνές (${free.size})", style = MotoType.body, color = c.base.text)
                Text(
                    "Δείχνουμε μόνο φωνές που παίζουν στο δωρεάν πακέτο, χωρίς συνδρομή. Μιλάνε ελληνικά και αγγλικά " +
                        "(με Multilingual v2 ή Flash v2.5). ▶ για δείγμα, πάτημα για επιλογή.",
                    style = MotoType.label,
                    color = c.base.textMuted,
                )
                KeyFieldPlain(filter, "Ψάξε φωνή (όνομα, female, male…)") { filter = it }
                free
                    .filter { v -> filter.isBlank() || (v.name + " " + v.description + " " + v.category).contains(filter, ignoreCase = true) }
                    .sortedBy { if (it.category == "premade") 0 else 1 }
                    .forEach { v ->
                        val kind = when (v.category) {
                            "premade" -> "ΔΩΡΕΑΝ"
                            "professional", "famous", "high_quality" -> "βιβλιοθήκη · θέλει συνδρομή"
                            "cloned" -> "δικός σου κλώνος"
                            "generated" -> "δική σου (Voice Design)"
                            else -> v.category
                        }
                        Choice(
                            title = v.name,
                            subtitle = listOf(kind, v.description).filter { it.isNotBlank() }.joinToString(" · "),
                            selected = s.voiceId == v.id,
                            onPreview = v.previewUrl?.let { url -> { playPreview(url) } },
                        ) {
                            update(s.copy(voiceId = v.id, voiceName = v.name))
                            testStatus = true to "Επιλέχθηκε «${v.name}». «Δοκιμή» για να την ακούσεις· στην πλοήγηση μιλάει μόνο αν ανοίξεις τον διακόπτη «Φωνή ElevenLabs»."
                        }
                    }
            }
        }
    }
}

@Composable
private fun Choice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onPreview: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) c.accent.copy(alpha = 0.16f) else c.base.surface2,
        border = BorderStroke(1.dp, if (selected) c.accent else c.outline.copy(alpha = 0.5f)),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (selected) "✓ " else "") + title, style = MotoType.body, color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MotoType.label, color = c.base.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onPreview != null) {
                Spacer(Modifier.width(Space.sm))
                TextChoice("▶", selected = false, onClick = onPreview)
            }
            if (actionLabel != null) {
                Spacer(Modifier.width(Space.sm))
                if (onAction != null) TextChoice(actionLabel, selected = true, onClick = onAction)
                else Text(actionLabel, style = MotoType.label, color = c.accent)
            }
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: String, mult: Float = 1f, onChange: (Float) -> Unit) {
    val c = LocalAppColors.current
    var v by remember(value) { mutableStateOf(value) }
    Column {
        Text("$label: ${String.format(Locale.US, fmt, v * mult)}", style = MotoType.label, color = c.base.text)
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onChange((v * 100).toInt() / 100f) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.base.surface3),
        )
    }
}

@Composable
private fun KeyFieldPlain(value: String, hint: String, onChange: (String) -> Unit) {
    val c = LocalAppColors.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(hint, style = MotoType.label, color = c.base.textMuted) },
        textStyle = MotoType.body.copy(color = c.base.text),
        shape = RoundedCornerShape(Radius.md),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.base.borderInput, cursorColor = c.accent),
        modifier = Modifier.fillMaxWidth(),
    )
}
