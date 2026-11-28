package app.motonav.nav

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import app.motonav.i18n.tr

/**
 * Χειριστήριο στο τιμόνι (Carpe Iter, WunderLINQ, Parrot, Bluetooth πληκτρολόγιο / remote):
 * στέλνουν πλήκτρα όπως ένα πληκτρολόγιο. Κάθε ενέργεια έχει ένα ή περισσότερα πλήκτρα.
 */
object KeyBus {
    enum class Action(val label: String) {
        ZOOM_IN(tr("Ζουμ +")),
        ZOOM_OUT(tr("Ζουμ −")),
        CENTER(tr("Κέντραρε / όλη η διαδρομή")),
        MUTE(tr("Φωνή ναι/όχι")),
        REPORT(tr("Αναφορά κινδύνου")),
        SEARCH(tr("Αναζήτηση")),
    }

    /** Προεπιλογή: βελάκια, OK και πλήκτρα ζουμ (ό,τι στέλνουν τα περισσότερα χειριστήρια). */
    private val defaults: Map<Action, List<Int>> = mapOf(
        Action.ZOOM_IN to listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ZOOM_IN, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_EQUALS),
        Action.ZOOM_OUT to listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_ZOOM_OUT, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT),
        Action.CENTER to listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER),
        Action.MUTE to listOf(KeyEvent.KEYCODE_DPAD_LEFT),
        Action.REPORT to listOf(KeyEvent.KEYCODE_DPAD_RIGHT),
        Action.SEARCH to emptyList(),
    )

    /** Τρέχουσα αντιστοίχιση (από Ρυθμίσεις → Χειριστήριο τιμονιού). */
    var mapping by mutableStateOf(defaults)
        private set

    /** Ο χάρτης φαίνεται (όχι ρυθμίσεις/αναζήτηση): μόνο τότε «τρώμε» τα πλήκτρα. */
    @Volatile var mapActive = false
    @Volatile var overlay = false

    /** Περιμένει πλήκτρο για αυτή την ενέργεια (Ρυθμίσεις → «Αλλαγή»). */
    var learning by mutableStateOf<Action?>(null)
    var lastKey by mutableStateOf<String?>(null)
        private set

    private val _events = MutableSharedFlow<Action>(extraBufferCapacity = 8)
    val events: SharedFlow<Action> = _events

    /** «ZOOM_IN=19,168;ZOOM_OUT=20» ↔ αντιστοίχιση. Κενό = προεπιλογές. */
    fun load(text: String) {
        if (text.isBlank()) { mapping = defaults; return }
        val m = defaults.toMutableMap()
        text.split(';').forEach { part ->
            val (k, v) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val a = runCatching { Action.valueOf(k) }.getOrNull() ?: return@forEach
            m[a] = v.split(',').mapNotNull { it.trim().toIntOrNull() }
        }
        mapping = m
    }

    fun serialize(m: Map<Action, List<Int>> = mapping): String =
        m.entries.joinToString(";") { (a, ks) -> a.name + "=" + ks.joinToString(",") }

    fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    /** Από το Activity. true = το χειριστήκαμε. Επιστρέφει νέα αντιστοίχιση όταν «μαθαίνει». */
    fun onKey(event: KeyEvent, save: (String) -> Unit): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) return false
        val l = learning
        if (l != null) {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            // ένα πλήκτρο κάνει μία ενέργεια: φεύγει από όποια άλλη το είχε
            val m = mapping.mapValues { (_, ks) -> ks - code }.toMutableMap()
            m[l] = listOf(code)
            mapping = m
            learning = null
            lastKey = keyName(code)
            save(serialize(m))
            return true
        }
        if (!mapActive || overlay) return false
        // Ένταση ήχου: μόνο αν την έχεις αντιστοιχίσει ρητά.
        val action = mapping.entries.firstOrNull { code in it.value }?.key ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) _events.tryEmit(action)
        return true
    }

    fun reset(save: (String) -> Unit) {
        mapping = defaults
        save("")
    }
}
