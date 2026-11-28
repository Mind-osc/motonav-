package app.motonav.data

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import app.motonav.i18n.tr

/**
 * Αντίγραφο ασφαλείας σε ένα .zip: ρυθμίσεις, κλειδιά, αγαπημένα, φωνή, παρέα, κάμερες,
 * αναφορές, βελάκι, ημερολόγιο διαδρομών. Όχι οι offline χάρτες και οι προσωρινές μνήμες
 * (ξανακατεβαίνουν), ούτε η ταυτότητα του Firebase (το νέο κινητό παίρνει δική του).
 *
 * Μέσα στο zip:
 *  - motonav-backup.json   (έκδοση, ώρα)
 *  - data/files/…          (context.filesDir, μαζί με το datastore)
 *  - data/shared_prefs/…   (SharedPreferences)
 *  - ext/…                 (getExternalFilesDir, εκεί είναι το ημερολόγιο)
 */
object Backup {
    private const val MANIFEST = "motonav-backup.json"
    private const val FORMAT = 1
    private const val MAX_FILE = 20L * 1024 * 1024

    /** Ονόματα που δεν μπαίνουν (αρχή του ονόματος, σε οποιοδήποτε επίπεδο). */
    private val skipPrefixes = listOf(
        "mbgl", "spots", "PersistedInstallation", "generatefid", "com.google", ".com.google",
        "com.crashlytics", "FirebaseHeartBeat", "WebView", "fuel_cache", "cameras_osm",
    )

    class Result(val files: Int, val bytes: Long)

    private fun roots(c: Context): List<Pair<String, File>> = buildList {
        add("data/files" to c.filesDir)
        c.filesDir.parentFile?.let { add("data/shared_prefs" to File(it, "shared_prefs")) }
        c.getExternalFilesDir(null)?.let { add("ext" to it) }
    }

    private fun skip(f: File) = skipPrefixes.any { f.name.startsWith(it) } ||
        (f.isFile && f.length() > MAX_FILE)

    fun export(c: Context, out: OutputStream): Result {
        var files = 0
        var bytes = 0L
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("app", c.packageName)
                .put("time", System.currentTimeMillis())
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            for ((prefix, dir) in roots(c)) {
                if (!dir.isDirectory) continue
                dir.walkTopDown()
                    .onEnter { it == dir || !skip(it) }
                    .filter { it.isFile && !skip(it) }
                    .forEach { f ->
                        val rel = f.relativeTo(dir).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry("$prefix/$rel").apply { time = f.lastModified() })
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        files++
                        bytes += f.length()
                    }
            }
        }
        return Result(files, bytes)
    }

    /** Γράφει τα αρχεία από πάνω. Μετά χρειάζεται [restart], για να ξαναδιαβαστούν. */
    fun import(c: Context, input: InputStream): Result {
        val roots = roots(c).toMap()
        var sawManifest = false
        var files = 0
        var bytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name == MANIFEST) {
                    val m = JSONObject(zip.readBytes().decodeToString())
                    require(m.optInt("format") in 1..FORMAT) { tr("Άγνωστη έκδοση αντιγράφου") }
                    sawManifest = true
                    continue
                }
                if (e.isDirectory || name.contains("..") || name.startsWith("/")) continue
                val prefix = roots.keys.firstOrNull { name.startsWith("$it/") } ?: continue
                val base = roots.getValue(prefix)
                val target = File(base, name.removePrefix("$prefix/"))
                if (!target.canonicalPath.startsWith(base.canonicalPath + File.separator)) continue
                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".restore")
                tmp.outputStream().use { zip.copyTo(it) }
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
                files++
                bytes += target.length()
            }
        }
        require(sawManifest) { tr("Δεν είναι αντίγραφο του Streetwolf") }
        return Result(files, bytes)
    }

    /** Ξανανοίγει την εφαρμογή από την αρχή, ώστε όλα να διαβαστούν από τα νέα αρχεία. */
    fun restart(c: Context) {
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName) ?: return
        val intent = Intent.makeRestartActivityTask(launch.component)
        c.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    fun fileName(): String {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return "motonav-backup-$d.zip"
    }
}
