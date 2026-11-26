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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.motonav.data.DisplayPrefs
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.Touch

/** S11 Ρυθμίσεις. */
@Composable
fun SettingsScreen(
    display: DisplayPrefs,
    onDisplayChange: (DisplayPrefs) -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenOffline: () -> Unit = {},
    onOpenMarker: () -> Unit = {},
    onOpenGroup: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    /** Ποια σελίδα: "main", "look", "map", "gauges", "cameras", "about". */
    page: String = "main",
    onPage: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    Box(
        Modifier
            .fillMaxSize()
            .background(c.base.bg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            val titles = mapOf(
                "main" to "Ρυθμίσεις",
                "look" to "Εμφάνιση",
                "map" to "Χάρτης",
                "gauges" to "Όργανα",
                "cameras" to "Κάμερες και ειδοποιήσεις",
                "about" to "Πηγές και άδειες",
                "voice" to "Φωνή πλοήγησης",
                "backup" to "Αντίγραφο ασφαλείας",
                "keys" to "Χειριστήριο τιμονιού",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RideIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    if (page == "main") "Πίσω στον χάρτη" else "Πίσω στις ρυθμίσεις",
                    { if (page == "main") onBack() else onPage("main") },
                    size = Touch.min,
                )
                Spacer(Modifier.width(Space.lg))
                Text(titles[page] ?: "Ρυθμίσεις", style = MotoType.street, color = c.base.text)
            }

            when (page) {
                "look" -> {
                    Group {
                        LinkRow("Θέμα και χρώματα", "Σκούρο / μαύρο / ανοιχτό / αυτόματο, χρώμα εφαρμογής", onOpenAppearance)
                        LinkRow("Βελάκι και γραμμή", "Σχήμα, μέγεθος, χρώματα, περίγραμμα, δική σου εικόνα", onOpenMarker)
                    }
                    Group {
                        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                            Text("Μέγεθος κουμπιών και κειμένων: ${(display.uiScale * 100).toInt()}%", style = MotoType.body, color = c.base.text)
                            androidx.compose.material3.Slider(
                                value = display.uiScale,
                                onValueChange = { onDisplayChange(display.copy(uiScale = (it * 20).toInt() / 20f)) },
                                valueRange = 0.75f..1.4f,
                                steps = 12,
                                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.base.surface3),
                            )
                        }
                        SwitchRow("Χρώμα ταμπέλας ανά δρόμο", "Πράσινη: αυτοκινητόδρομος · Μπλε: εθνική/κύρια · Πορτοκαλί: χώμα · Μπορντό: τελευταία στροφή/άφιξη", display.roadColors) {
                            onDisplayChange(display.copy(roadColors = it))
                        }
                        SwitchRow("Δική μας ώρα και μπαταρία", "Πάνω πάνω, αντί για τη γραμμή του κινητού (σύρε από πάνω για να τη δεις)", display.customStatus) {
                            onDisplayChange(display.copy(customStatus = it))
                        }
                        SwitchRow("Πληκτρολόγιο με το άνοιγμα αναζήτησης", "Ανοιχτό: βγαίνει αμέσως · Κλειστό: μόνο όταν πατήσεις εκεί που γράφεις", display.searchKeyboard) {
                            onDisplayChange(display.copy(searchKeyboard = it))
                        }
                    }
                }
                "map" -> {
                    Group {
                        SwitchRow("Εναλλακτικές διαδρομές", "Έως 4 λογικές, με «+3′» δίπλα. Κάτω από 5 km ή κλειστό εδώ: κουμπί «⇄ Εναλλακτικές» στην κάρτα.", display.showAlternatives) {
                            onDisplayChange(display.copy(showAlternatives = it))
                        }
                        SwitchRow("Αυτοκίνητο", "Διαδρομές και κίνηση για αυτοκίνητο· κρύβει το «Χώμα» και το κλισιόμετρο", display.car) {
                            onDisplayChange(display.copy(car = it))
                        }
                        SwitchRow("Εξοικονόμηση ενέργειας", "Λιγότερο ζέσταμα και μπαταρία: πιο αργή ανανέωση χάρτη, λιγότερη κλίση στην πλοήγηση, χωρίς ανάγλυφο και ισοϋψείς", display.powerSave) {
                            onDisplayChange(display.copy(powerSave = it))
                        }
                        SwitchRow("Πολλές στροφές", "Ο τύπος «Στροφές» ψάχνει πιο στριφτούς δρόμους, και με μεγαλύτερη παράκαμψη (έως 2× χρόνο)", display.moreCurves) {
                            onDisplayChange(display.copy(moreCurves = it))
                        }
                    }
                    Group {
                        // Αποφυγές (όπως στο Kurviger). Αυτοκινητόδρομοι και διόδια είναι στην κάρτα της διαδρομής.
                        SwitchRow("Αποφυγή πλοίων", "Χωρίς φέρι στη διαδρομή", display.avoidFerries) {
                            onDisplayChange(display.copy(avoidFerries = it))
                        }
                        SwitchRow("Αποφυγή κύριων δρόμων", "Λιγότερες εθνικές και κύριες οδούς (λιγότερη κίνηση)", display.avoidMain) {
                            onDisplayChange(display.copy(avoidMain = it))
                        }
                        SwitchRow("Αποφυγή πολύ στενών δρόμων", "Λιγότερα αγροτικά, δρομάκια εξυπηρέτησης και πεζόδρομους", display.avoidNarrow) {
                            onDisplayChange(display.copy(avoidNarrow = it))
                        }
                        SwitchRow("Αποφυγή χωματόδρομων", "Όσο γίνεται χωρίς χώμα και χαλίκι (όχι στον τύπο «Χώμα»)", display.avoidUnpaved) {
                            onDisplayChange(display.copy(avoidUnpaved = it))
                        }
                    }
                    Group {
                        val nogoCount = app.motonav.routing.NoGoAreas.list.size
                        if (nogoCount > 0) {
                            LinkRow(
                                "Περιοχές αποφυγής: $nogoCount",
                                "Οι διαδρομές δεν περνάνε από εκεί (κόκκινοι κύκλοι στον χάρτη, πάτημα = σβήσιμο). Πάτα εδώ για να σβήσεις όλες.",
                                { app.motonav.routing.NoGoAreas.clear() },
                            )
                        }
                        SwitchRow("Χωματόδρομοι", "Καφέ διακεκομμένη γραμμή, από ζουμ 10", display.showDirt) {
                            onDisplayChange(display.copy(showDirt = it))
                        }
                        SwitchRow("Μέρη για μηχανόβιους", "Διάλεξε παρακάτω τι θες να φαίνεται, από ζουμ 11", display.showSpots) {
                            onDisplayChange(display.copy(showSpots = it))
                        }
                        // Μενού: ποια είδη φαίνονται στον χάρτη (πάτημα = εμφάνιση/απόκρυψη).
                        if (display.showSpots) {
                            Column(
                                Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                                verticalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                app.motonav.spots.SpotKind.entries.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                        row.forEach { k ->
                                            val on = k.name !in display.hiddenSpots
                                            TextChoice(
                                                "${k.emoji} ${k.label}",
                                                selected = on,
                                                onClick = {
                                                    onDisplayChange(display.copy(hiddenSpots = if (on) display.hiddenSpots + k.name else display.hiddenSpots - k.name))
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        ImportedPoisSection()
                        SwitchRow("Ανάγλυφο βουνών", "Σκίαση στα βουνά και κορυφές με υψόμετρο", display.showRelief) {
                            onDisplayChange(display.copy(showRelief = it))
                        }
                    }
                    Group {
                        LinkRow("Χάρτες χωρίς internet", "Κατέβασε την Ελλάδα ή μια περιοχή", onOpenOffline)
                    }
            Text("Live κίνηση (TomTom)", style = MotoType.title, color = c.base.text)
            Group {
                Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        "Δωρεάν κλειδί από my.tomtom.com/keys («My first API key»). Μένει μόνο σε αυτό το κινητό.",
                        style = MotoType.label,
                        color = c.base.textMuted,
                    )
                    KeyField(display.tomtomKey) { onDisplayChange(display.copy(tomtomKey = it)) }
                    Text(
                        if (display.tomtomKey.isBlank()) "Χωρίς κλειδί: διαδρομές offline, χρόνος χωρίς κίνηση."
                        else "Με κλειδί: «Χωρίς κίνηση» διαδρομή, χρόνος άφιξης με κίνηση, χρώματα στους δρόμους.",
                        style = MotoType.label,
                        color = if (display.tomtomKey.isBlank()) c.base.textMuted else c.base.success,
                    )
                }
                SwitchRow("Χρώματα κίνησης στον χάρτη", "Πράσινο, πορτοκαλί, κόκκινο", display.showTraffic) {
                    onDisplayChange(display.copy(showTraffic = it))
                }
            }

            Text("Ισοϋψείς καμπύλες (MapTiler)", style = MotoType.title, color = c.base.text)
            Group {
                Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        "Δωρεάν κλειδί από cloud.maptiler.com (API keys). Μένει μόνο σε αυτό το κινητό.",
                        style = MotoType.label,
                        color = c.base.textMuted,
                    )
                    KeyField(display.maptilerKey) { onDisplayChange(display.copy(maptilerKey = it)) }
                    Text(
                        if (display.maptilerKey.isBlank()) "Χωρίς κλειδί: μόνο σκίαση και κορυφές."
                        else "Με κλειδί: ισοϋψείς κάθε 10–100 m με υψόμετρο, από ζουμ 11.",
                        style = MotoType.label,
                        color = if (display.maptilerKey.isBlank()) c.base.textMuted else c.base.success,
                    )
                }
                SwitchRow("Ισοϋψείς στον χάρτη", null, display.showContours) {
                    onDisplayChange(display.copy(showContours = it))
                }
            }

                }
                "gauges" -> {
                    Group {
                        SwitchRow("Κλισιόμετρο", "Πάτημα πάνω του: μηδενισμός μέγιστων", display.showLean) {
                            onDisplayChange(display.copy(showLean = it))
                        }
                        SwitchRow("Πυξίδα", null, display.showCompass) { onDisplayChange(display.copy(showCompass = it)) }
                        SwitchRow("Υψόμετρο", null, display.showAltitude) { onDisplayChange(display.copy(showAltitude = it)) }
                    }
                }
                "cameras" -> {
            Group {
                SwitchRow("Κάμερες στον χάρτη", "Κίτρινη μπάρα 300 m πριν από κάμερα μπροστά σου", display.showCameras) {
                    onDisplayChange(display.copy(showCameras = it))
                }
                SwitchRow("Φωνή για κάμερες", "«Κάμερα σε 300 μέτρα, όριο 50»", display.cameraVoice) {
                    onDisplayChange(display.copy(cameraVoice = it))
                }
                InfoRow("Από πού", "OpenStreetMap (όσες έχουν καταχωριστεί) και όσες σημειώνεις εσύ με το κουμπί κάμερας")
            }
            Group {
                SwitchRow("Παλλόμενες άκρες οθόνης", "Κόκκινο πάνω: πέρασες το όριο · πορτοκαλί: κίνδυνος μπροστά · κίτρινο: κάμερα", display.edgeWarnings) {
                    onDisplayChange(display.copy(edgeWarnings = it))
                }
                if (display.edgeWarnings) {
                    SwitchRow("Φωτισμός όταν περνάς το όριο", "Το κόκκινο πάνω στην οθόνη· κλείσ' το αν σε ενοχλεί (μένει το κόκκινο νούμερο)", display.overspeedGlow) {
                        onDisplayChange(display.copy(overspeedGlow = it))
                    }
                }
                SwitchRow("Φωνή για όριο και καιρό", "«Όριο 50» όταν το περνάς, «Βροχή σε 8 χιλιόμετρα»", display.speedVoice) {
                    onDisplayChange(display.copy(speedVoice = it))
                }
                SwitchRow("Καρό σημαία πάνω από 120 km/h", "Άσπρα-μαύρα τετραγωνάκια στο πάνω μέρος της οθόνης", display.flagOver120) {
                    onDisplayChange(display.copy(flagOver120 = it))
                }
                SwitchRow("Βενζινάδικα στη διαδρομή", "Επόμενο βενζινάδικο στην πλοήγηση και μεγαλύτερο κενό χωρίς βενζίνη", display.showFuel) {
                    onDisplayChange(display.copy(showFuel = it))
                }
                InfoRow("Βενζινάδικα · OpenStreetMap (τελευταία φορά)", app.motonav.fuel.FuelDiag.osm)
                InfoRow("Βενζινάδικα · TomTom (τελευταία φορά)", app.motonav.fuel.FuelDiag.tomtom)
                InfoRow("Βενζινάδικα · αποτέλεσμα", app.motonav.fuel.FuelDiag.result)
            }

                }
                "voice" -> VoicePage()
                "backup" -> BackupPage()
                "keys" -> KeysPage()
                "about" -> {
            Group {
                InfoRow("Δεδομένα χάρτη", "© OpenStreetMap contributors, άδεια ODbL")
                InfoRow("Πλακίδια χάρτη", "OpenFreeMap")
                InfoRow("Ισοϋψείς", "© MapTiler, με δικό σου κλειδί")
                InfoRow("Ανάγλυφο", "Terrain Tiles (Mapzen, AWS Open Data), SRTM/NASA κ.ά.")
                InfoRow("Απεικόνιση χάρτη", "MapLibre Native (BSD)")
                InfoRow("Διαδρομές", "BRouter (MIT), offline στο κινητό")
                InfoRow("Αναζήτηση", "Photon (komoot) και Overpass API, από OpenStreetMap")
                InfoRow("Καιρός", "Open-Meteo")
                InfoRow("Κίνηση", "TomTom Routing και Traffic API, με δικό σου κλειδί")
            }

                }
                else -> {
                    Group {
                        LinkRow("Εμφάνιση", "Θέμα, χρώματα, βελάκι, γραμμή, μέγεθος, ώρα και μπαταρία", { onPage("look") })
                        LinkRow("Χάρτης", "Κίνηση, εναλλακτικές, χωματόδρομοι, ανάγλυφο, ισοϋψείς, offline", { onPage("map") })
                        LinkRow("Όργανα", "Κλισιόμετρο, πυξίδα, υψόμετρο", { onPage("gauges") })
                        LinkRow("Κάμερες και ειδοποιήσεις", "Κάμερες, άκρες οθόνης, φωνή ορίου, βενζίνη", { onPage("cameras") })
                        LinkRow("Φωνή πλοήγησης", "Φωνές ElevenLabs ή η φωνή του κινητού", { onPage("voice") })
                    }
                    Group {
                        LinkRow("Παρέα", "Βλέπετε ο ένας τον άλλον στον χάρτη · walkie-talkie", onOpenGroup)
                        LinkRow("Ημερολόγιο διαδρομών", "Καταγραφή με παύση, χιλιόμετρα, κλίσεις, GPX", onOpenTrips)
                        LinkRow("Σελιδοδείκτες και διαδρομές", "Λίστες μερών με χρώματα, αποθηκευμένες διαδρομές, GPX/KML", onOpenBookmarks)
                    }
                    Group {
                        LinkRow("Χειριστήριο τιμονιού", "Κουμπιά Bluetooth στο τιμόνι: ζουμ, κέντρο, φωνή, αναφορά", { onPage("keys") })
                        LinkRow("Αντίγραφο ασφαλείας", "Όλα σε ένα αρχείο, επαναφορά με ένα πάτημα", { onPage("backup") })
                    }
                    Group {
                        LinkRow("Πηγές και άδειες", "Από πού έρχονται χάρτες, διαδρομές, κίνηση", { onPage("about") })
                    }
                }
            }

            Text("Moto Nav $version · δωρεάν, open source", style = MotoType.label, color = c.base.textMuted)
            if (page == "main") {
                DonateButton()
                val updScope = androidx.compose.runtime.rememberCoroutineScope()
                var checking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                val avail = app.motonav.update.Updater.available
                if (avail != null) {
                    TextChoice(
                        app.motonav.update.Updater.downloading ?: "Νέα έκδοση ${avail.tag} · Εγκατάσταση",
                        selected = true,
                        onClick = { updScope.launch { app.motonav.update.Updater.install(context) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    app.motonav.update.Updater.error?.let { Text(it, style = MotoType.label, color = c.base.warning) }
                } else {
                    TextChoice(
                        checking ?: "Έλεγχος για νέα έκδοση",
                        selected = false,
                        onClick = {
                            checking = "Ελέγχω…"
                            updScope.launch {
                                val has = app.motonav.update.Updater.check(context, force = true)
                                checking = if (has) null else "Έχεις την τελευταία έκδοση"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.heightIn(min = Space.xxl))
        }
    }
}

@Composable
internal fun Group(content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Surface(shape = RoundedCornerShape(Radius.lg), color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
internal fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(color = c.base.surface, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MotoType.body, color = c.base.text)
                Text(subtitle, style = MotoType.label, color = c.base.textMuted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.base.textMuted)
        }
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    Surface(color = c.base.surface, onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MotoType.body, color = c.base.text)
                if (subtitle != null) Text(subtitle, style = MotoType.label, color = c.base.textMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent,
                    checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.base.textMuted,
                    uncheckedTrackColor = c.base.surface3,
                    uncheckedBorderColor = c.base.borderInput,
                ),
            )
        }
    }
}

/** Πεδίο για κλειδί API: κρυμμένο, με «Δείξε». */
@Composable
internal fun KeyField(value: String, onChange: (String) -> Unit) {
    val c = LocalAppColors.current
    var keyText by remember { mutableStateOf(value) }
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = keyText,
        onValueChange = {
            keyText = it
            onChange(it.trim())
        },
        singleLine = true,
        placeholder = { Text("Επικόλλησε εδώ το κλειδί", style = MotoType.body, color = c.base.textMuted) },
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { reveal = !reveal }) {
                Text(if (reveal) "Κρύψε" else "Δείξε", style = MotoType.label, color = c.accent)
            }
        },
        textStyle = MotoType.body.copy(color = c.base.text),
        shape = RoundedCornerShape(Radius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.base.borderInput,
            cursorColor = c.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun InfoRow(title: String, value: String) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm)) {
        Text(title, style = MotoType.label, color = c.base.textMuted)
        Text(value, style = MotoType.body, color = c.base.text)
    }
}


/** Ρυθμίσεις → Χάρτης: «Δικά σου σημεία» (εισαγωγή αρχείων POI, εμφάνιση/απόκρυψη, διαγραφή). */
@androidx.compose.runtime.Composable
private fun ImportedPoisSection() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            var cats = 0
            var pts = 0
            var failed = 0
            uris.forEach { u ->
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { app.motonav.data.ImportedPois.import(context, u) }.getOrNull()
                }
                if (r == null || r.second == 0) failed++ else { cats += r.first; pts += r.second }
            }
            busy = false
            val msg = when {
                pts > 0 -> "Μπήκαν $pts σημεία σε $cats κατηγορί" + (if (cats == 1) "α" else "ες") + (if (failed > 0) " · $failed αρχεία χωρίς σημεία" else "")
                else -> "Δεν βρέθηκαν σημεία. Δέχεται GPX, KML/KMZ, CSV, OV2, GeoJSON ή ZIP με αυτά."
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val c = LocalAppColors.current
    Column(Modifier.padding(horizontal = Space.lg, vertical = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("Δικά σου σημεία", style = MotoType.body, color = c.base.text)
        Text(
            "Αρχεία σημείων από άλλες πηγές (π.χ. 4GPS, Garmin, TomTom, Google My Maps). Κάθε αρχείο γίνεται κατηγορία στον χάρτη και στην αναζήτηση.",
            style = MotoType.label, color = c.base.textMuted,
        )
        app.motonav.data.ImportedPois.collections.forEach { col ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextChoice(
                    "● ${col.name} (${col.count})",
                    selected = col.visible,
                    onClick = { app.motonav.data.ImportedPois.setVisible(col.id, !col.visible) },
                    modifier = Modifier.weight(1f),
                )
                TextChoice("✕", selected = false, onClick = { app.motonav.data.ImportedPois.delete(col.id) })
            }
        }
        TextChoice(
            if (busy) "Εισαγωγή…" else "+ Εισαγωγή σημείων (GPX, KML, CSV, OV2, ZIP)",
            selected = false,
            onClick = { if (!busy) pick.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
