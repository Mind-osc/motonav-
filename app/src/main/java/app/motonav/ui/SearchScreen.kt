@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import app.motonav.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.search.Category
import app.motonav.search.Place
import app.motonav.search.PlaceSearch
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.Touch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private const val TYPING_LOCK_KMH = 10f

/** S02 Αναζήτηση. Γράφεις ή διαλέγεις κατηγορία, πατάς αποτέλεσμα, γίνεται προορισμός. */
@Composable
fun SearchScreen(
    near: LatLon?,
    speedKmh: Float?,
    recents: List<Place>,
    onPick: (Place) -> Unit,
    onBack: () -> Unit,
    tomtomKey: String = "",
    /** true: το πληκτρολόγιο ανοίγει μόλις ανοίξει η αναζήτηση. false: μόνο όταν πατήσεις στο πεδίο. */
    autoKeyboard: Boolean = false,
    home: Place? = null,
    work: Place? = null,
    favorites: List<Place> = emptyList(),
    onSetHome: (Place) -> Unit = {},
    onSetWork: (Place) -> Unit = {},
    onToggleFavorite: (Place) -> Unit = {},
    /** true: διαλέγεις ενδιάμεση στάση, όχι προορισμό. */
    stopMode: Boolean = false,
    /** Κυκλική βόλτα τόσων ωρών από εδώ που είσαι. */
    onLoop: (Int) -> Unit = {},
    /** Οι τελευταίες διαδρομές που ξεκίνησες: πάτημα = ίδια διαδρομή ξανά. */
    history: List<app.motonav.nav.NavSession.HistoryEntry> = emptyList(),
    onReplay: (app.motonav.nav.NavSession.HistoryEntry) -> Unit = {},
    /** Διαδρομές που κράτησες με όνομα: πάτημα = «Ακολούθησε» ακριβώς την ίδια. */
    savedRoutes: List<app.motonav.trips.SavedRoute> = emptyList(),
    onFollowSaved: (app.motonav.trips.SavedRoute) -> Unit = {},
) {
    val c = LocalAppColors.current
    val search = remember(tomtomKey) { PlaceSearch(tomtomKey) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    // Πλήρης αναζήτηση (και με Nominatim) μόνο με «Αναζήτηση» στο πληκτρολόγιο ή με φωνή:
    // το Nominatim δεν επιτρέπει αναζήτηση σε κάθε γράμμα.
    var thoroughFor by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<Category?>(null) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchedOnce by remember { mutableStateOf(false) }
    // Το μέρος που διάλεξες: μπαίνει στο πεδίο για να το δεις/διορθώσεις, και μετά «Πάμε».
    var selected by remember { mutableStateOf<Place?>(null) }
    var selectedText by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val fieldInteraction = remember { MutableInteractionSource() }

    val choose: (Place) -> Unit = { place ->
        val text = listOf(place.name, place.detail).filter { it.isNotBlank() }.joinToString(", ")
        selected = place
        selectedText = text
        query = text
        keyboard?.hide()
    }

    // Φωνητική αναζήτηση (αναγνώριση ομιλίας του κινητού, ελληνικά). Δουλεύει και εν κινήσει.
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            category = null
            selected = null
            query = text
            thoroughFor = text
        }
    }
    val startVoice = {
        voiceError = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Πες διεύθυνση ή μέρος")
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            voiceError = "Δεν βρέθηκε αναγνώριση ομιλίας στο κινητό (Google)."
        }
    }

    val moving = (speedKmh ?: 0f) > TYPING_LOCK_KMH

    // Πληκτρολόγιο: εστίαση και ρητό άνοιγμα. Μόνο η εστίαση δεν το ανοίγει πάντα,
    // και μετά ένα πάτημα στο ήδη εστιασμένο πεδίο δεν έκανε τίποτα.
    LaunchedEffect(Unit) {
        if (!moving && autoKeyboard) {
            delay(250)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(fieldInteraction) {
        fieldInteraction.interactions.collect { i ->
            if (i is PressInteraction.Release && !moving) keyboard?.show()
        }
    }

    // Αναζήτηση κειμένου: περιμένει να σταματήσεις να γράφεις 0,4 s.
    LaunchedEffect(query, thoroughFor) {
        if (category != null) return@LaunchedEffect
        val thorough = thoroughFor != null && thoroughFor == query
        if (selected != null && query == selectedText) return@LaunchedEffect
        if (query.trim().length < 2) {
            results = emptyList(); error = null; loading = false; searchedOnce = false
            return@LaunchedEffect
        }
        if (!thorough) delay(400)
        loading = true; error = null
        try {
            results = search.text(query, near, thorough)
            searchedOnce = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = app.motonav.net.friendlyError(e)
        } finally {
            loading = false
        }
    }

    // Κατηγορία κοντά σου.
    LaunchedEffect(category) {
        val cat = category
        if (cat == null) {
            if (query.isEmpty()) { results = emptyList(); searchedOnce = false; error = null }
            return@LaunchedEffect
        }
        val here = near
        if (here == null) {
            error = "Περιμένω σήμα GPS για να ψάξω κοντά σου."
            return@LaunchedEffect
        }
        keyboard?.hide()
        loading = true; error = null; results = emptyList()
        try {
            results = search.category(cat, here)
            searchedOnce = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = app.motonav.net.friendlyError(e)
        } finally {
            loading = false
        }
    }

    val imeOpen = WindowInsets.isImeVisible
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val idle = selected == null && query.isEmpty() && category == null

    // Φύλλο που ανεβαίνει από κάτω· από πάνω φαίνεται ο χάρτης (σκοτεινιασμένος). Πάτημα εκεί = κλείσιμο.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onBack),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = c.base.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .animateContentSize(
                    // Ελατήριο (όχι σταθερός χρόνος) και άγκυρα κάτω: το φύλλο «ανεβαίνει» ζωντανά,
                    // η μπάρα μένει στη θέση της και το περιεχόμενο δεν κόβεται από πάνω.
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                    alignment = Alignment.BottomCenter,
                ),
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = Space.md, end = Space.md, bottom = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                // λαβή
                Box(Modifier.fillMaxWidth().padding(top = Space.sm), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(c.base.textMuted.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
                }

                // ---- Πάνω μέρος: αποτελέσματα / επιλογή / πρόσφατα (με όριο ύψους, για να φαίνεται ο χάρτης)
                val maxList = if (imeOpen) screenH * 0.32f else screenH * 0.5f
                selected?.let { place -> SelectedCard(place, near, stopMode, home, work, favorites, onPick, onSetHome, onSetWork, onToggleFavorite) {
                    selected = null
                    if (!moving) { runCatching { focus.requestFocus() }; keyboard?.show() }
                } }
                voiceError?.let { Hint(it) }
                if (stopMode) Hint("Διάλεξε ενδιάμεση στάση.")
                when {
                    loading -> Hint("Ψάχνω…")
                    error != null -> Hint(error!!)
                    moving && idle -> Hint("Όσο κινείσαι: κατηγορία ή πρόσφατο. Για να γράψεις, σταμάτα.")
                    selected == null && searchedOnce && results.isEmpty() && !idle -> Hint("Δεν βρέθηκε τίποτα.")
                }
                // Όσα έχεις ψάξει ή πάει ξανά και ταιριάζουν με ό,τι γράφεις: πρώτα, έως 2, με ρολόι.
                val past = if (selected == null && category == null && query.trim().length >= 2) {
                    val nq = app.motonav.search.PlaceParsers.normalize(query)
                    (favorites + recents).distinctBy { "${it.lat}|${it.lon}" }.filter { p ->
                        app.motonav.search.PlaceParsers.textScore(query, p.name) <= 2 ||
                            app.motonav.search.PlaceParsers.normalize(p.name + " " + p.detail).contains(nq)
                    }.take(2)
                } else emptyList()
                if (selected == null && !idle && (results.isNotEmpty() || past.isNotEmpty())) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxList)) {
                        items(past, key = { "past|${it.lat}|${it.lon}" }) { place ->
                            PlaceRow(place, near, modifier = Modifier.animateItem(), icon = ImageVector.vectorResource(R.drawable.ic_history)) { onPick(place) }
                        }
                        items(results.filter { r -> past.none { Geo.distance(it.latLon, r.latLon) < 60 } }, key = { "${it.name}|${it.lat}|${it.lon}" }) { place ->
                            PlaceRow(place, near, modifier = Modifier.animateItem()) { choose(place) }
                        }
                    }
                }
                // Χωρίς πληκτρολόγιο: βόλτα, κατηγορίες, αγαπημένα, πρόσφατα.
                if (idle && !imeOpen) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxList)) {
                        if (!stopMode) item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), contentPadding = PaddingValues(vertical = Space.xs)) {
                                item { Text("Βόλτα", style = MotoType.label, color = c.base.textMuted, modifier = Modifier.padding(end = Space.xs, top = 10.dp)) }
                                items(listOf(1, 2, 3, 4)) { h -> Chip(if (h == 1) "1 ώρα" else "$h ώρες", onClick = { onLoop(h) }) }
                            }
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), contentPadding = PaddingValues(vertical = Space.xs)) {
                                items(Category.entries) { cat -> Chip(cat.label, selected = category == cat, onClick = { query = ""; category = cat }) }
                            }
                        }
                        if (!stopMode && savedRoutes.isNotEmpty()) {
                            item { Section("Αποθηκευμένες διαδρομές") }
                            items(savedRoutes, key = { "saved|${it.id}" }) { r ->
                                PlaceRow(
                                    Place(r.name, String.format(java.util.Locale("el", "GR"), "%.1f km · ακολουθείς την ίδια", r.lengthM / 1000), 0.0, 0.0),
                                    null,
                                    icon = ImageVector.vectorResource(R.drawable.ic_history),
                                ) { onFollowSaved(r) }
                            }
                        }
                        if (!stopMode && history.isNotEmpty()) {
                            item { Section("Πρόσφατες διαδρομές") }
                            items(history.take(5)) { h ->
                                val label = h.name ?: String.format(java.util.Locale.US, "%.4f, %.4f", h.dest.lat, h.dest.lon)
                                val type = when (h.type) {
                                    app.motonav.routing.RouteType.TWISTY -> "Στροφές"
                                    app.motonav.routing.RouteType.DIRT -> "Χώμα"
                                    app.motonav.routing.RouteType.TRAFFIC -> "Κίνηση"
                                    else -> "Γρήγορη"
                                }
                                val via = if (h.stops.isNotEmpty()) " · ${h.stops.size} στάσεις" else ""
                                PlaceRow(
                                    Place(label, "$type$via", h.dest.lat, h.dest.lon),
                                    near,
                                    icon = ImageVector.vectorResource(R.drawable.ic_history),
                                ) { onReplay(h) }
                            }
                        }
                        if (favorites.isNotEmpty()) {
                            // Σελιδοδείκτες ανά λίστα, με το χρώμα της.
                            favorites.groupBy { it.listName }.forEach { (listName, ps) ->
                                val col = ps.firstOrNull { it.color != 0 }?.color ?: 0
                                item(key = "list|$listName") { ListSection(listName, col) }
                                items(ps, key = { "fav|$listName|${it.lat}|${it.lon}" }) { place ->
                                    PlaceRow(place, near, onRemove = { onToggleFavorite(place) }) { onPick(place) }
                                }
                            }
                        }
                        if (recents.isNotEmpty()) {
                            item { Section("Πρόσφατα") }
                            // Πρόσφατο: ένα πάτημα και βγαίνει κατευθείαν η διαδρομή (χωρίς «Πάμε»).
                            items(recents) { place -> PlaceRow(place, near) { onPick(place) } }
                        }
                    }
                }
                // Κατηγορία ανοιχτή: οι κατηγορίες μένουν για να αλλάξεις.
                if (category != null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        items(Category.entries) { cat ->
                            Chip(cat.label, selected = category == cat, onClick = { query = ""; category = if (category == cat) null else cat })
                        }
                    }
                }

                // ---- Μπάρα αναζήτησης (πάντα ακριβώς πάνω από το πληκτρολόγιο, μαζί με Σπίτι · Δουλειά)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(c.base.surface2, RoundedCornerShape(26.dp))
                        .border(1.dp, if (imeOpen) c.accent.copy(alpha = 0.7f) else c.outline.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Πίσω στον χάρτη", tint = c.base.textMuted)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                if (moving) "Σταμάτα για να γράψεις" else if (stopMode) "Στάση: διεύθυνση ή μέρος" else "Πού πάμε;",
                                style = MotoType.body,
                                color = c.base.textMuted,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                if (!moving) {
                                    query = it
                                    category = null
                                    if (selected != null && it != selectedText) selected = null
                                }
                            },
                            interactionSource = fieldInteraction,
                            readOnly = moving,
                            singleLine = true,
                            textStyle = MotoType.body.copy(color = c.base.text),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); thoroughFor = query }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; results = emptyList(); selected = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Καθαρισμός", tint = c.base.textMuted)
                        }
                    }
                    IconButton(onClick = startVoice) {
                        Icon(ImageVector.vectorResource(R.drawable.ic_mic), contentDescription = "Φωνητική αναζήτηση", tint = c.accent)
                    }
                }
                if (selected == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                        listOf(Triple("Σπίτι", home, Icons.Filled.Home), Triple("Δουλειά", work, Icons.Filled.Place)).forEach { (label, place, icon) ->
                            Chip(
                                text = label,
                                icon = icon,
                                dim = place == null,
                                onClick = {
                                    if (place != null) onPick(place.copy(name = label))
                                    else voiceError = "Για να ορίσεις «$label»: ψάξε τη διεύθυνση, πάτα την και μετά «$label»."
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedCard(
    place: Place,
    near: LatLon?,
    stopMode: Boolean,
    home: Place?,
    work: Place?,
    favorites: List<Place>,
    onPick: (Place) -> Unit,
    onSetHome: (Place) -> Unit,
    onSetWork: (Place) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onEdit: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MotoType.title, color = c.base.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(place.detail.takeIf { it.isNotBlank() }, near?.let { distanceLabel(Geo.distance(it, place.latLon)) }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MotoType.label, color = c.base.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val isFav = favorites.any { kotlin.math.abs(it.lat - place.lat) < 2e-4 && kotlin.math.abs(it.lon - place.lon) < 2e-4 }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Chip("Σπίτι", selected = home?.latLon == place.latLon, onClick = { onSetHome(place) })
            Chip("Δουλειά", selected = work?.latLon == place.latLon, onClick = { onSetWork(place) })
            Chip(if (isFav) "★" else "☆", selected = isFav, onClick = { onToggleFavorite(place) })
            Spacer(Modifier.weight(1f))
            Chip("Διόρθωση", onClick = onEdit)
        }
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = c.accent,
            onClick = { onPick(place) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (stopMode) "Βάλε στάση" else "Πάμε", style = MotoType.title.copy(fontSize = 18.sp), color = c.onAccent)
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dim: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) c.accent.copy(alpha = 0.18f) else c.base.surface2,
        border = BorderStroke(1.dp, if (selected) c.accent else c.outline.copy(alpha = 0.5f)),
        onClick = onClick,
        modifier = modifier.height(40.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (dim) c.base.textMuted else c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MotoType.label.copy(fontSize = 15.sp), color = if (dim) c.base.textMuted else c.base.text, maxLines = 1)
        }
    }
}

@Composable
private fun Section(text: String) {
    val c = LocalAppColors.current
    Text(text, style = MotoType.label, color = c.base.textMuted, modifier = Modifier.padding(top = Space.sm, bottom = 2.dp))
}

@Composable
private fun ListSection(name: String, color: Int) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.sm, bottom = 2.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(10.dp)
                .background(if (color != 0) androidx.compose.ui.graphics.Color(color) else c.accent, androidx.compose.foundation.shape.CircleShape),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Text(name, style = MotoType.label, color = c.base.textMuted)
    }
}

@Composable
private fun Hint(text: String) {
    val c = LocalAppColors.current
    Text(text, style = MotoType.body, color = c.base.textMuted, modifier = Modifier.padding(vertical = Space.xs))
}

@Composable
private fun PlaceRow(place: Place, near: LatLon?, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = Space.xs, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon ?: Icons.Filled.LocationOn, contentDescription = null, tint = if (icon != null) c.accent else c.base.textMuted.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MotoType.body, color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place.detail.isNotEmpty()) {
                Text(place.detail, style = MotoType.label, color = c.base.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (near != null) {
            Spacer(Modifier.width(Space.sm))
            Text(distanceLabel(Geo.distance(near, place.latLon)), style = MotoType.label, color = c.base.textMuted)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Clear, contentDescription = "Βγάλε από τα αγαπημένα", tint = c.base.textMuted)
            }
        }
    }
}

private fun distanceLabel(m: Double): String = when {
    m < 1000 -> "${(m / 10).roundToInt() * 10} m"
    m < 100_000 -> String.format(Locale("el", "GR"), "%.1f km", m / 1000)
    else -> "${(m / 1000).roundToInt()} km"
}
