package app.motonav.car

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.motonav.R
import app.motonav.nav.NavSession
import app.motonav.nav.Phrases
import app.motonav.routing.Turn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Η κύρια οθόνη στο Android Auto: χάρτης, ταμπέλα στροφής, χρόνος άφιξης, κουμπιά. */
class CarNavScreen(carContext: CarContext) : Screen(carContext) {
    private val session: NavSession = CarBridge.session(carContext, lifecycleScope)
    private val renderer = CarMapRenderer(carContext).also { it.session = session }
    private val navManager = carContext.getCarService(NavigationManager::class.java)
    private var started = false

    private val gps = LocationListener { loc: Location ->
        // Αν το κινητό στέλνει ήδη στίγματα στην ίδια πλοήγηση, δεν στέλνουμε διπλά.
        if (CarBridge.ownsSession(session) || session.lastFixAgeMs > 2500) session.onLocation(loc)
        renderer.update()
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        navManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                session.stop()
                invalidate()
            }
        })
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                startGps()
                // Έλεγχος κάθε δευτερόλεπτο (δουλεύει και χωρίς ανοιχτή την εφαρμογή στο κινητό).
                lifecycleScope.launch {
                    var last: List<Any?>? = null
                    while (true) {
                        val key = listOf(
                            session.navigating, session.route, session.destination, session.destinationName,
                            session.planning, session.arrived, session.muted, session.error,
                            session.state?.next, session.state?.distToNextM?.let { (it / 10).roundToInt() },
                            session.state?.remainingS?.let { (it / 60).roundToInt() },
                        )
                        if (key != last) {
                            last = key
                            syncNavigationState()
                            renderer.update()
                            invalidate()
                        }
                        delay(1000)
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                runCatching { carContext.getSystemService(LocationManager::class.java).removeUpdates(gps) }
                if (started) navManager.navigationEnded()
                renderer.release()
                CarBridge.release()
            }
        })
    }

    private fun startGps() {
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            carContext.getSystemService(LocationManager::class.java)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gps, Looper.getMainLooper())
        }
    }

    /** Λέμε στο Android Auto πότε πλοηγούμαστε (για να μη μιλάνε δύο εφαρμογές μαζί). */
    private fun syncNavigationState() {
        if (session.navigating && !started) {
            started = true
            runCatching { navManager.navigationStarted() }
        } else if (!session.navigating && started) {
            started = false
            runCatching { navManager.navigationEnded() }
        }
    }

    /** Στο Android Auto μόνο ένα κουμπί της μπάρας μπορεί να έχει κείμενο· τα άλλα είναι εικονίδια. */
    private fun action(title: String, onClick: () -> Unit): Action =
        Action.Builder().setTitle(title).setOnClickListener { onClick(); invalidate() }.build()

    private fun iconAction(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { onClick(); invalidate() }
            .build()

    private fun openDestinations() {
        screenManager.push(CarDestinationsScreen(carContext) { place -> session.pick(place.latLon, place.name) })
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
        val route = session.route
        when {
            session.navigating -> {
                strip.addAction(action("Τέλος") { session.stop() })
                strip.addAction(iconAction(if (session.muted) R.drawable.ic_car_mute else R.drawable.ic_car_sound) { session.toggleMute() })
            }
            route != null -> {
                strip.addAction(action("Ξεκίνα") { session.start() })
                strip.addAction(iconAction(R.drawable.ic_car_close) { session.clear() })
                strip.addAction(iconAction(R.drawable.ic_car_place) { openDestinations() })
            }
            else -> strip.addAction(action("Προορισμοί") { openDestinations() })
        }
        val b = NavigationTemplate.Builder().setActionStrip(strip.build())

        val st = session.state
        val next = st?.next
        when {
            session.navigating && session.arrived -> b.setNavigationInfo(MessageInfo.Builder("Έφτασες!").build())
            session.navigating && st != null && next != null -> {
                val road = Phrases.road(next)
                val step = Step.Builder(listOfNotNull(Phrases.action(next).replaceFirstChar { it.uppercase() }, road).joinToString(" · "))
                    .setManeuver(maneuver(next))
                    .apply { road?.let { setRoad(it) } }
                    .build()
                b.setNavigationInfo(RoutingInfo.Builder().setCurrentStep(step, distance(st.distToNextM)).build())
                val remainingS = st.remainingS * session.trafficFactor
                b.setDestinationTravelEstimate(
                    TravelEstimate.Builder(
                        distance(st.remainingM),
                        DateTimeWithZone.create(System.currentTimeMillis() + (remainingS * 1000).toLong(), TimeZone.getDefault()),
                    ).setRemainingTimeSeconds(remainingS.toLong()).build(),
                )
            }
            session.navigating || session.planning -> b.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
            route != null -> {
                val min = (route.durationS * session.trafficFactor / 60).roundToInt()
                val time = if (min >= 60) "${min / 60} ώ ${min % 60} λ" else "$min λεπτά"
                b.setNavigationInfo(
                    MessageInfo.Builder(session.destinationName ?: "Προορισμός")
                        .setText("$time · ${String.format(java.util.Locale("el", "GR"), "%.1f", route.lengthM / 1000)} km — πάτα «Ξεκίνα»")
                        .build(),
                )
            }
            session.error != null -> b.setNavigationInfo(MessageInfo.Builder("Δεν βγήκε διαδρομή").setText(session.error?.message ?: "").build())
            else -> b.setNavigationInfo(MessageInfo.Builder("Moto Nav").setText("Πάτα «Προορισμοί» για να διαλέξεις πού πας").build())
        }
        return b.build()
    }

    private fun distance(m: Double): Distance = when {
        m < 1000 -> Distance.create(((m / 10).roundToInt() * 10).toDouble(), Distance.UNIT_METERS)
        else -> Distance.create((m / 100).roundToInt() / 10.0, Distance.UNIT_KILOMETERS_P1)
    }

    private fun maneuver(m: app.motonav.routing.Maneuver): Maneuver {
        val roundabout = m.turn == Turn.ROUNDABOUT || m.turn == Turn.ROUNDABOUT_LEFT
        val type = when (m.turn) {
            Turn.STRAIGHT -> Maneuver.TYPE_STRAIGHT
            Turn.LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT
            Turn.SLIGHT_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT
            Turn.SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
            Turn.RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT
            Turn.SLIGHT_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
            Turn.SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
            Turn.KEEP_LEFT -> Maneuver.TYPE_KEEP_LEFT
            Turn.KEEP_RIGHT -> Maneuver.TYPE_KEEP_RIGHT
            Turn.UTURN -> Maneuver.TYPE_U_TURN_LEFT
            Turn.EXIT_LEFT -> Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT
            Turn.EXIT_RIGHT -> Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT
            Turn.ROUNDABOUT, Turn.ROUNDABOUT_LEFT ->
                if (m.exit > 0) Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW else Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
            Turn.ARRIVE -> Maneuver.TYPE_DESTINATION
        }
        return Maneuver.Builder(type)
            .setIcon(CarIcon.Builder(IconCompat.createWithBitmap(arrow(m.turn, m.exit))).build())
            .apply { if (roundabout && m.exit > 0) setRoundaboutExitNumber(m.exit) }
            .build()
    }

    /** Απλό λευκό βέλος για την ταμπέλα του αυτοκινήτου. */
    private fun arrow(turn: Turn, exit: Int): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val cx = size / 2f
        if (turn == Turn.ARRIVE) {
            p.style = Paint.Style.FILL
            cv.drawCircle(cx, cx, 30f, p)
            return bmp
        }
        if (turn == Turn.ROUNDABOUT || turn == Turn.ROUNDABOUT_LEFT) {
            cv.drawCircle(cx, cx - 6, 26f, p)
            cv.drawLine(cx, size - 10f, cx, cx + 20, p)
            if (exit > 0) {
                p.style = Paint.Style.FILL
                p.strokeWidth = 0f
                p.textSize = 34f
                p.textAlign = Paint.Align.CENTER
                cv.drawText(exit.toString(), cx, cx + 6, p)
            }
            return bmp
        }
        val deg = when (turn) {
            Turn.LEFT -> -90.0; Turn.SLIGHT_LEFT -> -45.0; Turn.SHARP_LEFT -> -135.0
            Turn.RIGHT -> 90.0; Turn.SLIGHT_RIGHT -> 45.0; Turn.SHARP_RIGHT -> 135.0
            Turn.KEEP_LEFT -> -20.0; Turn.KEEP_RIGHT -> 20.0
            Turn.EXIT_LEFT -> -35.0; Turn.EXIT_RIGHT -> 35.0
            Turn.UTURN -> 180.0
            else -> 0.0
        }
        val midY = 66f
        val path = Path().apply { moveTo(cx, size - 10f); lineTo(cx, midY) }
        val rad = Math.toRadians(deg)
        val len = if (turn == Turn.UTURN) 0f else 46f
        val ex = cx + (sin(rad) * len).toFloat()
        val ey = midY - (cos(rad) * len).toFloat()
        if (turn == Turn.UTURN) {
            path.quadTo(cx, 18f, cx - 36, 30f)
            path.lineTo(cx - 36, 70f)
            cv.drawPath(path, p)
            drawHead(cv, p, cx - 36, 78f, 180.0)
        } else {
            path.lineTo(ex, ey)
            cv.drawPath(path, p)
            drawHead(cv, p, ex, ey, deg)
        }
        return bmp
    }

    private fun drawHead(cv: Canvas, p: Paint, x: Float, y: Float, deg: Double) {
        val rad = Math.toRadians(deg)
        val back = 26f
        for (side in listOf(-1, 1)) {
            val a = rad + side * Math.toRadians(145.0)
            cv.drawLine(x, y, x + (sin(a) * back).toFloat(), y - (cos(a) * back).toFloat(), p)
        }
    }
}
