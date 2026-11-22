package app.motonav.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.motonav.data.BookmarkFiles
import app.motonav.data.SettingsRepo
import app.motonav.search.Place
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import app.motonav.ui.theme.Touch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Χρώματα για λίστες σελιδοδεικτών (ίδια σειρά: η νέα λίστα παίρνει το επόμενο). */
val BOOKMARK_COLORS = listOf(
    0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
    0xFF32ADE6.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D55.toInt(), 0xFFA2845E.toInt(),
)

/**
 * Ρυθμίσεις → Σελιδοδείκτες: λίστες με χρώμα (π.χ. «Βόλτες Πήλιο», «Καφέ για στάση»),
 * εισαγωγή από GPX/KML, κοινοποίηση λίστας ως GPX, πάτημα σε μέρος = διαδρομή εκεί.
 */
@Composable
fun BookmarksScreen(onGo: (Place) -> Unit, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val repo = remember { SettingsRepo(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val favorites by repo.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val lists = remember(favorites) { favorites.groupBy { it.listName } }
    var open by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    val name = BookmarkFiles.title(text)
                        ?: (uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.take(30) ?: "Εισαγωγή")
                    name to BookmarkFiles.parse(text)
                }
            }
            r.onSuccess { (name, places) ->
                if (places.isEmpty()) {
                    message = "Δεν βρήκα σημεία στο αρχείο (θέλει GPX με wpt ή KML με Placemark)."
                } else {
                    val col = BOOKMARK_COLORS[lists.size % BOOKMARK_COLORS.size]
                    repo.importFavorites(name, col, places)
                    message = "Μπήκαν ${places.size} σημεία στη λίστα «$name»."
                    open = name
                }
            }.onFailure { message = "Δεν άνοιξε το αρχείο: ${it.message}" }
        }
    }

    fun share(name: String, places: List<Place>) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "list" }
            val f = File(dir, "$safe.gpx")
            f.writeText(BookmarkFiles.toGpx(name, places))
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("application/gpx+xml")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .putExtra(android.content.Intent.EXTRA_SUBJECT, name)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(android.content.Intent.createChooser(send, "Κοινοποίηση λίστας"))
        }.onFailure { message = "Δεν έγινε η κοινοποίηση: ${it.message}" }
    }

    androidx.activity.compose.BackHandler(enabled = open != null) { open = null; renaming = null; confirmDelete = null }
    Box(Modifier.fillMaxSize().background(c.base.bg)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RideIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", { if (open != null) open = null else onBack() }, size = Touch.min)
                Spacer(Modifier.width(Space.lg))
                Text(open ?: "Σελιδοδείκτες", style = MotoType.street, color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            message?.let { Text(it, style = MotoType.label, color = c.base.textMuted) }

            val current = open
            if (current == null) {
                TextChoice("Εισαγωγή από GPX / KML", selected = false, onClick = { message = null; importFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth())
                if (lists.isEmpty()) {
                    Text(
                        "Δεν έχεις ακόμα σελιδοδείκτες. Σε έναν προορισμό πάτα ★ → «Σε λίστα…» για να τον βάλεις σε λίστα, π.χ. «Βόλτες Πήλιο».",
                        style = MotoType.body,
                        color = c.base.textMuted,
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items(lists.keys.toList(), key = { it }) { name ->
                        val ps = lists.getValue(name)
                        val col = ps.firstOrNull { it.color != 0 }?.color ?: 0
                        Surface(
                            shape = RoundedCornerShape(Radius.md),
                            color = c.base.surface,
                            border = BorderStroke(1.dp, c.outline),
                            onClick = { open = name; message = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        ) {
                            Row(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).background(if (col != 0) Color(col) else c.accent, CircleShape))
                                Spacer(Modifier.width(Space.md))
                                Text(name, style = MotoType.body, color = c.base.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${ps.size}", style = MotoType.label, color = c.base.textMuted)
                            }
                        }
                    }
                }
            } else {
                val ps = lists[current].orEmpty()
                val col = ps.firstOrNull { it.color != 0 }?.color ?: 0
                // Χρώμα λίστας
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    BOOKMARK_COLORS.forEach { k ->
                        ColorSwatch(Color(k), k == col, "Χρώμα λίστας", onClick = { scope.launch { repo.setListColor(current, k) } }) {
                            if (k == col) Text("✓", style = MotoType.label, color = Color.White)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                    TextChoice("Κοινοποίηση (GPX)", selected = false, onClick = { share(current, ps) }, modifier = Modifier.weight(1f))
                    TextChoice("Μετονομασία", selected = renaming != null, onClick = { renaming = if (renaming == null) current else null }, modifier = Modifier.weight(1f))
                    TextChoice(
                        if (confirmDelete == current) "Σίγουρα;" else "Διαγραφή",
                        selected = confirmDelete == current,
                        onClick = {
                            if (confirmDelete == current) {
                                scope.launch { repo.deleteList(current) }
                                open = null; confirmDelete = null
                            } else confirmDelete = current
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                renaming?.let { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = r,
                            onValueChange = { renaming = it.take(30) },
                            singleLine = true,
                            shape = RoundedCornerShape(Radius.md),
                            modifier = Modifier.weight(1f),
                        )
                        TextChoice("OK", selected = true, onClick = {
                            val to = r.trim()
                            if (to.isNotEmpty() && to != current) {
                                scope.launch { repo.renameList(current, to) }
                                open = to
                            }
                            renaming = null
                        })
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    items(ps, key = { "${it.lat}|${it.lon}" }) { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(Radius.md),
                                color = c.base.surface,
                                onClick = { onGo(p) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
                                    Text(p.name, style = MotoType.body, color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (p.detail.isNotBlank()) Text(p.detail, style = MotoType.label, color = c.base.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            TextChoice("✕", selected = false, onClick = { scope.launch { repo.removeFavorite(p) } })
                        }
                    }
                }
            }
        }
    }
}
