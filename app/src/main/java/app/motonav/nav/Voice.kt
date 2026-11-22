package app.motonav.nav

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.motonav.routing.Maneuver
import app.motonav.routing.Turn
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Τελευταία αποτυχία του ElevenLabs (για τη σελίδα «Φωνή»). */
object ElevenStatus {
    var lastError by androidx.compose.runtime.mutableStateOf<String?>(null)
    var lastOkAt by androidx.compose.runtime.mutableStateOf(0L)
}

/**
 * Φωνή πλοήγησης. Με κλειδί ElevenLabs: η φωνή που διάλεξες (κάθε φράση αποθηκεύεται στο κινητό
 * και την επόμενη φορά παίζει χωρίς internet). Χωρίς internet, χωρίς κλειδί ή αν αποτύχει: η φωνή του Android.
 * Παίζει όπου παίζει ο ήχος (και στο Bluetooth κράνους), χαμηλώνοντας τη μουσική όσο μιλάει.
 */
class VoiceGuide(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private var ready = false
    var greekMissing = false
        private set
    var muted = false

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var generation = 0L
    private val queue = ArrayDeque<java.io.File>()
    private var player: android.media.MediaPlayer? = null
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts.setLanguage(Locale("el", "GR"))
        greekMissing = r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED
        tts.setAudioAttributes(attrs)
        ready = true
    }

    private fun systemSay(text: String, interrupt: Boolean) {
        if (!ready) return
        tts.speak(text, if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "moto-${System.nanoTime()}")
    }

    /** Ως πότε δεν ρωτάμε το ElevenLabs (μετά από αποτυχία). */
    private var elevenDownUntil = 0L

    /** Ως πότε δεν κόβεται ό,τι λέγεται (π.χ. η φράση εκκίνησης): οι επόμενες μπαίνουν στη σειρά. */
    private var holdUntil = 0L

    /** Λέει τη φράση και δεν την αφήνει να κοπεί για holdMs· ό,τι έρθει στο μεταξύ περιμένει τη σειρά του. */
    fun sayProtected(text: String, holdMs: Long = 5_000) {
        say(text, interrupt = true)
        holdUntil = android.os.SystemClock.elapsedRealtime() + holdMs
    }

    fun say(text: String, interrupt: Boolean = true) {
        if (muted) return
        val cut = interrupt && android.os.SystemClock.elapsedRealtime() >= holdUntil
        val s = ElevenStore.load(appContext)
        // Αν το ElevenLabs απέτυχε πρόσφατα, όλα με τη φωνή του κινητού για 10′ — όχι μισά-μισά
        // (π.χ. η φράση εκκίνησης από το κινητό αποθηκευμένη με άλλη φωνή και οι οδηγίες με του κινητού).
        if (!s.usable || android.os.SystemClock.elapsedRealtime() < elevenDownUntil) {
            systemSay(text, cut)
            return
        }
        if (cut) stopAll()
        val g = generation
        worker.execute {
            val res = runCatching { ElevenClient.speech(appContext, s, text) }
            main.post {
                if (g != generation || muted) return@post
                res.fold(
                    onSuccess = { f ->
                        ElevenStatus.lastOkAt = System.currentTimeMillis()
                        ElevenStatus.lastError = null
                        queue.addLast(f)
                        if (player == null) playNext()
                    },
                    onFailure = { e ->
                        ElevenStatus.lastError = e.message ?: e.toString()
                        elevenDownUntil = android.os.SystemClock.elapsedRealtime() + 10 * 60_000L
                        systemSay(text, false)
                    },
                )
            }
        }
    }

    private fun playNext() {
        val f = queue.removeFirstOrNull()
        if (f == null) {
            player = null
            audio.abandonAudioFocusRequest(focus)
            return
        }
        audio.requestAudioFocus(focus)
        val p = android.media.MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(attrs)
            p.setDataSource(f.path)
            p.setOnCompletionListener { it.release(); if (player === it) { player = null; playNext() } }
            p.setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) { player = null; playNext() }; true }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            p.release()
            player = null
            f.delete() // χαλασμένο αρχείο: την επόμενη φορά ξανά από το ElevenLabs
            playNext()
        }
    }

    private fun stopAll() {
        generation++
        queue.clear()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        audio.abandonAudioFocusRequest(focus)
        if (ready) tts.stop()
    }

    fun silence() {
        main.post { stopAll() }
    }

    fun shutdown() {
        stopAll()
        worker.shutdownNow()
        tts.stop()
        tts.shutdown()
    }
}

object Phrases {
    private val ordinals = listOf("", "πρώτη", "δεύτερη", "τρίτη", "τέταρτη", "πέμπτη", "έκτη")

    fun action(m: Maneuver): String = when (m.turn) {
        Turn.STRAIGHT -> "συνέχισε ευθεία"
        Turn.LEFT -> "στρίψε αριστερά"
        Turn.SLIGHT_LEFT -> "στρίψε ελαφρά αριστερά"
        Turn.SHARP_LEFT -> "στρίψε απότομα αριστερά"
        Turn.RIGHT -> "στρίψε δεξιά"
        Turn.SLIGHT_RIGHT -> "στρίψε ελαφρά δεξιά"
        Turn.SHARP_RIGHT -> "στρίψε απότομα δεξιά"
        Turn.KEEP_LEFT -> "κράτα αριστερά"
        Turn.KEEP_RIGHT -> "κράτα δεξιά"
        Turn.UTURN -> "κάνε αναστροφή"
        Turn.EXIT_LEFT -> "βγες αριστερά"
        Turn.EXIT_RIGHT -> "βγες δεξιά"
        Turn.ROUNDABOUT, Turn.ROUNDABOUT_LEFT ->
            if (m.exit > 0) "στον κυκλικό κόμβο, πάρε την ${ordinal(m.exit)} έξοδο" else "μπες στον κυκλικό κόμβο"
        Turn.ARRIVE -> "φτάνεις στον προορισμό"
    }

    /** Πού μπαίνεις: «έξοδος 12 · Λαμία» ή το όνομα του δρόμου. null αν δεν το ξέρουμε. */
    fun road(m: Maneuver): String? {
        val exit = m.exitNumber?.let { "έξοδος $it" }
        val parts = listOfNotNull(exit, m.signpost ?: m.street.takeIf { exit == null || m.signpost == null })
        return parts.distinct().joinToString(" · ").ifBlank { null }
    }

    fun ordinal(n: Int): String = ordinals.getOrNull(n)?.takeIf { it.isNotEmpty() } ?: "${n}η"

    /** «300 μέτρα», «1,5 χιλιόμετρα». */
    fun distance(m: Double): String = when {
        m < 1000 -> "${max(50, ((m / 50.0).roundToInt() * 50))} μέτρα"
        else -> {
            val km = (m / 100.0).roundToInt() / 10.0
            when {
                km == 1.0 -> "1 χιλιόμετρο"
                km % 1.0 == 0.0 -> "${km.toInt()} χιλιόμετρα"
                else -> String.format(Locale("el", "GR"), "%.1f χιλιόμετρα", km)
            }
        }
    }

    fun capitalized(s: String) = s.replaceFirstChar { it.uppercase(Locale("el", "GR")) }
}

/**
 * Αποφασίζει πότε μιλάει. Τρία σημεία για κάθε κίνηση, ανάλογα με την ταχύτητα:
 * μακριά (~30 s πριν), κοντά (~10 s πριν), τώρα (~3 s πριν).
 */
class Announcer(private val voice: VoiceGuide) {
    private val stage = HashMap<Int, Int>()
    private var arrivedSaid = false

    fun reset() {
        stage.clear()
    }

    fun onState(s: NavState, speedMps: Float) {
        val m = s.next ?: return
        val v = max(speedMps.toDouble(), 8.0)
        val far = max(500.0, v * 30.0)
        val near = max(120.0, v * 10.0)
        val now = max(30.0, v * 3.0)
        val done = stage[m.pointIndex] ?: 0
        val d = s.distToNextM
        val thenText = s.then?.let { ", και μετά ${Phrases.action(it)}" } ?: ""

        when {
            d <= now && done < 3 -> {
                if (m.turn != Turn.ARRIVE) voice.say("Τώρα, ${Phrases.action(m)}$thenText")
                stage[m.pointIndex] = 3
            }
            d <= near && done < 2 -> {
                val road = Phrases.road(m)?.let { ", $it" } ?: ""
                voice.say("Σε ${Phrases.distance(d)}, ${Phrases.action(m)}$road$thenText")
                stage[m.pointIndex] = 2
            }
            d <= far && d > near * 1.5 && done < 1 -> {
                voice.say("Σε ${Phrases.distance(d)}, ${Phrases.action(m)}")
                stage[m.pointIndex] = 1
            }
        }
    }

    fun arrived() {
        if (arrivedSaid) return
        arrivedSaid = true
        voice.say("Έφτασες στον προορισμό σου.")
    }
}
