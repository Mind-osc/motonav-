package app.motonav.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.motonav.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import app.motonav.i18n.tr
import app.motonav.i18n.trf

/**
 * Νέα έκδοση; Ρωτάει τα Releases του δημόσιου repo στο GitHub.
 * - Build του δημόσιου repo: κατεβάζει το APK και ανοίγει την εγκατάσταση (ένα πάτημα «Εγκατάσταση»).
 * - Build του ιδιωτικού με κλειδί (UPDATE_TOKEN): ρωτάει τα Releases του ιδιωτικού και κατεβάζει μόνη της το APK.
 * - Build του ιδιωτικού χωρίς κλειδί: ανοίγει τη σελίδα Releases στον browser.
 */
object Updater {
    const val PUBLIC_REPO = "Mind-osc/motonav-"
    const val PRIVATE_REPO = "Mind-osc/Moto-nav"

    class Available(val tag: String, val apkUrl: String?, val notes: String)

    var available by mutableStateOf<Available?>(null)
        private set
    var downloading by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var dismissed by mutableStateOf(false)

    private val isPublicBuild get() = BuildConfig.BUILD_REPO.equals(PUBLIC_REPO, ignoreCase = true)

    /** Ιδιωτικό build με κλειδί: ενημερώσεις κατευθείαν από το ιδιωτικό repo. */
    private val token get() = BuildConfig.UPDATE_TOKEN.takeIf { it.isNotBlank() && !isPublicBuild }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Streetwolf")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Αριθμός build από το tag («build-16» → 16). */
    fun buildOf(tag: String): Int? = tag.substringAfterLast('-').toIntOrNull()

    /** Έλεγχος, το πολύ κάθε 6 ώρες (ή αμέσως με force). */
    suspend fun check(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked", 0L) < 6 * 3600_000L) return@withContext available != null
        runCatching {
            val tok = token
            val o = JSONObject(get("https://api.github.com/repos/${if (tok != null) PRIVATE_REPO else PUBLIC_REPO}/releases/latest"))
            prefs.edit().putLong("checked", now).apply()
            val tag = o.optString("tag_name")
            val published = runCatching { java.time.Instant.parse(o.optString("published_at")).toEpochMilli() }.getOrDefault(0L)
            val assets = o.optJSONArray("assets")
            val list = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }
            val apk = if (tok != null) {
                // Ιδιωτικό: το moto-nav.apk (με την Pointer), από το API (το browser_download_url θέλει σύνδεση στο GitHub).
                list.firstOrNull { it.optString("name") == "moto-nav.apk" }?.optString("url")
            } else list.firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
            val newer = if (isPublicBuild || tok != null) {
                (buildOf(tag) ?: 0) > BuildConfig.VERSION_CODE
            } else {
                // Ιδιωτικό build: κάθε αλλαγή πάει και στο δημόσιο, οπότε «νέο» = δημοσιεύτηκε μετά από αυτό το build.
                published > BuildConfig.BUILD_TIME + 15 * 60_000L
            }
            available = if (newer) Available(tag, if (isPublicBuild || tok != null) apk else null, o.optString("body").take(300)) else null
            error = null
            newer
        }.getOrElse {
            // Να φαίνεται ότι απέτυχε (π.χ. χωρίς internet ή λάθος κλειδί), όχι «έχεις την τελευταία».
            error = trf("Δεν έγινε ο έλεγχος: {0}", it.message)
            false
        }
    }

    /** Πάτημα «Εγκατάσταση». */
    suspend fun install(context: Context) {
        val a = available ?: return
        val url = a.apkUrl
        if (url == null) {
            // ιδιωτικό: η σελίδα Releases στον browser (είσαι συνδεδεμένος στο GitHub)
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$PRIVATE_REPO/releases/latest")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        // Άδεια «εγκατάσταση από αυτή την εφαρμογή» (μία φορά).
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            error = tr("Δώσε την άδεια «Εγκατάσταση άγνωστων εφαρμογών» στο Streetwolf και πάτα ξανά.")
            return
        }
        error = null
        downloading = tr("Κατεβαίνει…")
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val f = File(dir, "moto-nav-${a.tag}.apk")
                // Το GitHub στέλνει redirect σε άλλον server: καινούργια σύνδεση κάθε φορά, με τα headers πριν συνδεθεί.
                var u = URL(url)
                var c: HttpURLConnection
                var hops = 0
                while (true) {
                    c = u.openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 10_000
                    c.readTimeout = 30_000
                    c.setRequestProperty("User-Agent", "Streetwolf")
                    // Κλειδί μόνο προς το api.github.com· ποτέ στον server του redirect.
                    val tok = token
                    if (tok != null && u.host == "api.github.com") {
                        c.setRequestProperty("Authorization", "Bearer $tok")
                        c.setRequestProperty("Accept", "application/octet-stream")
                    }
                    val code = c.responseCode
                    if (code in 300..399 && hops < 5) {
                        val loc = c.getHeaderField("Location") ?: throw IOException(tr("redirect χωρίς Location"))
                        c.disconnect()
                        u = URL(u, loc)
                        hops++
                        continue
                    }
                    break
                }
                if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) downloading = trf("Κατεβαίνει… {0}%", done * 100 / total)
                        }
                    }
                }
                c.disconnect()
                f
            }
        }
        downloading = null
        file.onSuccess { f ->
            runCatching {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { error = trf("Δεν άνοιξε η εγκατάσταση: {0}", it.message) }
        }.onFailure { error = trf("Δεν κατέβηκε: {0}", it.message) }
    }
}
