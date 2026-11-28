package app.motonav.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Γλώσσα της εφαρμογής: Ελληνικά, English, ή «Αυτόματο» (της γλώσσας του κινητού: ελληνικά αν είναι
 * στα ελληνικά, αλλιώς αγγλικά). Τα κείμενα περνάνε από tr()/trf(): κλειδί είναι το ελληνικό κείμενο,
 * η αγγλική μετάφραση είναι στο En.kt. Στην αλλαγή η εφαρμογή ξανανοίγει, για να αλλάξουν όλα μαζί.
 */
object Lang {
    const val AUTO = "auto"
    const val GREEK = "el"
    const val ENGLISH = "en"

    var mode by mutableStateOf(AUTO)
        private set
    var english by mutableStateOf(false)
        private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("app_lang", Context.MODE_PRIVATE)

    fun init(c: Context) {
        mode = prefs(c).getString("mode", AUTO) ?: AUTO
        english = resolve(mode)
    }

    fun resolve(m: String): Boolean = when (m) {
        ENGLISH -> true
        GREEK -> false
        else -> Locale.getDefault().language != "el"
    }

    /** Αποθηκεύει την επιλογή· true αν άλλαξε η γλώσσα (τότε ξανανοίγουμε την εφαρμογή). */
    fun set(c: Context, m: String): Boolean {
        prefs(c).edit().putString("mode", m).commit()
        mode = m
        return resolve(m) != english
    }
}

/** Το κείμενο στη γλώσσα της εφαρμογής. */
fun tr(s: String): String = if (Lang.english) EN[s] ?: s else s

/** Το ίδιο, με τιμές στις θέσεις {0}, {1}… */
fun trf(s: String, vararg args: Any?): String {
    var r = tr(s)
    args.forEachIndexed { i, v -> r = r.replace("{$i}", v.toString()) }
    return r
}
