package app.motonav

import app.motonav.i18n.EN
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Κάθε tr("…")/trf("…") του κώδικα έχει αγγλική μετάφραση στο i18n/En.kt, με τις ίδιες θέσεις {0}, {1}…
 * Αν αποτύχει μετά από νέο κείμενο: πρόσθεσε τη γραμμή στο En.kt.
 */
class TranslationTest {
    private val call = Regex("""\btrf?\("((?:[^"\\]|\\.)*)"""")
    private val holes = Regex("""\{\d+\}|%[.\d]*[sdf]""")

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n'); 't' -> append('\t'); else -> append(n)
                }
                i += 2
            } else { append(ch); i++ }
        }
    }

    @Test fun everyTextHasEnglish() {
        val missing = mutableListOf<String>()
        val wrongHoles = mutableListOf<String>()
        File("src/main/java").walk().filter { it.extension == "kt" && "i18n" !in it.path }.forEach { f ->
            call.findAll(f.readText()).forEach { m ->
                val key = unescape(m.groupValues[1])
                val en = EN[key]
                if (en == null) missing += "${f.name}: $key"
                else if (holes.findAll(key).map { it.value }.sorted().toList() != holes.findAll(en).map { it.value }.sorted().toList())
                    wrongHoles += "${f.name}: $key → $en"
            }
        }
        assertTrue("Χωρίς μετάφραση:\n" + missing.joinToString("\n"), missing.isEmpty())
        assertTrue("Άλλες θέσεις {n}:\n" + wrongHoles.joinToString("\n"), wrongHoles.isEmpty())
    }

    @Test fun formatFills() {
        assertTrue(app.motonav.i18n.trf("{0} σε {1} km", "a", 3) == "a σε 3 km")
    }
}
