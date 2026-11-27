package app.motonav.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.motonav.R
import app.motonav.media.MediaHub
import app.motonav.media.MyApps
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Κατάσταση του pill μουσικής πάνω στο κέντρο του χάρτη. */
object MediaPillState {
    /** Η εφαρμογή που άνοιξες από το συρτάρι (π.χ. Spotify). */
    var pkg by mutableStateOf<String?>(null)
    var expanded by mutableStateOf(true)
    /** Το έκλεισες με ✕: ξαναβγαίνει όταν αρχίσει να παίζει κάτι. */
    var hidden by mutableStateOf(false)
    /** Αλλάζει σε κάθε άγγιγμα του pill, για να μετράει από την αρχή ο χρόνος ως το κρύψιμο. */
    var touch by mutableIntStateOf(0)
    var justPlayed by mutableStateOf(false)

    fun show(pkg: String) {
        this.pkg = pkg
        MediaHub.preferredPkg = pkg
        hidden = false
        expanded = true
        touch++
    }

    /** Πάτημα στον χάρτη: το pill ανεβαίνει και μένει μόνο το κουμπάκι. */
    fun onMapTap() { if (expanded) expanded = false }
}

/** Λεπτό pill μουσικής πάνω στο κέντρο. Ανεβαίνει μόνο του όταν πατήσεις play και αφήνει ένα μικρό κουμπάκι. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaPill(docked: Boolean = false) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    LifecycleResumeEffect(Unit) {
        MediaHub.start(context)
        onPauseOrDispose { }
    }

    val now = MediaHub.now
    val st = MediaPillState
    // Ξαναβγαίνει μόνο του όταν αρχίσει να παίζει μουσική.
    LaunchedEffect(now?.playing) { if (now?.playing == true) st.hidden = false }
    val pkg = now?.pkg ?: st.pkg
    val visible = pkg != null && !st.hidden
    val playing = now?.playing == true && (st.pkg == null || now.pkg == pkg)

    // Παίζει: κρύψου (λίγο μετά το play, ή μετά από λίγα δευτερόλεπτα χωρίς άγγιγμα).
    LaunchedEffect(st.expanded, playing, st.touch) {
        if (st.expanded && playing) {
            delay(if (st.justPlayed) 1_100 else 5_000)
            st.justPlayed = false
            st.expanded = false
        }
    }
    // Πάτησες play αλλά η εφαρμογή δεν ξύπνησε: άνοιξέ την για να ξεκινήσει.
    var waitingFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(waitingFor) {
        val w = waitingFor ?: return@LaunchedEffect
        delay(2_500)
        if (MediaHub.now?.pkg != w) MyApps.open(context, w)
        waitingFor = null
    }

    val label = remember(pkg) { pkg?.let { MyApps.label(context, it) } ?: "" }
    val appIcon = remember(pkg) { pkg?.let { MyApps.icon(context, it)?.asImageBitmap() } }
    val art = remember(now?.art) { now?.art?.asImageBitmap() }

    // docked: μέσα στη στήλη των κουμπιών πάνω δεξιά (οριζόντιο), αλλιώς πάνω στο κέντρο.
    val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (docked) content()
        else Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(top = 2.dp),
            contentAlignment = Alignment.TopCenter,
        ) { content() }
    }
    wrap {
        AnimatedVisibility(
            visible = visible && st.expanded,
            // Στη στήλη των κουμπιών «μαζεύεται» προς τα πάνω· στο κέντρο γλιστράει έξω από την οθόνη.
            enter = if (docked) expandVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) + fadeIn(tween(180))
                else slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(tween(180)),
            exit = if (docked) shrinkVertically(tween(320), shrinkTowards = Alignment.Top) + fadeOut(tween(240))
                else slideOutVertically(tween(320)) { -it * 2 } + fadeOut(tween(260)),
        ) {
            Surface(
                shape = CircleShape,
                color = c.base.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, c.outline),
                shadowElevation = 6.dp,
                modifier = Modifier.height(46.dp).widthIn(max = 380.dp),
            ) {
                Row(
                    Modifier.padding(start = 5.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Εξώφυλλο (ή εικονίδιο της εφαρμογής). Πάτημα = άνοιγμα της εφαρμογής.
                    val img = art ?: appIcon
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c.base.surface2)
                            .clickable { pkg?.let { MyApps.open(context, it) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (img != null) Image(img, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(
                        Modifier.padding(horizontal = Space.sm).widthIn(max = 170.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { pkg?.let { MyApps.open(context, it) } },
                    ) {
                        Text(
                            now?.title?.takeIf { it.isNotBlank() && now.pkg == pkg } ?: label,
                            style = MotoType.label.copy(fontSize = 13.sp, lineHeight = 15.sp),
                            color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val sub = now?.artist?.takeIf { it.isNotBlank() && now.pkg == pkg } ?: if (now == null) "Πάτα ▶" else ""
                        if (sub.isNotBlank()) Text(
                            sub, fontSize = 11.sp, lineHeight = 13.sp, color = c.base.textMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PillButton(ImageVector.vectorResource(R.drawable.ic_skip_prev), "Προηγούμενο") {
                        st.touch++; MediaHub.previous(context, pkg)
                    }
                    Surface(
                        shape = CircleShape, color = c.accent, contentColor = c.onAccent,
                        modifier = Modifier.size(38.dp),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            st.touch++
                            if (!playing) {
                                st.justPlayed = true
                                if (now == null || now.pkg != pkg) waitingFor = pkg
                            }
                            MediaHub.playPause(context, pkg)
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                ImageVector.vectorResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                                if (playing) "Παύση" else "Αναπαραγωγή", Modifier.size(24.dp),
                            )
                        }
                    }
                    PillButton(ImageVector.vectorResource(R.drawable.ic_skip_next), "Επόμενο") {
                        st.touch++; MediaHub.next(context, pkg)
                    }
                    // ✕ μόνο όταν δεν παίζει, για να το κλείσεις τελείως.
                    if (!playing) Box(
                        Modifier.size(28.dp).clip(CircleShape).clickable { st.hidden = true; st.pkg = null },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", fontSize = 13.sp, color = c.base.textMuted) }
                }
            }
        }
    }
}

/** Το μικρό μακρόστενο κουμπάκι, τέρμα πάνω στο κέντρο (κάτω από την κάμερα, αν είναι εκεί).
 *  Μεγάλη περιοχή αγγίγματος γύρω και κάτω του: δεν χρειάζεται να το πετύχεις ακριβώς. */
@Composable
fun MediaHandle() {
    val c = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val st = MediaPillState
    val now = MediaHub.now
    val pkg = now?.pkg ?: st.pkg
    val visible = pkg != null && !st.hidden
    val playing = now?.playing == true && (st.pkg == null || now.pkg == pkg)
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible && !st.expanded,
            enter = fadeIn(tween(220, delayMillis = 180)) + slideInVertically(tween(220, delayMillis = 180)) { -it },
            exit = fadeOut(tween(120)),
        ) {
            Box(
                Modifier
                    .size(width = 170.dp, height = 56.dp)
                    .clickable(remember { MutableInteractionSource() }, null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        st.expanded = true; st.touch++
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier.padding(top = 3.dp).size(width = 46.dp, height = 6.dp).clip(CircleShape)
                        .background(if (playing) c.accent.copy(alpha = 0.95f) else c.base.textMuted.copy(alpha = 0.8f)),
                )
            }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(22.dp), tint = c.base.text) }
}

/** Συρτάρι με τις εφαρμογές που πρόσθεσες (Spotify κ.λπ.). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsPanel(landscape: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    LaunchedEffect(Unit) { MyApps.load(context) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(MediaHub.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        access = MediaHub.hasAccess(context)
        if (access) MediaHub.start(context)
        onPauseOrDispose { }
    }
    val mediaPkgs = remember(MyApps.list.toList()) { MyApps.list.filter { MyApps.isMedia(context, it) }.toSet() }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
    ) {
        Surface(
            shape = if (landscape) RoundedCornerShape(Radius.lg) else RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
            color = c.base.surface,
            border = BorderStroke(1.dp, c.outline),
            modifier = (if (landscape) {
                Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(Space.sm).width(420.dp).fillMaxHeight()
            } else {
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 640.dp).safeDrawingPadding()
            }).clickable(remember { MutableInteractionSource() }, null) { },
        ) {
            Column {
                Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Surface(shape = CircleShape, color = c.base.surface2, onClick = { if (picking) picking = false else onClose() }, modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 34.sp, color = c.base.text, fontWeight = FontWeight.Bold) }
                    }
                    Text(
                        if (picking) "Πρόσθεσε εφαρμογή" else "Εφαρμογές",
                        style = MotoType.title.copy(fontWeight = FontWeight.Bold), color = c.base.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (!picking && MyApps.list.isNotEmpty()) TextChoice(if (editing) "Τέλος" else "Αλλαγή", selected = editing, onClick = { editing = !editing })
                }
                HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                if (picking) {
                    val all by produceState<List<MyApps.App>?>(null) { value = withContext(Dispatchers.IO) { MyApps.installed(context) } }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = Space.xs)) {
                        if (all == null) Text("Φόρτωση…", Modifier.padding(Space.lg), color = c.base.textMuted)
                        all?.forEach { app ->
                            val added = app.pkg in MyApps.list
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (added) MyApps.remove(context, app.pkg) else MyApps.add(context, app.pkg)
                                }.padding(horizontal = Space.lg, vertical = Space.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.md),
                            ) {
                                AppIcon(app.pkg, 40)
                                Text(app.label, style = MotoType.body, color = c.base.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (added) "✓" else "+", fontSize = 22.sp, color = if (added) c.accent else c.base.textMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        if (mediaPkgs.isNotEmpty() && !access) {
                            Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface2, border = BorderStroke(1.dp, c.outline)) {
                                Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    Text("Για το pill μουσικής (▶ ⏭ πάνω στον χάρτη) δώσε στο Streetwolf «Πρόσβαση στις ειδοποιήσεις». Μία φορά.", style = MotoType.label, color = c.base.text)
                                    TextChoice("Άνοιγμα ρύθμισης", selected = true, onClick = {
                                        try { context.startActivity(MediaHub.accessIntent()) } catch (_: Exception) { }
                                    })
                                }
                            }
                        }
                        val tiles = MyApps.list.toList() + listOf("+")
                        tiles.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { pkg ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                        if (pkg == "+") AddTile { picking = true; editing = false }
                                        else AppTile(
                                            pkg, editing,
                                            onTap = {
                                                when {
                                                    editing -> MyApps.remove(context, pkg)
                                                    pkg in mediaPkgs -> {
                                                        MediaPillState.show(pkg)
                                                        MediaHub.start(context)
                                                        if (access) onClose()
                                                        else try { context.startActivity(MediaHub.accessIntent()) } catch (_: Exception) { }
                                                    }
                                                    else -> { MyApps.open(context, pkg); onClose() }
                                                }
                                            },
                                            onLongPress = { editing = true },
                                        )
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (MyApps.list.isEmpty()) Text(
                            "Πάτα + και διάλεξε εφαρμογές (π.χ. Spotify). Οι εφαρμογές μουσικής ανοίγουν pill πάνω στον χάρτη με ▶ ⏭.",
                            style = MotoType.label, color = c.base.textMuted, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String, sizeDp: Int) {
    val context = LocalContext.current
    val bmp = remember(pkg) { MyApps.icon(context, pkg)?.asImageBitmap() }
    if (bmp != null) Image(bmp, null, Modifier.size(sizeDp.dp))
    else Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape(Radius.sm)).background(LocalAppColors.current.base.surface3))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(pkg: String, editing: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val label = remember(pkg) { MyApps.label(context, pkg) }
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(Radius.md))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(pkg, 56)
            if (editing) Box(
                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(c.base.danger),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 12.sp, color = c.base.onDanger, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(Space.xs))
        Text(label, fontSize = 12.sp, color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(Radius.md)).clickable(onClick = onClick).padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(Radius.md)).background(c.base.surface2),
            contentAlignment = Alignment.Center,
        ) { Text("+", fontSize = 30.sp, color = c.accent, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(Space.xs))
        Text("Προσθήκη", fontSize = 12.sp, color = c.base.textMuted, maxLines = 1)
    }
}
