package app.motonav.nav

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Μια φωνή από το ElevenLabs. */
data class ElevenVoice(
    val id: String,
    val name: String,
    /** «premade», «cloned», «professional», «generated», ή «library» για τη βιβλιοθήκη. */
    val category: String,
    /** π.χ. «female · young · greek». */
    val description: String,
    val previewUrl: String?,
    /** Μόνο για φωνές της βιβλιοθήκης: για να προστεθούν στις δικές σου. */
    val publicOwnerId: String? = null,
)

data class ElevenModel(val id: String, val name: String, val greek: Boolean)

/** Ρυθμίσεις φωνής ElevenLabs, όπως στο site (Stability, Similarity, Style, Speaker boost, Speed). */
data class ElevenSettings(
    val enabled: Boolean = false,
    val key: String = "",
    val voiceId: String = "",
    val voiceName: String = "",
    val modelId: String = "eleven_multilingual_v2",
    val stability: Float = 0.5f,
    val similarity: Float = 0.75f,
    val style: Float = 0f,
    val speakerBoost: Boolean = true,
    val speed: Float = 1f,
) {
    val usable: Boolean get() = enabled && key.isNotBlank() && voiceId.isNotBlank()

    /** Ίδια φράση με ίδιες ρυθμίσεις = ίδιο αρχείο στο κινητό. */
    fun cacheKey(text: String): String {
        val raw = listOf(text, voiceId, modelId, stability, similarity, style, speakerBoost, speed).joinToString("|")
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

object ElevenStore {
    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences("eleven", Context.MODE_PRIVATE)

    fun load(c: Context): ElevenSettings {
        val p = prefs(c)
        return ElevenSettings(
            enabled = p.getBoolean("enabled", false),
            key = p.getString("key", "") ?: "",
            voiceId = p.getString("voice", "") ?: "",
            voiceName = p.getString("voice_name", "") ?: "",
            modelId = p.getString("model", "eleven_multilingual_v2") ?: "eleven_multilingual_v2",
            stability = p.getFloat("stability", 0.5f),
            similarity = p.getFloat("similarity", 0.75f),
            style = p.getFloat("style", 0f),
            speakerBoost = p.getBoolean("boost", true),
            speed = p.getFloat("speed", 1f),
        )
    }

    fun save(c: Context, s: ElevenSettings) {
        prefs(c).edit()
            .putBoolean("enabled", s.enabled)
            .putString("key", s.key.trim())
            .putString("voice", s.voiceId)
            .putString("voice_name", s.voiceName)
            .putString("model", s.modelId)
            .putFloat("stability", s.stability)
            .putFloat("similarity", s.similarity)
            .putFloat("style", s.style)
            .putBoolean("boost", s.speakerBoost)
            .putFloat("speed", s.speed)
            .apply()
    }

    /** Τι λέει η φωνή όταν πατάς «Ξεκίνα». {προορισμός} = το όνομα του προορισμού. */
    const val DEFAULT_START = "Ξεκινάμε."

    fun startPhrase(c: Context): String = prefs(c).getString("start_phrase", DEFAULT_START)?.ifBlank { DEFAULT_START } ?: DEFAULT_START

    fun setStartPhrase(c: Context, text: String) {
        prefs(c).edit().putString("start_phrase", text).apply()
    }

    fun cacheDir(c: Context) = File(c.applicationContext.cacheDir, "eleven").apply { mkdirs() }

    fun clearCache(c: Context) {
        cacheDir(c).listFiles()?.forEach { it.delete() }
    }

    fun cacheSizeBytes(c: Context): Long = cacheDir(c).listFiles()?.sumOf { it.length() } ?: 0L
}

/** Κλήσεις στο API του ElevenLabs (api.elevenlabs.io). */
object ElevenClient {
    private const val BASE = "https://api.elevenlabs.io"

    private fun open(path: String, key: String, method: String = "GET", body: String? = null, readTimeoutMs: Int = 15_000): HttpURLConnection {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = readTimeoutMs
        c.requestMethod = method
        c.setRequestProperty("xi-api-key", key)
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            c.disconnect()
            throw IOException(friendly(code, err))
        }
        return c
    }

    private fun friendly(code: Int, body: String): String {
        val detail = runCatching {
            val d = JSONObject(body).opt("detail")
            when (d) {
                is JSONObject -> d.optString("message").ifBlank { d.optString("status") }
                is String -> d
                else -> ""
            }
        }.getOrDefault("")
        val what = when (code) {
            401 -> "Λάθος ή ληγμένο κλειδί ElevenLabs"
            402 -> "Χρειάζεται συνδρομή ElevenLabs για αυτό"
            403 -> "Δεν επιτρέπεται με αυτό το κλειδί (δικαιώματα κλειδιού ή πακέτο)"
            429 -> "Πολλά αιτήματα ή τελείωσαν οι χαρακτήρες του μήνα"
            else -> "ElevenLabs HTTP $code"
        }
        return if (detail.isBlank()) what else "$what: ${detail.take(160)}"
    }

    private fun text(path: String, key: String, method: String = "GET", body: String? = null): String {
        val c = open(path, key, method, body)
        try {
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun voiceDescription(labels: JSONObject?, extra: List<String> = emptyList()): String {
        val parts = ArrayList<String>()
        if (labels != null) {
            for (k in listOf("gender", "age", "accent", "language", "descriptive", "use_case", "description")) {
                labels.optString(k).takeIf { it.isNotBlank() }?.let { parts += it.replace('_', ' ') }
            }
        }
        parts += extra.filter { it.isNotBlank() }
        return parts.distinct().joinToString(" · ")
    }

    /** Όλες οι φωνές του λογαριασμού σου (έτοιμες του ElevenLabs + όσες έχεις προσθέσει ή φτιάξει). */
    fun voices(key: String): List<ElevenVoice> = parseVoices(text("/v1/voices", key))

    fun parseVoices(json: String): List<ElevenVoice> {
        val arr = JSONObject(json).optJSONArray("voices") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val v = arr.optJSONObject(i) ?: return@mapNotNull null
            ElevenVoice(
                id = v.optString("voice_id"),
                name = v.optString("name"),
                category = v.optString("category"),
                description = voiceDescription(v.optJSONObject("labels"), listOf(v.optString("description").take(80))),
                previewUrl = v.optString("preview_url").takeIf { it.startsWith("http") },
            )
        }.filter { it.id.isNotBlank() }
    }

    /** Βιβλιοθήκη φωνών (κοινόχρηστες), φιλτραρισμένες σε μια γλώσσα. Στο δωρεάν πακέτο το API δεν τη δίνει. */
    fun library(key: String, language: String = "el", search: String = "", page: Int = 0): List<ElevenVoice> {
        val q = buildString {
            append("/v1/shared-voices?page_size=60&page=$page")
            if (language.isNotBlank()) append("&language=").append(URLEncoder.encode(language, "UTF-8"))
            if (search.isNotBlank()) append("&search=").append(URLEncoder.encode(search, "UTF-8"))
        }
        val arr = JSONObject(text(q, key)).optJSONArray("voices") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val v = arr.optJSONObject(i) ?: return@mapNotNull null
            ElevenVoice(
                id = v.optString("voice_id"),
                name = v.optString("name"),
                category = "library",
                description = listOf(v.optString("gender"), v.optString("age"), v.optString("accent"), v.optString("language"), v.optString("use_case"), v.optString("descriptive"))
                    .filter { it.isNotBlank() }.joinToString(" · ").replace('_', ' '),
                previewUrl = v.optString("preview_url").takeIf { it.startsWith("http") },
                publicOwnerId = v.optString("public_owner_id").takeIf { it.isNotBlank() },
            )
        }
    }

    /** Προσθέτει μια φωνή της βιβλιοθήκης στις δικές σου· επιστρέφει το νέο voice_id. */
    fun addFromLibrary(key: String, v: ElevenVoice): String {
        val owner = v.publicOwnerId ?: throw IOException("Λείπει ο κάτοχος της φωνής")
        val res = text("/v1/voices/add/$owner/${v.id}", key, "POST", JSONObject().put("new_name", v.name).toString())
        return JSONObject(res).optString("voice_id").ifBlank { v.id }
    }

    /** Μοντέλα που κάνουν κείμενο σε ομιλία· σημειώνουμε όσα λένε ότι ξέρουν ελληνικά. */
    fun models(key: String): List<ElevenModel> {
        val arr = JSONArray(text("/v1/models", key))
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            if (!m.optBoolean("can_do_text_to_speech", false)) return@mapNotNull null
            val langs = m.optJSONArray("languages")
            val greek = langs != null && (0 until langs.length()).any { j ->
                val l = langs.optJSONObject(j)
                l?.optString("language_id") == "el" || l?.optString("name")?.contains("Greek", true) == true
            }
            ElevenModel(m.optString("model_id"), m.optString("name"), greek)
        }
    }

    /** Η φράση σε mp3 (από το κινητό αν την έχει ξαναπεί, αλλιώς από το ElevenLabs). */
    fun speech(context: Context, s: ElevenSettings, text: String): File {
        val f = File(ElevenStore.cacheDir(context), s.cacheKey(text) + ".mp3")
        if (f.exists() && f.length() > 0) return f
        val settings = JSONObject()
            .put("stability", s.stability.toDouble())
            .put("similarity_boost", s.similarity.toDouble())
            .put("style", s.style.toDouble())
            .put("use_speaker_boost", s.speakerBoost)
            .put("speed", s.speed.toDouble())
        val body = JSONObject()
            .put("text", text)
            .put("model_id", s.modelId)
            .put("voice_settings", settings)
            .apply { if (s.modelId.contains("v2_5") || s.modelId.contains("v3")) put("language_code", "el") }
            .toString()
        val c = open("/v1/text-to-speech/${s.voiceId}?output_format=mp3_44100_128", s.key, "POST", body, readTimeoutMs = 12_000)
        try {
            val tmp = File(f.path + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(f)) throw IOException("cache")
            return f
        } finally {
            c.disconnect()
        }
    }
}
