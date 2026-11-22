package app.motonav.nav

import android.content.Context
import android.location.Location
import app.motonav.trips.TripStats
import app.motonav.trips.TripSummary
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Γράφει το ίχνος κάθε οδήγησης σε αρχείο GPX στο κινητό (Android/data/app.motonav/files/trips/)
 * και στο τέλος μια σύνοψη (.json δίπλα του) για το ημερολόγιο διαδρομών.
 */
class TripRecorder(context: Context) {
    private val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "trips")
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private var writer: BufferedWriter? = null
    private var count = 0
    private val stats = TripStats()
    var file: File? = null
        private set

    fun start() {
        runCatching {
            dir.mkdirs()
            val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val f = File(dir, "$name.gpx")
            file = f
            writer = f.bufferedWriter().apply {
                write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                write("<gpx version=\"1.1\" creator=\"Moto Nav\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                write("<trk><name>$name</name><trkseg>\n")
            }
        }
    }

    fun add(l: Location) {
        val w = writer ?: return
        stats.add(
            lat = l.latitude,
            lon = l.longitude,
            timeMs = l.time,
            speedKmh = if (l.hasSpeed()) l.speed * 3.6 else null,
            altitude = if (l.hasAltitude()) l.altitude else null,
        )
        runCatching {
            w.write(String.format(Locale.US, "<trkpt lat=\"%.6f\" lon=\"%.6f\">", l.latitude, l.longitude))
            if (l.hasAltitude()) w.write(String.format(Locale.US, "<ele>%.1f</ele>", l.altitude))
            w.write("<time>${timeFmt.format(Date(l.time))}</time></trkpt>\n")
            if (++count % 10 == 0) w.flush()
        }
    }

    /** Παύση: τα στίγματα δεν γράφονται· η συνέχεια ξεκινά νέο κομμάτι (χωρίς ευθεία γραμμή στο κενό). */
    fun pause() {
        runCatching { writer?.flush() }
    }

    fun resume() {
        stats.gap()
        runCatching { writer?.write("</trkseg><trkseg>\n") }
    }

    /** Κλίση από τα όργανα (για τα μέγιστα αριστερά/δεξιά). */
    fun lean(deg: Float) = stats.lean(deg.toDouble())

    val distanceM: Double get() = stats.distanceM

    fun finish() {
        runCatching {
            writer?.run {
                write("</trkseg></trk>\n</gpx>\n")
                close()
            }
        }
        writer = null
        val f = file ?: return
        if (stats.points >= 2) {
            runCatching { File(f.parentFile, f.nameWithoutExtension + ".json").writeText(TripSummary.toJson(stats.summary(f.nameWithoutExtension))) }
        } else {
            // Ούτε δύο σημεία: δεν άξιζε να κρατηθεί.
            runCatching { f.delete() }
        }
    }
}
