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
)

object LeanMath {
    private const val G = 9.80665

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
    private var lean = 0.0
    private var compass: Double? = null

    private var speedMps = 0.0
    private var gpsBearing: Double? = null
    private var altitude: Double? = null
    private var maxLeft = 0f
    private var maxRight = 0f
    private var lastEmit = 0L

    private val available = gyro != null && (gameRot != null || magRot != null)

    fun start() {
        val s = sm ?: return
        gameRot?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magRot?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
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
            Sensor.TYPE_GYROSCOPE -> {
                val r = when {
                    hasGame -> rGame
                    hasMag -> rMag
                    else -> return
                }
                // Τρίτη γραμμή του πίνακα = κατακόρυφος άξονας του κόσμου.
                val wz = r[6] * e.values[0] + r[7] * e.values[1] + r[8] * e.values[2]
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
            )
        )
    }
}
