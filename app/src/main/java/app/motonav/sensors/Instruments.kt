package app.motonav.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2

data class Instruments(
    /** Κλίση σε μοίρες. Αρνητικό = αριστερά, θετικό = δεξιά. */
    val leanDeg: Float = 0f,
    val maxLeft: Float = 0f,
    val maxRight: Float = 0f,
    /** Κατεύθυνση 0..360 (0 = βορράς), ή null αν δεν είναι γνωστή. */
    val headingDeg: Float? = null,
    val altitudeM: Double? = null,
    val leanAvailable: Boolean = true,
    /** Κλίση δρόμου σε %, από βαρόμετρο (αν υπάρχει) ή υψόμετρο GPS. Θετικό = ανηφόρα. */
    val gradePct: Double? = null,
    /** Επιτάχυνση σε g. Μπρος–πίσω: θετικό = γκάζι, αρνητικό = φρένο. Πλάγια: θετικό = στροφή δεξιά. */
    val gLon: Float = 0f,
    val gLat: Float = 0f,
    val maxAccel: Float = 0f,
    val maxBrake: Float = 0f,
    val maxGLeft: Float = 0f,
    val maxGRight: Float = 0f,
    /** 0–100: πότε ξεκίνησε (SystemClock.elapsedRealtime), αν τρέχει τώρα. */
    val runStartMs: Long? = null,
    /** 0–100 σε δευτερόλεπτα: το τελευταίο και το καλύτερο. */
    val last0to100: Float? = null,
    val best0to100: Float? = null,
)

object LeanMath {
    const val G = 9.80665

    /**
     * Κλίση μηχανής σε σταθερή στροφή: tan(φ) = v · ω / g.
     * v σε m/s, ω (ρυθμός στροφής γύρω από τον κατακόρυφο άξονα) σε rad/s, θετικό = αριστερά.
     * Επιστρέφει μοίρες, αρνητικό = αριστερά.
     */
    fun leanDeg(speedMps: Double, yawRateRadS: Double): Double {
        if (speedMps < 3.0) return 0.0
        return -Math.toDegrees(atan(speedMps * yawRateRadS / G))
    }

    /**
     * Ρυθμός στροφής γύρω από τον πραγματικό κατακόρυφο, χωρίς να ξέρουμε πώς είναι στερεωμένο το κινητό
     * ούτε πόσο γέρνει η μηχανή: (γυροσκόπιο · επιτάχυνση) / g.
     * Σε στροφή, η επιτάχυνση που «νιώθει» το κινητό έχει πάντα κατακόρυφο κομμάτι g, και η περιστροφή
     * είναι γύρω από τον κατακόρυφο, άρα το εσωτερικό γινόμενο βγάζει ω·g, όσο κι αν γέρνει η μηχανή.
     * (Με τον «κατακόρυφο» της σύντηξης αισθητήρων, σε σταθερή στροφή 45° έβγαινε ~35°.)
     */
    fun yawRate(gx: Double, gy: Double, gz: Double, ax: Double, ay: Double, az: Double): Double =
        (gx * ax + gy * ay + gz * az) / LeanMath.G

    /**
     * Κλίση δρόμου από δείγματα (απόσταση που διένυσες, υψόμετρο): το τελευταίο μείον το πρώτο
     * σε παράθυρο τουλάχιστον minDist μέτρων. null αν δεν έχεις κινηθεί αρκετά.
     */
    fun gradePct(samples: List<Pair<Double, Double>>, minDist: Double = 60.0): Double? {
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val d = last.first - first.first
        if (d < minDist) return null
        return (last.second - first.second) / d * 100.0
    }

    /**
     * Πλάγια επιτάχυνση σε στροφή, από την κλίση: g = tan(κλίση).
     * (Ο αισθητήρας του κινητού δεν τη βλέπει: η μηχανή γέρνει μαζί της.) 45° = 1 g.
     */
    fun lateralG(leanDeg: Double): Double = kotlin.math.tan(Math.toRadians(leanDeg.coerceIn(-65.0, 65.0)))

    /** Πότε πέρασε τα 100: γραμμική παρεμβολή ανάμεσα σε δύο σημεία GPS. Χρόνοι σε ms. */
    fun crossTime(t0: Long, v0: Double, t1: Long, v1: Double, target: Double): Long =
        if (v1 <= v0) t1 else t0 + ((target - v0) / (v1 - v0) * (t1 - t0)).toLong().coerceIn(0L, t1 - t0)

    /** Κατεύθυνση 0..360 από διάνυσμα (ανατολή, βορράς). */
    fun headingDeg(east: Double, north: Double): Double =
        (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
}

/**
 * Διαβάζει αισθητήρες του κινητού:
 * - Γυροσκόπιο + προσανατολισμός (game rotation vector): ρυθμός στροφής στον κόσμο, άρα κλίση
 *   ανεξάρτητα από το πώς είναι στερεωμένο το κινητό.
 * - Rotation vector (με μαγνητόμετρο): πυξίδα όταν είσαι σταματημένος. Όταν κινείσαι, κατεύθυνση από το GPS.
 */
class InstrumentEngine(context: Context, private val onChange: (Instruments) -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val gameRot = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val magRot = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val pressure = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private var baroAlt: Double? = null
    private val gradeSamples = ArrayDeque<Pair<Double, Double>>()
    private var traveled = 0.0
    private var lastLoc: Location? = null
    private var grade: Double? = null

    private val rGame = FloatArray(9)
    private val rMag = FloatArray(9)
    private var hasGame = false
    private var hasMag = false
    private var yawRate = 0.0
    /** Επιτάχυνση με φίλτρο (~0,5″), για να σβήνουν οι κραδασμοί. */
    private val acc = DoubleArray(3)
    private var hasAcc = false
    private var lean = 0.0
    private var compass: Double? = null

    private var speedMps = 0.0
    private var gpsBearing: Double? = null
    private var altitude: Double? = null
    private var maxLeft = 0f
    private var maxRight = 0f
    private var lastEmit = 0L

    // ---- Επιτάχυνση (g)
    /** Μπρος–πίσω από τους αισθητήρες (m/s²): επιτάχυνση στον κόσμο, πάνω στην κατεύθυνση του GPS. */
    private var accFwd = 0.0
    private var accFwdSum = 0.0
    private var accFwdN = 0
    private var accFwdAt = 0L
    /** Διόρθωση των αισθητήρων με το GPS (σε g): ό,τι λέει η αλλαγή ταχύτητας ανάμεσα σε δύο σημεία. */
    private var bias = 0.0
    private var gpsLon = 0.0
    private var gLon = 0.0
    private var prevSpeed: Double? = null
    private var prevFixMs = 0L
    private var maxAccel = 0f
    private var maxBrake = 0f
    private var maxGLeft = 0f
    private var maxGRight = 0f
    // ---- 0–100
    private var stoppedAtMs: Long? = null
    private var runStartMs: Long? = null
    private var last0to100: Float? = null
    private var best0to100: Float? = null

    private val available = gyro != null && (accel != null || gameRot != null || magRot != null)

    fun start() {
        val s = sm ?: return
        gameRot?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magRot?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accel?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        pressure?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        emit(force = true)
    }

    fun stop() {
        sm?.unregisterListener(this)
    }

    fun resetMax() {
        maxLeft = 0f
        maxRight = 0f
        emit(force = true)
    }

    fun resetG() {
        maxAccel = 0f; maxBrake = 0f; maxGLeft = 0f; maxGRight = 0f
        last0to100 = null; best0to100 = null
        emit(force = true)
    }

    /** Νέο σημείο GPS: επιτάχυνση από την αλλαγή ταχύτητας, διόρθωση αισθητήρων, και 0–100. */
    private fun onSpeedFix(nowMs: Long) {
        val v = speedMps
        val pv = prevSpeed
        val dt = (nowMs - prevFixMs) / 1000.0
        if (pv != null && dt in 0.2..3.0) {
            val g = (v - pv) / dt / LeanMath.G
            gpsLon = gpsLon + 0.5 * (g - gpsLon)
            if (accFwdN > 0) {
                val avg = accFwdSum / accFwdN / LeanMath.G
                bias += 0.3 * (g - avg - bias)
            }
        }
        accFwdSum = 0.0; accFwdN = 0

        // 0–100: ξεκινάει όταν φεύγεις από στάση, σταματάει στα 100 km/h.
        val target = 100 / 3.6
        when {
            v < 0.8 -> { stoppedAtMs = nowMs; runStartMs = null }
            // Πότε ξεκίνησες: ανάμεσα στο τελευταίο σημείο σε στάση και σε αυτό.
            runStartMs == null && stoppedAtMs != null && pv != null && pv < 0.8 ->
                runStartMs = LeanMath.crossTime(prevFixMs, pv, nowMs, v, 0.3)
        }
        val start = runStartMs
        if (start != null && v >= 0.8) {
            if (pv != null && v >= target) {
                val t = (LeanMath.crossTime(prevFixMs, pv, nowMs, v, target) - start) / 1000f
                if (t in 1.5f..30f) {
                    last0to100 = t
                    best0to100 = best0to100?.let { minOf(it, t) } ?: t
                }
                runStartMs = null; stoppedAtMs = null
            } else if ((pv != null && v < pv - 3.0) || nowMs - start > 30_000) {
                // Έκοψες ή άργησε πολύ: δεν μετράει.
                runStartMs = null; stoppedAtMs = null
            }
        }
        prevSpeed = v
        prevFixMs = nowMs
    }

    private fun updateG() {
        val now = SystemClock.elapsedRealtime()
        val sensorOk = now - accFwdAt < 500 && gpsBearing != null
        val raw = when {
            speedMps < 0.8 && (prevSpeed ?: 0.0) < 0.8 -> 0.0
            sensorOk -> accFwd / LeanMath.G + bias
            else -> gpsLon
        }
        gLon += 0.3 * (raw.coerceIn(-2.0, 2.0) - gLon)
        val lat = LeanMath.lateralG(lean)
        if (speedMps > 3.0) {
            val lo = gLon.toFloat(); val la = lat.toFloat()
            if (lo > maxAccel) maxAccel = lo
            if (-lo > maxBrake) maxBrake = -lo
            if (la > maxGRight) maxGRight = la
            if (-la > maxGLeft) maxGLeft = -la
        }
    }

    fun onLocation(loc: Location) {
        speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0
        gpsBearing = if (loc.hasBearing() && speedMps > 3.0) loc.bearing.toDouble() else null
        altitude = when {
            Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude() -> loc.mslAltitudeMeters
            loc.hasAltitude() -> loc.altitude
            else -> null
        }
        // Κλίση δρόμου: υψόμετρο (βαρόμετρο αν υπάρχει) σε σχέση με την απόσταση που διένυσες.
        val prev = lastLoc
        lastLoc = loc
        if (prev != null && speedMps > 2.0) {
            val d = prev.distanceTo(loc).toDouble()
            if (d < 100) traveled += d
            val alt = baroAlt ?: altitude
            if (alt != null) {
                gradeSamples.addLast(traveled to alt)
                while (gradeSamples.size > 2 && traveled - gradeSamples.first().first > 120) gradeSamples.removeFirst()
                LeanMath.gradePct(gradeSamples.toList())?.let { g ->
                    grade = grade?.let { it + 0.35 * (g - it) } ?: g
                }
            }
        }
        onSpeedFix(SystemClock.elapsedRealtime())
        emit(force = true)
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rGame, e.values)
                hasGame = true
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rMag, e.values)
                hasMag = true
                compass = compassFrom(rMag)
            }
            Sensor.TYPE_PRESSURE -> {
                val a = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, e.values[0]).toDouble()
                baroAlt = baroAlt?.let { it + 0.1 * (a - it) } ?: a
                return
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!hasAcc) {
                    for (i in 0..2) acc[i] = e.values[i].toDouble()
                    hasAcc = true
                } else {
                    for (i in 0..2) acc[i] += 0.04 * (e.values[i] - acc[i])
                }
                // Μπρος–πίσω: επιτάχυνση στον κόσμο (ανατολή/βορράς, με πυξίδα), πάνω στην κατεύθυνση του GPS.
                // Η βαρύτητα είναι κατακόρυφη, άρα δεν μπαίνει στο οριζόντιο.
                val b = gpsBearing
                if (hasMag && b != null) {
                    val ax = e.values[0].toDouble(); val ay = e.values[1].toDouble(); val az = e.values[2].toDouble()
                    val east = rMag[0] * ax + rMag[1] * ay + rMag[2] * az
                    val north = rMag[3] * ax + rMag[4] * ay + rMag[5] * az
                    val rad = Math.toRadians(b)
                    val f = east * kotlin.math.sin(rad) + north * kotlin.math.cos(rad)
                    accFwd += 0.12 * (f - accFwd)
                    accFwdSum += accFwd; accFwdN++
                    accFwdAt = SystemClock.elapsedRealtime()
                }
                updateG()
                emit()
                return
            }
            Sensor.TYPE_GYROSCOPE -> {
                val wz = if (hasAcc) {
                    LeanMath.yawRate(
                        e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble(),
                        acc[0], acc[1], acc[2],
                    )
                } else {
                    // Χωρίς επιταχυνσιόμετρο: κατακόρυφος από τη σύντηξη (υποεκτιμά σε μεγάλες κλίσεις).
                    val r = when {
                        hasGame -> rGame
                        hasMag -> rMag
                        else -> return
                    }
                    r[6] * e.values[0] + r[7] * e.values[1] + r[8] * e.values[2].toDouble()
                }
                yawRate += 0.08 * (wz - yawRate)
                val target = LeanMath.leanDeg(speedMps, yawRate)
                lean += 0.25 * (target - lean)
                if (abs(lean) < 0.5) lean = 0.0
                val l = lean.toFloat()
                if (l < 0 && -l > maxLeft) maxLeft = -l
                if (l > 0 && l > maxRight) maxRight = l
            }
        }
        emit()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Πού «κοιτάει» η μηχανή: η πίσω πλευρά του κινητού (-z), ή η πάνω πλευρά (y) αν το κινητό είναι ξαπλωτό. */
    private fun compassFrom(r: FloatArray): Double {
        val backE = -r[2].toDouble()
        val backN = -r[5].toDouble()
        val backUp = -r[8].toDouble()
        return if (abs(backUp) < 0.85) {
            LeanMath.headingDeg(backE, backN)
        } else {
            LeanMath.headingDeg(r[1].toDouble(), r[4].toDouble())
        }
    }

    private fun emit(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastEmit < 100) return
        lastEmit = now
        onChange(
            Instruments(
                leanDeg = lean.toFloat(),
                maxLeft = maxLeft,
                maxRight = maxRight,
                headingDeg = (gpsBearing ?: compass)?.toFloat(),
                altitudeM = altitude,
                leanAvailable = available,
                gradePct = grade,
                gLon = gLon.toFloat(),
                gLat = LeanMath.lateralG(lean).toFloat(),
                maxAccel = maxAccel,
                maxBrake = maxBrake,
                maxGLeft = maxGLeft,
                maxGRight = maxGRight,
                runStartMs = runStartMs,
                last0to100 = last0to100,
                best0to100 = best0to100,
            )
        )
    }
}
