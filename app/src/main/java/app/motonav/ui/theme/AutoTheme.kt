package app.motonav.ui.theme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Πού είναι ο ήλιος: αρκετά ακριβές (±1°) για «μέρα ή νύχτα». */
object Sun {
    fun elevationDeg(lat: Double, lon: Double, epochMs: Long): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = epochMs }
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val hours = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
        val b = Math.toRadians(360.0 * (n - 81) / 364.0)
        val eotMin = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val decl = Math.toRadians(-23.44 * cos(Math.toRadians(360.0 / 365.0 * (n + 10))))
        val solar = hours + lon / 15.0 + eotMin / 60.0
        val h = Math.toRadians(15.0 * (solar - 12.0))
        val phi = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(phi) * sin(decl) + cos(phi) * cos(decl) * cos(h)))
    }

    /** Νύχτα από λίγο μετά τη δύση ως λίγο πριν την ανατολή. */
    fun isNight(lat: Double, lon: Double, epochMs: Long): Boolean = elevationDeg(lat, lon, epochMs) < -1.0
}

/**
 * Τούνελ/σκοτάδι από τον αισθητήρα φωτός, με καθυστέρηση για να μην αναβοσβήνει:
 * σκοτάδι αν < 12 lux για 1,5 s, φως ξανά αν > 60 lux για 3 s.
 */
class LowLight(
    private val darkLux: Float = 12f,
    private val brightLux: Float = 60f,
    private val darkMs: Long = 1_500,
    private val brightMs: Long = 3_000,
) {
    var low = false
        private set
    private var since = -1L

    fun update(lux: Float, nowMs: Long): Boolean {
        val want = when {
            lux < darkLux -> true
            lux > brightLux -> false
            else -> low
        }
        if (want == low) {
            since = -1L
        } else {
            if (since < 0L) since = nowMs
            if (nowMs - since >= (if (want) darkMs else brightMs)) {
                low = want
                since = -1L
            }
        }
        return low
    }
}

/** true = σκούρο θέμα τώρα. Χωρίς αισθητήρα φωτός μετράει μόνο ο ήλιος. */
@Composable
fun rememberAutoDark(enabled: Boolean): Boolean {
    val context = LocalContext.current.applicationContext
    var night by remember { mutableStateOf(true) }
    var lowLight by remember { mutableStateOf(false) }
    val lux = remember { floatArrayOf(Float.NaN) }

    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { lux[0] = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (enabled && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(listener) }
    }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val logic = LowLight()
        var lastSunAt = 0L
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (lastSunAt == 0L || now - lastSunAt > 60_000) {
                lastSunAt = now
                val (lat, lon) = lastPosition(context)
                night = Sun.isNight(lat, lon, System.currentTimeMillis())
            }
            // Ο αισθητήρας στέλνει μόνο αλλαγές, γι' αυτό ρωτάμε εμείς κάθε μισό δευτερόλεπτο.
            if (!lux[0].isNaN()) lowLight = logic.update(lux[0], now)
            delay(500)
        }
    }
    return night || lowLight
}

/** Τελευταία γνωστή θέση, αλλιώς Αθήνα (η ώρα της δύσης αλλάζει λίγα λεπτά μέσα στην Ελλάδα). */
private fun lastPosition(context: Context): Pair<Double, Double> {
    val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (ok) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val l = runCatching {
            lm?.getProviders(true)?.mapNotNull { lm.getLastKnownLocation(it) }?.maxByOrNull { it.time }
        }.getOrNull()
        if (l != null) return l.latitude to l.longitude
    }
    return 37.98 to 23.73
}
