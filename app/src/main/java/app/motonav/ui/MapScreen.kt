@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.aspectRatio
import app.motonav.ui.theme.Tokens
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import app.motonav.group.VoiceRecorder
import app.motonav.group.NavShare
import app.motonav.group.GroupLink
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalClipboardManager
import app.motonav.routing.Turn
import app.motonav.routing.Lane
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.onSizeChanged
import app.motonav.search.Place
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import android.Manifest
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import android.os.Build
import app.motonav.nav.NavBus
import app.motonav.nav.NavService
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import app.motonav.cameras.CameraAlerts
import app.motonav.cameras.CameraClient
import app.motonav.cameras.CameraStore
import app.motonav.cameras.SpeedCamera
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import app.motonav.data.DisplayPrefs
import app.motonav.routing.RouteType
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.motonav.data.SettingsRepo
import app.motonav.sensors.InstrumentEngine
import app.motonav.sensors.Instruments
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.OnLocationCameraTransitionListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star as OutlinedStar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import app.motonav.fuel.FuelOnRoute
import app.motonav.fuel.FuelOnRouteStop
import app.motonav.fuel.FuelStation
import app.motonav.hazards.Hazard
import app.motonav.hazards.HazardKind
import app.motonav.hazards.HazardStore
import app.motonav.hazards.Hazards
import app.motonav.routing.ElevationProfile
import app.motonav.weather.Sky
import app.motonav.weather.WeatherPoint
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.platform.LocalView
import app.motonav.R
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.nav.NavSession
import app.motonav.nav.NavState
import app.motonav.nav.Phrases
import app.motonav.routing.BRouterClient
import app.motonav.routing.Route
import app.motonav.routing.RoutingException
import app.motonav.ui.theme.AppColors
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Space
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Προσωρινά online χάρτες OpenFreeMap (OpenStreetMap, δωρεάν, χωρίς κλειδί).
// Οι διαδρομές είναι ήδη offline (BRouter). Ο offline χάρτης έρχεται μετά.
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"

/** Μεγέθη για όρθια και πλάγια οθόνη. Στην πλάγια όλα πιο μικρά, για να φαίνεται ο χάρτης. */
data class Sizes(
    val button: Dp,
    val speedBox: Dp,
    val speedText: TextStyle,
    val maneuverIcon: Dp,
    val maneuverText: TextStyle,
    val panelPadding: Dp,
    val sideWidth: Dp,
    /** Στρογγυλά όργανα (πυξίδα, υψόμετρο). */
    val dial: Dp,
    /** Μικρά κουμπιά (σίγαση, κέντραρε, αναζήτηση, ρυθμίσεις στην όρθια). */
    val smallButton: Dp,
)

private val Portrait = Sizes(
    button = 56.dp,
    speedBox = 76.dp,
    speedText = MotoType.speed.copy(fontSize = 38.sp, lineHeight = 40.sp),
    maneuverIcon = 64.dp,
    maneuverText = MotoType.maneuver.copy(fontSize = 34.sp, lineHeight = 38.sp),
    panelPadding = Space.md,
    sideWidth = Dp.Unspecified,
    dial = 56.dp,
    smallButton = 48.dp,
)

private val Landscape = Sizes(
    button = 56.dp,
    speedBox = 80.dp,
    speedText = MotoType.speed.copy(fontSize = 38.sp, lineHeight = 40.sp),
    maneuverIcon = 52.dp,
    maneuverText = MotoType.maneuver.copy(fontSize = 28.sp, lineHeight = 32.sp),
    panelPadding = Space.sm,
    sideWidth = 300.dp,
    dial = 56.dp,
    smallButton = 48.dp,
)

private val LocalSizes = staticCompositionLocalOf { Portrait }

/** S01 Χάρτης + S03/S04 προορισμός και διαδρομή + S05 πλοήγηση (πρώτη έκδοση). */
@Composable
fun MapScreen(
    display: DisplayPrefs,
    onOpenSettings: () -> Unit,
    shownTrack: List<LatLon>? = null,
    onClearTrack: () -> Unit = {},
    pip: Boolean = false,
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val session = remember { NavSession(context, scope) }
    // Το Android Auto δείχνει την ίδια πλοήγηση με το κινητό.
    DisposableEffect(session) {
        app.motonav.car.CarBridge.phoneSession = session
        onDispose { if (app.motonav.car.CarBridge.phoneSession === session) app.motonav.car.CarBridge.phoneSession = null }
    }
    DisposableEffect(Unit) { onDispose { session.dispose() } }

    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasLocation = result.values.any { it } }
    val requestLocation = {
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) { if (!hasLocation) requestLocation() }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Μπαταρία: στο παρασκήνιο GPS μόνο αν πλοηγείσαι ή μοιράζεσαι θέση στην παρέα.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val gps = rememberGps(hasLocation, active = foreground || session.navigating || session.recordingFree || GroupLink.code != null)
    // Οθόνη αναμμένη μόνο στην πλοήγηση ή όταν κινείσαι (και 3 λεπτά μετά). Αλλιώς σβήνει κανονικά.
    val view = LocalView.current
    var movingAt by remember { mutableStateOf(0L) }
    LaunchedEffect(gps.value.speedKmh) {
        if ((gps.value.speedKmh ?: 0f) >= 10f) movingAt = SystemClock.elapsedRealtime()
    }
    LaunchedEffect(session.navigating, movingAt) {
        if (session.navigating) { view.keepScreenOn = true; return@LaunchedEffect }
        val left = KEEP_ON_AFTER_MOVING_MS - (SystemClock.elapsedRealtime() - movingAt)
        if (movingAt != 0L && left > 0) {
            view.keepScreenOn = true
            kotlinx.coroutines.delay(left)
        }
        view.keepScreenOn = false
    }
    // GPS κλειστό: το παράθυρο του Android «Ενεργοποίηση τοποθεσίας» (ένα «OK»), αλλιώς οι Ρυθμίσεις τοποθεσίας.
    val gpsDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val turnOnGps: () -> Unit = {
        val openSettings = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        try {
            val request = com.google.android.gms.location.LocationSettingsRequest.Builder()
                .addLocationRequest(
                    com.google.android.gms.location.LocationRequest.Builder(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000L,
                    ).build(),
                )
                .setAlwaysShow(true)
                .build()
            com.google.android.gms.location.LocationServices.getSettingsClient(context)
                .checkLocationSettings(request)
                .addOnFailureListener { e ->
                    if (e is com.google.android.gms.common.api.ResolvableApiException) {
                        runCatching { gpsDialog.launch(androidx.activity.result.IntentSenderRequest.Builder(e.resolution).build()) }
                            .onFailure { openSettings() }
                    } else {
                        openSettings()
                    }
                }
        } catch (_: Throwable) {
            openSettings() // χωρίς Google Play Services
        }
    }
    // Trip A: ξεκινάει από το μηδέν με κάθε πλοήγηση/καταγραφή και σταματάει στο τέλος της.
    var wasRiding by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(session.recording) {
        val prev = wasRiding
        wasRiding = session.recording
        if (prev == false && session.recording) app.motonav.trips.TripMeter.startRide()
        if (prev == true && !session.recording) app.motonav.trips.TripMeter.endRide()
    }
    LaunchedEffect(gps.value.location) { gps.value.location?.let(session::onLocation) }
    // Προσομοίωση: «ψεύτικο» GPS πάνω στη διαδρομή, με το όριο του δρόμου (30–90 km/h) × 1/2/4.
    LaunchedEffect(app.motonav.nav.Simulator.active) {
        if (!app.motonav.nav.Simulator.active) return@LaunchedEffect
        var current: app.motonav.routing.Route? = null
        var along = 0.0
        var pos: LatLon? = null
        while (app.motonav.nav.Simulator.active) {
            val r = session.route
            if (r == null || r.points.size < 2) { kotlinx.coroutines.delay(1_000); continue }
            if (r !== current) {
                along = pos?.let { app.motonav.routing.OnRoute.project(r, it)?.alongM } ?: 0.0
                current = r
            }
            val kmh = (r.speedLimitAt(along) ?: 50).coerceIn(30, 90).toDouble()
            along = (along + kmh / 3.6 * app.motonav.nav.Simulator.speedUp).coerceAtMost(r.lengthM)
            val p = r.pointAt(along)
            val ahead = r.pointAt((along + 20.0).coerceAtMost(r.lengthM))
            pos = p
            val loc = Location("sim").apply {
                latitude = p.lat
                longitude = p.lon
                if (Geo.distance(p, ahead) > 1.0) bearing = Geo.bearing(p, ahead).toFloat()
                speed = (kmh / 3.6).toFloat()
                accuracy = 4f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            gps.value = gps.value.copy(enabled = true, location = loc, speedKmh = kmh.toFloat())
            kotlinx.coroutines.delay(1_000)
        }
    }

    val mapView = rememberMapViewWithLifecycle()
    // fps χάρτη: 30 στην οδήγηση, όσο πάει η οθόνη (60/120) σταματημένος, 20 με ζέστη ή χαμηλή μπαταρία.
    val battery = rememberBattery()
    val hot = rememberThermalHot()
    val riding = session.navigating || (gps.value.speedKmh ?: 0f) >= 10f
    val screenHz = remember(view) {
        runCatching { view.display?.supportedModes?.maxOf { it.refreshRate }?.toInt() }.getOrNull()?.coerceIn(30, 144) ?: 60
    }
    // Μετά από «Ξεκίνα», «κέντραρε» ή αλλαγή βορρά: πλήρη Hz για λίγα δευτερόλεπτα, ώσπου να τελειώσει
    // η κίνηση της κάμερας (ζουμ, κλίση, περιστροφή)· μετά πέφτει στα 30 της οδήγησης.
    var fpsBoost by remember { mutableStateOf(false) }
    var fpsBoostKey by remember { mutableStateOf(0) }
    LaunchedEffect(fpsBoostKey) {
        if (fpsBoostKey == 0) return@LaunchedEffect
        fpsBoost = true
        kotlinx.coroutines.delay(4_000)
        fpsBoost = false
    }
    LaunchedEffect(session.navigating) { if (session.navigating) fpsBoostKey++ }
    val mapFps = when {
        // Εξοικονόμηση: λιγότερα καρέ (το βελάκι κινείται λίγο πιο «βηματικά», το κινητό ζεσταίνεται πολύ λιγότερο).
        display.powerSave -> if (hot) 12 else if (riding) 15 else 24
        hot || (battery.percent < 20 && !battery.charging) -> 20
        fpsBoost -> screenHz
        riding -> 30
        else -> screenHz
    }
    LaunchedEffect(mapFps) { mapView.setMaximumFps(mapFps) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var centered by remember { mutableStateOf(false) }
    var trackingListenerAdded by remember { mutableStateOf(false) }
    var trackingDismissedAt by remember { mutableStateOf(0L) }
    var lastCompassPush by remember { mutableStateOf(0L) }
    val styleUrl = if (c.base.isDark) STYLE_DARK else STYLE_LIGHT

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sizes = if (landscape) Landscape else Portrait
    val density = LocalDensity.current.density

    val repo = remember { SettingsRepo(context.applicationContext) }
    val recents by repo.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val homePlace by repo.home.collectAsStateWithLifecycle(initialValue = null)
    val markerPrefs by repo.marker.collectAsStateWithLifecycle(initialValue = MarkerPrefs())
    val workPlace by repo.work.collectAsStateWithLifecycle(initialValue = null)
    val favorites by repo.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val favLists = remember(favorites) { favorites.groupBy { it.listName }.map { (n, ps) -> n to (ps.firstOrNull { it.color != 0 }?.color ?: 0) } }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val (instruments, resetMax) = rememberInstruments(gps.value.location, active = foreground || session.navigating || session.recordingFree)

    // ---- Κάμερες
    val cameraStore = remember { CameraStore(context) }
    val cameraClient = remember { CameraClient() }
    val bundledCameras = remember { cameraStore.loadBundled() }
    var cameras by remember { mutableStateOf(cameraStore.merge(cameraStore.loadOsm(), cameraStore.loadMine(), bundledCameras + app.motonav.data.ImportedPois.cameras())) }
    // Κάμερες από εισαγμένα αρχεία (π.χ. 4GPS): μπαίνουν/βγαίνουν μόλις αλλάξουν
    LaunchedEffect(app.motonav.data.ImportedPois.version) {
        cameras = cameraStore.merge(cameraStore.loadOsm(), cameraStore.loadMine(), bundledCameras + app.motonav.data.ImportedPois.cameras())
    }
    var camFetchedAt by remember { mutableStateOf<LatLon?>(null) }
    var camFailAt by remember { mutableStateOf(0L) }
    var cameraAlert by remember { mutableStateOf<Pair<SpeedCamera, Double>?>(null) }
    val announcedCams = remember { mutableSetOf<String>() }
    var cameraMarkedAt by remember { mutableStateOf(0L) }
    var hasMoved by remember { mutableStateOf(false) }
    var cameraTilt by remember { mutableStateOf(0) }
    // Ορατή περιοχή (νότος, δύση, βορράς, ανατολή, ζουμ) μετά από κάθε μετακίνηση, για τα μέρη μηχανόβιων.
    var visibleArea by remember { mutableStateOf<DoubleArray?>(null) }
    val spotCache = remember { app.motonav.spots.SpotCache(java.io.File(context.filesDir, "spots")) }
    var spotsLoading by remember { mutableStateOf(false) }
    // Το μέρος που πάτησες (για να δείξει φωτογραφία στην κάρτα).
    var selectedSpot by remember { mutableStateOf<app.motonav.spots.Spot?>(null) }
    var spots by remember { mutableStateOf<List<app.motonav.spots.Spot>>(emptyList()) }
    var weatherOpen by remember { mutableStateOf(false) }
    // Παρέα: ποιον πάτησες στον χάρτη, και αν ηχογραφείς τώρα.
    var selectedFriend by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    // Ύψος της κάρτας προορισμού στην όρθια προβολή, για να χωράει η διαδρομή στον χάρτη που μένει ελεύθερος.
    var bottomPanelPx by remember { mutableStateOf(0) }
    var shownLoc by remember { mutableStateOf<Location?>(null) }
    val puckFilter = remember { PuckFilter() }
    var wasSnapped by remember { mutableStateOf(false) }
    val snapInfo = remember { SnapInfo() }
    var lastMovingBearing by remember { mutableStateOf<Float?>(null) }
    // Δική σου κάμερα που πάτησες: περιμένει επιβεβαίωση για σβήσιμο.
    var cameraToDelete by remember { mutableStateOf<String?>(null) }
    // Κλίση δρόμου: στην πλοήγηση από το προφίλ της διαδρομής (χωρίς θόρυβο), αλλιώς από τους αισθητήρες.
    val speedLimitNow = session.state?.let { st -> session.route?.speedLimitAt(st.progressM) }?.takeIf { session.navigating }
    val gradeNow = session.state?.let { st -> session.route?.gradeAt(st.progressM) }
        ?.takeIf { session.navigating } ?: instruments.gradePct

    // ---- Βενζίνη στη διαδρομή (OpenStreetMap)
    var fuelRaw by remember { mutableStateOf<List<FuelStation>>(emptyList()) }
    var fuelOnRoute by remember { mutableStateOf<List<FuelOnRouteStop>>(emptyList()) }
    var fuelShown by remember { mutableStateOf(false) }
    var stopsOpen by remember { mutableStateOf(false) }
    var fuelLoading by remember { mutableStateOf(false) }
    // Το Overpass δεν απάντησε (μετά από 3 προσπάθειες). Πάτημα στο ⛽ = ξανά.
    var fuelFailed by remember { mutableStateOf(false) }
    var fuelRetry by remember { mutableStateOf(0) }
    var fuelOpen by remember { mutableStateOf(false) }
    val progressNow = if (session.navigating) session.state?.progressM ?: 0.0 else 0.0
    val fuelAhead = FuelOnRoute.ahead(fuelOnRoute, progressNow)
    val elevation = remember(session.route) { session.route?.let { ElevationProfile.of(it) } }

    // ---- Αναφορές κινδύνου (μόνο στο κινητό σου)
    val hazardStore = remember { HazardStore(context.filesDir) }
    var hazards by remember { mutableStateOf(hazardStore.load()) }
    var reportOpen by remember { mutableStateOf(false) }
    // Γρήγορες ενέργειες (μετρητής ταξιδιού, πλακίδια, διακόπτες) και αναφορά χάρτη στο OSM
    var quickOpen by remember { mutableStateOf(false) }
    var appsOpen by remember { mutableStateOf(false) }
    // Αποτελέσματα κατηγορίας της αναζήτησης (π.χ. Καφές): αριθμημένοι κύκλοι στον χάρτη
    var categoryPins by remember { mutableStateOf<List<app.motonav.search.Place>>(emptyList()) }
    // Μετά από πάτημα στον χάρτη: η κατηγορία μένει στον χάρτη χωρίς το φύλλο (πάτημα σε σημείο = προορισμός)
    var browse by remember { mutableStateOf<Pair<String, List<app.motonav.search.Place>>?>(null) }
    val shownPins = if (categoryPins.isNotEmpty()) categoryPins else browse?.second.orEmpty()
    LaunchedEffect(session.navigating) { if (session.navigating) browse = null }
    var mapReportOpen by remember { mutableStateOf(false) }
    // Μετρητής ταξιδιού A/B: κάθε στίγμα (με ή χωρίς πλοήγηση)
    LaunchedEffect(gps.value.location) { gps.value.location?.let { if (!app.motonav.nav.Simulator.active) app.motonav.trips.TripMeter.add(it) } }
    // Επόμενα σημεία στη διαδρομή (offline): υπολογίζονται μία φορά ανά διαδρομή
    val routePoiKinds = remember(display.nextPois) { app.motonav.routing.RoutePoiKind.entries.filter { it.name in display.nextPois } }
    val routePois by androidx.compose.runtime.produceState(emptyList<app.motonav.routing.RoutePoi>(), session.route, routePoiKinds) {
        val r = session.route
        value = if (r == null || routePoiKinds.isEmpty()) emptyList()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { app.motonav.routing.RoutePois.along(r, routePoiKinds) }.getOrDefault(emptyList()) }
    }
    val nextPois = if (session.navigating) app.motonav.routing.RoutePois.next(routePois, progressNow) else emptyList()
    var reportedAt by remember { mutableStateOf(0L) }
    var hazardAlert by remember { mutableStateOf<Pair<Hazard, Double>?>(null) }
    val announcedHazards = remember { mutableSetOf<String>() }
    var hazardToDelete by remember { mutableStateOf<String?>(null) }
    var noGoToDelete by remember { mutableStateOf<app.motonav.routing.NoGo?>(null) }
    val recenterRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Το μικρόφωνο της παρέας μόνο όταν είσαι σε παρέα με κάποιον ακόμα (ή αν είναι ήδη ανοιχτό, για να το κλείσεις).
    val groupActive = GroupLink.code != null && (GroupLink.micOn || GroupLink.members.any { it.uid != GroupLink.myUid })

    // ---- Όριο ταχύτητας: άκρες οθόνης και φωνή
    val speedNow = gps.value.speedKmh ?: 0f
    val overspeed = speedLimitNow != null && speedNow > speedLimitNow + 3

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.also { mv ->
                    mv.getMapAsync { m ->
                        // Ξεκινάμε από την Ελλάδα, όχι από τον ωκεανό στο 0,0.
                        m.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(38.6, 23.5))
                            .zoom(6.0)
                            .build()
                        // Χωρίς το λογότυπο MapLibre. Το μικρό (i) μένει: είναι η αναφορά στο OpenStreetMap.
                        m.uiSettings.isLogoEnabled = false
                        // Η αναφορά στο OpenStreetMap είναι στις Ρυθμίσεις → Πηγές και άδειες.
                        m.uiSettings.isAttributionEnabled = false
                        // Έχουμε δική μας πυξίδα κάτω.
                        m.uiSettings.isCompassEnabled = false
                        m.addOnMapClickListener { p ->
                            // Πάτημα στον χάρτη: το pill μουσικής ανεβαίνει και μένει το κουμπάκι.
                            MediaPillState.onMapTap()
                            val pt = m.projection.toScreenLocation(p)
                            val r = 28f * density
                            // Πάτημα σε μέλος της παρέας: πληροφορίες και «πήγαινε εκεί».
                            val friend = m.queryRenderedFeatures(
                                android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                FriendsLayer.L_FRIENDS,
                            ).firstOrNull()?.getStringProperty("uid")
                            if (friend != null) {
                                selectedFriend = friend
                                return@addOnMapClickListener true
                            }
                            // Πάτημα σε εναλλακτική (γραμμή ή συννεφάκι): γίνεται η κύρια.
                            if (!session.navigating && session.alternatives.isNotEmpty()) {
                                val alt = m.queryRenderedFeatures(
                                    android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                    RouteLayers.L_ALT_LABEL, RouteLayers.L_ALT,
                                ).firstOrNull()?.getNumberProperty("idx")?.toInt()
                                if (alt != null) {
                                    session.selectAlternative(alt)
                                    return@addOnMapClickListener true
                                }
                            }
                            // Πάτημα σε σημείο της κατηγορίας (π.χ. καφέ): γίνεται προορισμός, με κάρτα και «Ξεκίνα».
                            if (!session.navigating && browse != null) {
                                val i = m.queryRenderedFeatures(
                                    android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                    CategoryPins.LAYER,
                                ).firstOrNull()?.getNumberProperty("i")?.toInt()
                                val p = i?.let { browse?.second?.getOrNull(it) }
                                if (p != null) {
                                    browse = null
                                    session.pick(p.latLon, p.name)
                                    return@addOnMapClickListener true
                                }
                            }
                            // Πάτημα σε δικό σου σημείο (εισαγμένο αρχείο): γίνεται προορισμός.
                            if (!session.navigating) {
                                val f = m.queryRenderedFeatures(
                                    android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                    PoiLayer.LAYER,
                                ).firstOrNull()
                                val p = f?.let { app.motonav.data.ImportedPois.point(it.getStringProperty("c") ?: "", it.getNumberProperty("i")?.toInt() ?: -1) }
                                if (p != null) {
                                    session.pick(LatLon(p.lat, p.lon), p.name)
                                    return@addOnMapClickListener true
                                }
                            }
                            // Πάτημα σε μέρος μηχανόβιων: γίνεται προορισμός (κάρτα με όνομα, διαδρομή, ★).
                            if (!session.navigating) {
                                val spotId = m.queryRenderedFeatures(
                                    android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                    SpotLayer.LAYER,
                                ).firstOrNull()?.getStringProperty("id")
                                val spot = spots.firstOrNull { it.id == spotId }
                                if (spot != null) {
                                    selectedSpot = spot
                                    session.pick(spot.latLon, spot.name)
                                    return@addOnMapClickListener true
                                }
                            }
                            // Πάτημα σε περιοχή αποφυγής: ρωτάει αν να σβηστεί.
                            val ng = m.queryRenderedFeatures(pt, NoGoLayer.LAYER).firstOrNull()
                            if (ng != null && !session.navigating) {
                                val idx = ng.getNumberProperty("i")?.toInt()
                                noGoToDelete = idx?.let { app.motonav.routing.NoGoAreas.list.getOrNull(it) }
                                if (noGoToDelete != null) return@addOnMapClickListener true
                            }
                            // Πάτημα σε σελιδοδείκτη: διαδρομή εκεί.
                            val bm = m.queryRenderedFeatures(
                                android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                BookmarkLayer.LAYER,
                            ).firstOrNull()
                            if (bm != null && !session.navigating) {
                                val g = bm.geometry() as? org.maplibre.geojson.Point
                                if (g != null) {
                                    session.pick(LatLon(g.latitude(), g.longitude()), bm.getStringProperty("name"))
                                    return@addOnMapClickListener true
                                }
                            }
                            // Πάτημα σε αναφορά κινδύνου: ρωτάει αν να σβηστεί.
                            val hz = m.queryRenderedFeatures(
                                android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                HazardLayer.LAYER,
                            ).firstOrNull()?.getStringProperty("id")
                            if (hz != null) {
                                hazardToDelete = hz
                                return@addOnMapClickListener true
                            }
                            val hit = m.queryRenderedFeatures(
                                android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r),
                                CameraLayer.LAYER,
                            ).firstOrNull { f -> f.getStringProperty("src") == "mine" }
                            cameraToDelete = hit?.getStringProperty("id")
                            hit != null
                        }
                        m.addOnCameraIdleListener {
                            // κλίση κάμερας ανά 5°, για το τέντωμα του βελακιού
                            cameraTilt = (Math.round(m.cameraPosition.tilt / 5.0) * 5).toInt()
                            val b = m.projection.visibleRegion.latLngBounds
                            app.motonav.map.MapBus.visible = doubleArrayOf(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast)
                            visibleArea = doubleArrayOf(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast, m.cameraPosition.zoom)
                        }
                        m.addOnMapLongClickListener { p ->
                            session.pick(LatLon(p.latitude, p.longitude))
                            true
                        }
                        map = m
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        LaunchedEffect(map, styleUrl) {
            val m = map ?: return@LaunchedEffect
            style = null
            m.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
                if (c.base.isDark) MapContrast.apply(loaded)
                Terrain.apply(loaded, c.base.isDark, display.showRelief && !display.powerSave, display.maptilerKey, display.showContours && !display.powerSave, display.showDirt)
                style = loaded
            }
        }
        LaunchedEffect(style, display.showRelief, display.maptilerKey, display.showContours, display.showDirt, display.powerSave, display.navTilt) {
            CameraPrefs.powerSave = display.powerSave
            CameraPrefs.navTilt = display.navTilt.toDouble()
            val s = style ?: return@LaunchedEffect
            // Φωτεινό θέμα (liberty): 3D κτίρια από ζουμ 14. Στην εξοικονόμηση κρύβονται (βαρύ σχέδιο στην κάρτα γραφικών).
            s.getLayer("building-3d")?.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.visibility(
                    if (display.powerSave) org.maplibre.android.style.layers.Property.NONE else org.maplibre.android.style.layers.Property.VISIBLE,
                ),
            )
            Terrain.apply(s, c.base.isDark, display.showRelief && !display.powerSave, display.maptilerKey, display.showContours && !display.powerSave, display.showDirt)
        }
        LaunchedEffect(style, hasLocation, c, markerPrefs) {
            val m = map ?: return@LaunchedEffect
            val s = style ?: return@LaunchedEffect
            if (hasLocation) showMyLocation(context, m, s, c, markerPrefs, cameraTilt.toDouble())
            RouteLayers.marker = markerPrefs
            RouteLayers.install(s, c)
            // Αν η θέση ήρθε πριν φορτώσει ο χάρτης, τη δίνουμε τώρα.
            val loc = gps.value.location ?: gps.value.lastKnown
            if (loc != null && m.locationComponent.isLocationComponentActivated) {
                m.locationComponent.forceLocationUpdate(loc)
            }
        }
        // Πυξίδα μόνο πριν ξεκινήσεις (άνοιγμα εφαρμογής ή «Ξεκίνα»). Μόλις κινηθείς, σε στάση
        // (φανάρι) το βελάκι μένει στην τελευταία κατεύθυνση κίνησης και ο χάρτης δεν γυρίζει.
        LaunchedEffect(session.navigating) {
            if (session.navigating) hasMoved = false
        }
        // Η θέση πηγαίνει κατευθείαν στο βελάκι (δεν περιμένουμε δεύτερο GPS του χάρτη).
        LaunchedEffect(style, gps.value.location, gps.value.lastKnown) {
            val loc0 = gps.value.location
            if (loc0 != null && loc0.hasSpeed() && loc0.speed * 3.6f >= 10f && loc0.hasBearing() && (!loc0.hasAccuracy() || loc0.accuracy <= 20f)) {
                hasMoved = true
                lastMovingBearing = loc0.bearing
            }
            val m = map ?: return@LaunchedEffect
            if (style == null || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
            // Το lastKnown (μπορεί να είναι από δίκτυο, «στον δρόμο») μόνο πριν το πρώτο στίγμα GPS.
            val raw = gps.value.location ?: gps.value.lastKnown?.takeIf { shownLoc == null } ?: return@LaunchedEffect
            // Στην πλοήγηση το βελάκι κάθεται πάνω στη μωβ γραμμή (όχι δίπλα της, στις γραμμές κίνησης).
            val snapped = snapToRoute(raw, session.state?.takeIf { session.navigating }, session.route, wasSnapped, snapInfo)
            wasSnapped = snapped !== raw
            // Μέσα στο σπίτι το GPS «χορεύει» 10–60 m και δίνει και ψεύτικη ταχύτητα. Το PuckFilter κρατάει
            // το βελάκι ακίνητο ώσπου να δει πραγματική κίνηση (καλή ακρίβεια + ταχύτητα σε 2 συνεχόμενα στίγματα).
            val held = shownLoc
            val keep = puckFilter.hold(
                held?.let { it.distanceTo(snapped) },
                if (raw.hasSpeed()) raw.speed * 3.6f else null,
                if (raw.hasAccuracy()) raw.accuracy else null,
            )
            val loc = if (keep && held != null) {
                Location(snapped).apply {
                    latitude = held.latitude
                    longitude = held.longitude
                    bearing = held.bearing
                }
            } else {
                snapped
            }
            shownLoc = loc
            // Στην πλοήγηση με ταχύτητα: όχι εκεί που ήσουν όταν μετρήθηκε το στίγμα, αλλά εκεί που είσαι τώρα πάνω στη γραμμή.
            val now = session.route?.takeIf { session.navigating && wasSnapped }
                ?.let { extrapolateOnRoute(it, snapInfo, SystemClock.elapsedRealtimeNanos(), loc) }
            m.locationComponent.forceLocationUpdate(now ?: withCompassWhenStopped(loc, if (hasMoved) lastMovingBearing else instruments.headingDeg))
            // Η γραμμή ξεκινάει από το ίδιο σημείο με το βελάκι (όχι από το στίγμα): δεν ξεκολλάνε.
            if (session.navigating && wasSnapped && !keep) {
                val r = session.route
                val along = extrapolatedAlong(snapInfo, SystemClock.elapsedRealtimeNanos()) ?: snapInfo.alongM
                if (r != null && along != null) RouteLayers.setRemainingAlong(style!!, r, along)
            }
            if (!centered) {
                centered = true
                m.locationComponent.cameraMode = CameraMode.TRACKING_GPS
                m.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(loc.latitude, loc.longitude)).zoom(15.0).build()
                    )
                )
            }
        }
        // Ανάμεσα στα στίγματα (κάθε 150 ms) το βελάκι προχωράει πάνω στη γραμμή με την ταχύτητά σου.
        LaunchedEffect(session.navigating, style) {
            while (session.navigating) {
                kotlinx.coroutines.delay(150)
                val m = map ?: continue
                if (style == null || !m.locationComponent.isLocationComponentActivated || !wasSnapped) continue
                val r = session.route ?: continue
                val base = shownLoc ?: continue
                val t = SystemClock.elapsedRealtimeNanos()
                val p = extrapolateOnRoute(r, snapInfo, t, base) ?: continue
                m.locationComponent.forceLocationUpdate(p)
                extrapolatedAlong(snapInfo, t)?.let { a -> style?.let { RouteLayers.setRemainingAlong(it, r, a) } }
            }
        }
        LaunchedEffect(display.showAlternatives) { session.showAlternatives = display.showAlternatives }
        LaunchedEffect(display.moreCurves) { session.router.moreCurves = display.moreCurves }
        LaunchedEffect(display.car) {
            app.motonav.routing.Vehicle.car = display.car
            if (display.car && session.routeType == RouteType.DIRT) session.chooseRouteType(RouteType.FASTEST)
        }
        LaunchedEffect(display.avoidFerries, display.avoidMain, display.avoidNarrow, display.avoidUnpaved) {
            val A = app.motonav.routing.Avoid
            val changed = A.ferries != display.avoidFerries || A.mainRoads != display.avoidMain || A.narrow != display.avoidNarrow || A.unpaved != display.avoidUnpaved
            A.ferries = display.avoidFerries
            A.mainRoads = display.avoidMain
            A.narrow = display.avoidNarrow
            A.unpaved = display.avoidUnpaved
            if (changed && !session.navigating && session.destination != null) session.plan()
        }
        LaunchedEffect(style, session.alternatives, session.route, session.navigating) {
            val s = style ?: return@LaunchedEffect
            val alts = if (session.navigating) emptyList() else session.alternatives
            RouteLayers.setAlternatives(s, alts, session.route?.durationS)
            // Χωράμε στην οθόνη και τις εναλλακτικές.
            val m = map
            val r = session.route
            if (alts.isNotEmpty() && m != null && r != null) fitRoute(m, r, landscape, density, bottomPanelPx, alts.map { it.route })
        }
        LaunchedEffect(display.tomtomKey) {
            session.trafficKey = display.tomtomKey
            session.refreshTraffic(force = true)
        }
        // Μέρη μηχανόβιων: κατεβαίνουν ανά κελί ~11 km όταν ο χάρτης σταθεί σε ζουμ 11+.
        LaunchedEffect(visibleArea, display.showSpots) {
            val a = visibleArea ?: return@LaunchedEffect
            if (!display.showSpots || a[4] < 11.0) return@LaunchedEffect
            kotlinx.coroutines.delay(700) // περιμένουμε να σταματήσει το σύρσιμο
            val cells = app.motonav.spots.Spots.cells(a[0], a[1], a[2], a[3])
            if (cells.size > 40) return@LaunchedEffect
            val now = System.currentTimeMillis()
            val missing = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { spotCache.missing(cells, now) }
            if (spotCache.all.size != spots.size) spots = spotCache.all
            if (missing.isEmpty()) return@LaunchedEffect
            val d = app.motonav.spots.Spots.CELL
            val s0 = missing.minOf { it.first } * d
            val w0 = missing.minOf { it.second } * d
            val n0 = (missing.maxOf { it.first } + 1) * d
            val e0 = (missing.maxOf { it.second } + 1) * d
            spotsLoading = true
            val found = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Το Overpass θέλει 30–50″ για μια πόλη· αφήνουμε έως 90″.
                    app.motonav.spots.Spots.parse(app.motonav.net.Overpass.query(app.motonav.spots.Spots.query(s0, w0, n0, e0), 90_000))
                        .also { spotCache.add(missing, it) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                spotsLoading = false
                throw e
            } catch (e: Exception) {
                null
            }
            spotsLoading = false
            if (found == null) spotCache.failed(missing, now) else spots = spotCache.all
        }
        LaunchedEffect(style, shownPins, c) {
            val s = style ?: return@LaunchedEffect
            val m = map ?: return@LaunchedEffect
            runCatching { CategoryPins.set(s, shownPins, c.accent.toArgb(), c.onAccent.toArgb(), c.base.bg.toArgb(), c.base.text.toArgb()) }
            if (shownPins.isEmpty()) return@LaunchedEffect
            // Όλα τα σημεία (και εσύ) στο πάνω μέρος της οθόνης, πάνω από το φύλλο της αναζήτησης.
            if (m.locationComponent.isLocationComponentActivated) m.locationComponent.cameraMode = CameraMode.NONE
            val me = gps.value.location ?: gps.value.lastKnown
            val b = LatLngBounds.Builder().apply {
                shownPins.forEach { include(LatLng(it.lat, it.lon)) }
                me?.let { include(LatLng(it.latitude, it.longitude)) }
            }
            runCatching {
                val update = if (landscape) {
                    CameraUpdateFactory.newLatLngBounds(b.build(), (60 * density).toInt(), (70 * density).toInt(), (60 * density).toInt(), (40 * density).toInt())
                } else {
                    val bottom = if (categoryPins.isNotEmpty()) (m.height * 0.58f).toInt() else (140 * density).toInt()
                    CameraUpdateFactory.newLatLngBounds(b.build(), (50 * density).toInt(), (120 * density).toInt(), (50 * density).toInt(), bottom)
                }
                m.animateCamera(update, 700)
            }
        }
        // Χρώμα κλίσης πάνω στη γραμμή της διαδρομής
        LaunchedEffect(style, session.route, display.slopeColors) {
            val s = style ?: return@LaunchedEffect
            runCatching { SlopeLayer.set(s, session.route, display.slopeColors) }
        }
        // Δικά σου σημεία (εισαγμένα αρχεία POI)
        LaunchedEffect(style, app.motonav.data.ImportedPois.version, c.base.isDark, session.navigating) {
            val s = style ?: return@LaunchedEffect
            runCatching { PoiLayer.set(s, density, c.base.text.toArgb(), c.base.bg.toArgb(), session.navigating) }
        }
        LaunchedEffect(style, spots, display.showSpots, display.hiddenSpots, c.base.isDark) {
            val s = style ?: return@LaunchedEffect
            if (display.showSpots) SpotLayer.set(s, spots.filter { it.kind.name !in display.hiddenSpots }, density, c.base.text.toArgb(), c.base.bg.toArgb())
            else SpotLayer.clear(s)
        }
        // Η κίνηση φαίνεται μόνο ως χρώμα πάνω στη γραμμή της διαδρομής· καθόλου γραμμές δίπλα στους δρόμους.
        LaunchedEffect(style) {
            val s = style ?: return@LaunchedEffect
            TrafficLayer.set(s, null, c.base.isDark)
        }
        LaunchedEffect(style, session.route, session.routeTraffic, display.showTraffic, session.state?.segIndex, session.navigating) {
            val s = style ?: return@LaunchedEffect
            val r = session.route
            val t = session.routeTraffic
            val from = if (session.navigating) session.state?.progressM ?: 0.0 else 0.0
            if (r != null && t.route === r && display.showTraffic) RouteLayers.setTraffic(s, r, t.jams, from)
            else RouteLayers.setTraffic(s, null, emptyList(), 0.0)
        }
        // Σταματημένος: ο χάρτης και το βελάκι γυρίζουν με την πυξίδα (έως 4 φορές το δευτερόλεπτο).
        LaunchedEffect(instruments.headingDeg) {
            val m = map ?: return@LaunchedEffect
            if (style == null || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
            // Μόνο περιστροφή: η θέση είναι αυτή που δείχνει ήδη το βελάκι (φιλτραρισμένη), όχι το ωμό GPS,
            // αλλιώς το βελάκι πηγαινοέρχεται ανάμεσα στις δύο 4 φορές το δευτερόλεπτο.
            val loc = shownLoc ?: return@LaunchedEffect
            val now = SystemClock.elapsedRealtime()
            if (now - lastCompassPush < 250) return@LaunchedEffect
            if (hasMoved || (gps.value.speedKmh ?: 0f) >= STOPPED_KMH || instruments.headingDeg == null) return@LaunchedEffect
            lastCompassPush = now
            m.locationComponent.forceLocationUpdate(withCompassWhenStopped(loc, instruments.headingDeg))
        }
        LaunchedEffect(style, session.route) {
            val s = style ?: return@LaunchedEffect
            RouteLayers.setRoute(s, session.route)
            val r = session.route
            val m = map
            if (r != null && m != null && !session.navigating) fitRoute(m, r, landscape, density, bottomPanelPx)
        }
        // Η κάρτα άλλαξε ύψος (π.χ. μπήκε στάση): ξαναχωράμε τη διαδρομή πάνω από αυτήν.
        LaunchedEffect(bottomPanelPx / 40) {
            val r = session.route
            val m = map
            if (style != null && r != null && m != null && !session.navigating) fitRoute(m, r, landscape, density, bottomPanelPx)
        }
        // Παλιά διαδρομή από το ημερολόγιο.
        LaunchedEffect(style, shownTrack) {
            val s = style ?: return@LaunchedEffect
            TrackLayer.set(s, shownTrack)
            val m = map ?: return@LaunchedEffect
            val t = shownTrack
            if (t != null && t.size >= 2) {
                if (m.locationComponent.isLocationComponentActivated) m.locationComponent.cameraMode = CameraMode.NONE
                val b = LatLngBounds.Builder().apply { t.forEach { include(LatLng(it.lat, it.lon)) } }.build()
                m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(b, (40 * density).toInt()))
            }
        }
        LaunchedEffect(session.arrived) {
            if (session.arrived) {
                kotlinx.coroutines.delay(10_000)
                session.clearArrived()
            }
        }
        // Κλίση στο ημερολόγιο (μέγιστα).
        LaunchedEffect(instruments.leanDeg) {
            if (session.recording && !session.recordingPaused) session.recordLean(instruments.leanDeg)
        }
        // Γύρισες το κινητό στην πλοήγηση: ξαναβάζουμε το βελάκι στο 70% του νέου ύψους.
        LaunchedEffect(landscape) {
            val m = map ?: return@LaunchedEffect
            if (session.navigating && style != null && m.locationComponent.isLocationComponentActivated) {
                kotlinx.coroutines.delay(300)
                centerOnMe(m, true)
            }
        }
        // Παρέα: στέλνουμε τη θέση (και την πλοήγηση) και δείχνουμε τους άλλους.
        LaunchedEffect(gps.value.location) {
            val loc = gps.value.location ?: return@LaunchedEffect
            if (GroupLink.code == null) return@LaunchedEffect
            val r = session.route
            val d = session.destination
            val st = session.state
            val share = if (session.navigating && r != null && d != null && st != null) {
                NavShare(d, session.destinationName, System.currentTimeMillis() + (st.remainingS * 1000).toLong(), r.points.drop(st.segIndex))
            } else null
            GroupLink.publish(loc, share)
        }
        // VOX: ακούει όσο είσαι σε παρέα με «Με τη φωνή» και ανοιχτό μικρόφωνο.
        val micPermitted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        LaunchedEffect(GroupLink.code, GroupLink.voxMode, GroupLink.micOn, micPermitted) { GroupLink.updateVox(micPermitted) }
        var voxTalking by remember { mutableStateOf(false) }
        LaunchedEffect(GroupLink.code, GroupLink.voxMode) {
            while (GroupLink.code != null && GroupLink.voxMode) { voxTalking = GroupLink.voxTalking; kotlinx.coroutines.delay(150) }
            voxTalking = false
        }
        var friendsTick by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) { kotlinx.coroutines.delay(30_000); friendsTick++ }
        }
        LaunchedEffect(style, GroupLink.members, GroupLink.code, friendsTick) {
            val s = style ?: return@LaunchedEffect
            runCatching { FriendsLayer.set(context, s, if (GroupLink.code == null) emptyList() else GroupLink.members, GroupLink.myUid) }
        }
        LaunchedEffect(selectedFriend) {
            if (selectedFriend != null) { kotlinx.coroutines.delay(10_000); selectedFriend = null }
        }
        // Η κάμερα γέρνει (50°) μόνο στην πλοήγηση: τότε το βελάκι τεντώνεται για να μη φαίνεται πατημένο.
        LaunchedEffect(style, cameraTilt, markerPrefs, c) {
            val s = style ?: return@LaunchedEffect
            s.addImage(PUCK_IMAGE, PuckArt.bitmap(context, markerPrefs, c.accent.toArgb(), puckStretch(cameraTilt.toDouble())))
        }
        // Ζουμ ανάλογα με την ταχύτητα (κάθε ~3 s, μόνο όταν η κάμερα σε ακολουθεί).
        var lastAutoZoomAt by remember { mutableStateOf(0L) }
        val speedBucket = ((gps.value.speedKmh ?: 0f) / 10f).toInt()
        val nearTurn = (session.state?.distToNextM ?: 1e9) < 250.0
        LaunchedEffect(speedBucket, nearTurn, session.navigating) {
            val m = map ?: return@LaunchedEffect
            if (!session.navigating || style == null) return@LaunchedEffect
            val lc = m.locationComponent
            if (!lc.isLocationComponentActivated || (lc.cameraMode != CameraMode.TRACKING_GPS && lc.cameraMode != CameraMode.TRACKING)) return@LaunchedEffect
            val now = SystemClock.elapsedRealtime()
            if (now - lastAutoZoomAt < 3_000) return@LaunchedEffect
            val target = navZoomFor(gps.value.speedKmh ?: 0f, session.state?.distToNextM)
            if (kotlin.math.abs(m.cameraPosition.zoom - target) < 0.3) return@LaunchedEffect
            lastAutoZoomAt = now
            runCatching { lc.zoomWhileTracking(target, 2_000L) }
        }
        // «Τέλος» από την ειδοποίηση.
        LaunchedEffect(Unit) {
            NavBus.stopRequests.collect { session.stop() }
        }
        // Η ειδοποίηση δείχνει την επόμενη οδηγία (αλλάζει μόνο όταν αλλάζει το κείμενο).
        LaunchedEffect(session.state?.next, session.state?.distToNextM?.let { (it / 50).toInt() }, session.navigating) {
            val st = session.state ?: return@LaunchedEffect
            if (!session.navigating) return@LaunchedEffect
            val m = st.next ?: return@LaunchedEffect
            NavService.update(
                context,
                "${formatShortDistance(st.distToNextM)} · ${Phrases.capitalized(Phrases.action(m))}",
            )
        }
        // Άδεια ειδοποιήσεων (Android 13+), για να φαίνεται η ειδοποίηση πλοήγησης.
        LaunchedEffect(session.navigating) {
            if (session.navigating && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Στην πλοήγηση η γραμμή «σβήνει» πίσω σου.
        LaunchedEffect(style, session.state) {
            val s = style ?: return@LaunchedEffect
            val r = session.route ?: return@LaunchedEffect
            val st = session.state ?: return@LaunchedEffect
            // Κολλημένο στη γραμμή: το κόψιμο το κάνει η θέση του βελακιού (πιο πάνω). Εδώ μόνο όταν δεν είναι.
            if (session.navigating && (!wasSnapped || snapInfo.alongM == null)) RouteLayers.setRemaining(s, r, st.segIndex, st.snapped)
        }
        // Αν μετακινήσεις τον χάρτη στην πλοήγηση, επιστρέφει στο κέντρο μόνος του μετά από 7 s.
        LaunchedEffect(style, hasLocation) {
            val m = map ?: return@LaunchedEffect
            if (style == null || trackingListenerAdded || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
            trackingListenerAdded = true
            m.locationComponent.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() {
                    trackingDismissedAt = SystemClock.elapsedRealtime()
                }
                override fun onCameraTrackingChanged(currentMode: Int) = Unit
            })
        }
        LaunchedEffect(gps.value.location) {
            val m = map ?: return@LaunchedEffect
            if (!session.navigating || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
            val lc = m.locationComponent
            if (lc.cameraMode == CameraMode.NONE && SystemClock.elapsedRealtime() - trackingDismissedAt > 7_000) {
                centerOnMe(m, navigating = true)
            }
        }
        // Κάμερες γύρω σου (40 km): ξανά όταν απομακρυνθείς 20 km. Χωρίς internet μένουν οι αποθηκευμένες.
        LaunchedEffect(gps.value.location, display.showCameras) {
            val loc = gps.value.location ?: return@LaunchedEffect
            if (!display.showCameras) return@LaunchedEffect
            val here = LatLon(loc.latitude, loc.longitude)
            val last = camFetchedAt
            if (last != null && Geo.distance(last, here) < 20_000) return@LaunchedEffect
            if (SystemClock.elapsedRealtime() - camFailAt < 120_000) return@LaunchedEffect
            camFetchedAt = here
            try {
                val osm = cameraClient.around(here)
                cameraStore.saveOsm(osm)
                cameras = cameraStore.merge(osm, cameraStore.loadMine(), bundledCameras + app.motonav.data.ImportedPois.cameras())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                camFetchedAt = null
                camFailAt = SystemClock.elapsedRealtime()
            }
        }
        // Κάμερα μπροστά σου ≤ 300 m: μπάρα + μία φορά φωνή.
        LaunchedEffect(gps.value.location) {
            val loc = gps.value.location ?: return@LaunchedEffect
            if (!display.showCameras) {
                cameraAlert = null
                return@LaunchedEffect
            }
            val here = LatLon(loc.latitude, loc.longitude)
            val heading = if (loc.hasBearing() && loc.speed > 2f) loc.bearing.toDouble() else null
            val a = CameraAlerts.ahead(
                here, heading, cameras,
                route = if (session.navigating) session.route else null,
                progressM = if (session.navigating) session.state?.progressM else null,
            )
            cameraAlert = a
            if (a != null && a.first.id !in announcedCams) {
                announcedCams += a.first.id
                if (display.cameraVoice) {
                    session.announce(Phrases.L("${a.first.title} σε ", "${a.first.titleEn} in ") + Phrases.distance(a.second) + (a.first.maxspeed?.let { Phrases.L(", όριο $it", ", limit $it") } ?: ""))
                }
            }
            announcedCams.retainAll { id ->
                cameras.firstOrNull { it.id == id }?.let { Geo.distance(here, it.position) < 600 } ?: false
            }
        }
        LaunchedEffect(style, cameras, display.showCameras) {
            val s = style ?: return@LaunchedEffect
            CameraLayer.set(s, if (display.showCameras) cameras else emptyList())
        }
        LaunchedEffect(style, session.destination, session.stops) {
            val s = style ?: return@LaunchedEffect
            RouteLayers.setDestination(s, session.destination, session.stops.map { it.position })
        }
        LaunchedEffect(session.navigating) {
            val m = map ?: return@LaunchedEffect
            if (!m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
            centerOnMe(m, session.navigating)
        }

        // Βενζινάδικα: τα αποθηκευμένα μπαίνουν αμέσως πάνω στη νέα διαδρομή, μετά ρωτάμε το Overpass.
        LaunchedEffect(session.route, display.showFuel, fuelRetry) {
            val r = session.route
            fuelFailed = false
            if (r == null || !display.showFuel) {
                fuelOnRoute = emptyList()
                fuelLoading = false
                return@LaunchedEffect
            }
            // Πρώτα από τη λίστα μέσα στην εφαρμογή (δουλεύει και χωρίς internet), μετά από OpenStreetMap/TomTom.
            val offlineFuel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                app.motonav.search.OfflineGazetteer.fuelNear(r.points, FuelOnRoute.MAX_OFF_M).map { (n, p) ->
                    app.motonav.fuel.FuelStation("off-${p.lat},${p.lon}", n.ifBlank { "Βενζινάδικο" }, p.lat, p.lon)
                }
            }
            fuelRaw = (fuelRaw + offlineFuel.filter { o -> fuelRaw.none { Geo.distance(it.position, o.position) < 80 } }).takeLast(3_000)
            fuelOnRoute = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { FuelOnRoute.along(r, fuelRaw) }
            kotlinx.coroutines.delay(1_500) // αν αλλάξει πάλι η διαδρομή (εναλλακτικές, νέος υπολογισμός), δεν ρωτάμε άδικα
            fuelLoading = true
            try {
                // Το Overpass συχνά είναι φορτωμένο (μαζί ρωτάνε και τα ονόματα δρόμων): έως 3 προσπάθειες.
                for (attempt in 0 until 3) {
                    try {
                        // OpenStreetMap (Overpass) και, με κλειδί, TomTom κατά μήκος της διαδρομής, μαζί.
                        // Φτάνει να απαντήσει ένα από τα δύο.
                        val key = display.tomtomKey
                        val found = kotlinx.coroutines.coroutineScope {
                            val osm = async(kotlinx.coroutines.Dispatchers.IO) {
                                val t0 = SystemClock.elapsedRealtime()
                                runCatching { FuelOnRoute.parse(app.motonav.net.Overpass.query(FuelOnRoute.query(r), 60_000)) }
                                    .also { res -> app.motonav.fuel.FuelDiag.osm(res.getOrNull()?.size, res.exceptionOrNull(), SystemClock.elapsedRealtime() - t0, FuelOnRoute.boxes(r).size) }
                            }
                            val tt = async(kotlinx.coroutines.Dispatchers.IO) {
                                val t0 = SystemClock.elapsedRealtime()
                                (if (key.isBlank()) Result.failure(java.io.IOException("χωρίς κλειδί"))
                                else runCatching { FuelOnRoute.tomtomAlong(r, key) })
                                    .also { res -> app.motonav.fuel.FuelDiag.tomtom(res.getOrNull()?.size, res.exceptionOrNull(), SystemClock.elapsedRealtime() - t0, key.isNotBlank()) }
                            }
                            val a = osm.await()
                            val b = tt.await()
                            a.exceptionOrNull()?.let { android.util.Log.w("MotoNav", "fuel osm: ${it.message}") }
                            b.exceptionOrNull()?.let { android.util.Log.w("MotoNav", "fuel tomtom: ${it.message}") }
                            if (a.isFailure && b.isFailure) throw (a.exceptionOrNull() ?: java.io.IOException("fuel"))
                            // TomTom και OSM για το ίδιο πρατήριο: κρατάμε το ένα (< 80 m).
                            val osmList = a.getOrDefault(emptyList())
                            osmList + b.getOrDefault(emptyList()).filter { t -> osmList.none { Geo.distance(it.position, t.position) < 80 } }
                        }
                        // Τα «offline» που υπάρχουν και στα online φεύγουν (κρατάμε τα online, έχουν ώρες κ.λπ.).
                        fuelRaw = (fuelRaw.filterNot { o -> o.id.startsWith("off-") && found.any { Geo.distance(it.position, o.position) < 80 } } + found)
                            .distinctBy { it.id }.takeLast(3_000)
                        fuelOnRoute = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { FuelOnRoute.along(r, fuelRaw) }
                        fuelFailed = false
                        app.motonav.fuel.FuelDiag.result(fuelOnRoute.size, r.lengthM / 1000.0)
                        break
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("MotoNav", "fuel on route: ${e.message}")
                        fuelFailed = true
                        if (attempt < 2) kotlinx.coroutines.delay(8_000L * (attempt + 1))
                    }
                }
            } finally {
                fuelLoading = false
            }
        }
        LaunchedEffect(session.navigating) { if (!session.navigating) fuelOpen = false }
        // Τα βενζινάδικα στον χάρτη: στην προεπισκόπηση μόνο αν πατήσεις τη γραμμή ⛽ της κάρτας·
        // στην πλοήγηση μόνο το επόμενο (όλα όσα είναι μπροστά αν ανοίξεις τη λίστα).
        LaunchedEffect(session.destination) { fuelShown = false }
        val fuelOnMap = when {
            session.navigating && fuelOpen -> fuelAhead
            session.navigating -> fuelAhead.take(1)
            fuelShown -> fuelOnRoute
            else -> emptyList()
        }
        val fuelNextId = fuelAhead.firstOrNull()?.station?.id
        LaunchedEffect(style, fuelOnMap.map { it.station.id }, fuelNextId, display.showFuel, c.base.isDark) {
            val s = style ?: return@LaunchedEffect
            FuelLayer.set(s, if (display.showFuel) fuelOnMap else emptyList(), if (session.navigating) fuelNextId else null, density, c.base.text.toArgb(), c.base.bg.toArgb())
        }
        // Κίνδυνος μπροστά σου ≤ 500 m: μπάρα + μία φορά φωνή.
        LaunchedEffect(gps.value.location, hazards) {
            val loc = gps.value.location ?: return@LaunchedEffect
            val here = LatLon(loc.latitude, loc.longitude)
            val heading = if (loc.hasBearing() && loc.speed > 2f) loc.bearing.toDouble() else null
            val now = System.currentTimeMillis()
            val a = Hazards.ahead(
                here, heading, hazards.filter { it.alive(now) },
                route = if (session.navigating) session.route else null,
                progressM = if (session.navigating) session.state?.progressM else null,
            )
            hazardAlert = a
            if (a != null && a.first.id !in announcedHazards && now - a.first.createdAtMs > 60_000) {
                announcedHazards += a.first.id
                session.announce(Phrases.L("Προσοχή, ${a.first.kind.label.lowercase(Locale("el", "GR"))} σε ", "Caution, ${a.first.kind.labelEn} in ") + Phrases.distance(a.second) + ".")
            }
            announcedHazards.retainAll { id -> hazards.firstOrNull { it.id == id }?.let { Geo.distance(here, it.position) < 900 } ?: false }
        }
        // Οι ληγμένες αναφορές σβήνουν μόνες τους.
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(300_000)
                hazards = hazardStore.load()
            }
        }
        LaunchedEffect(style, hazards) {
            val s = style ?: return@LaunchedEffect
            HazardLayer.set(s, hazards, density)
        }
        LaunchedEffect(Unit) { app.motonav.trips.SavedRoutes.load(context) }
        // Νέα έκδοση της εφαρμογής; (το πολύ κάθε 6 ώρες)
        LaunchedEffect(Unit) { runCatching { app.motonav.update.Updater.check(context) } }
        // Οι offline χάρτες ενημερώνονται μόνοι τους μία φορά τον μήνα, με Wi‑Fi (όταν είναι έτοιμος ο χάρτης).
        LaunchedEffect(style != null) {
            if (style != null) runCatching { app.motonav.map.OfflineMaps.autoUpdate(context) }
        }
        LaunchedEffect(style, app.motonav.routing.NoGoAreas.list, app.motonav.routing.NoGoAreas.temp) {
            val s = style ?: return@LaunchedEffect
            NoGoLayer.set(s, app.motonav.routing.NoGoAreas.list + app.motonav.routing.NoGoAreas.temp)
        }
        val accentArgb = LocalAppColors.current.accent.toArgb()
        LaunchedEffect(style, favorites, accentArgb) {
            val s = style ?: return@LaunchedEffect
            BookmarkLayer.set(s, favorites, accentArgb)
        }
        LaunchedEffect(hazardToDelete) {
            if (hazardToDelete != null) {
                kotlinx.coroutines.delay(8_000)
                hazardToDelete = null
            }
        }
        // «Όριο 50»: μία φορά όταν το περνάς για 3″. Ξανά μόνο αφού πέσεις κάτω από το όριο ή αλλάξει.
        var overspeedSaidFor by remember { mutableStateOf<Int?>(null) }
        val underLimit = speedLimitNow != null && speedNow < speedLimitNow
        LaunchedEffect(underLimit, speedLimitNow) { if (underLimit) overspeedSaidFor = null }
        LaunchedEffect(overspeed, speedLimitNow) {
            if (!overspeed || !session.navigating || !display.speedVoice) return@LaunchedEffect
            kotlinx.coroutines.delay(3_000)
            if (overspeedSaidFor != speedLimitNow) {
                overspeedSaidFor = speedLimitNow
                session.announce(Phrases.L("Όριο $speedLimitNow.", "Speed limit $speedLimitNow."))
            }
        }
        // Κακός καιρός στα επόμενα 10 km: μία φορά φωνή ανά σημείο.
        val announcedWeather = remember { mutableSetOf<Long>() }
        LaunchedEffect(session.navigating) { if (session.navigating) announcedWeather.clear() }
        LaunchedEffect(session.weather, (progressNow / 1_000).toInt(), session.navigating) {
            if (!session.navigating || !display.speedVoice) return@LaunchedEffect
            val bad = session.weather.firstOrNull { it.isBad && it.atMeters > progressNow && it.atMeters - progressNow <= 10_000 }
                ?: return@LaunchedEffect
            val key = (bad.atMeters / 5_000).toLong()
            if (announcedWeather.add(key)) {
                session.announce(Phrases.L("Προσοχή, ${weatherPhrase(bad)} σε ", "Caution, ${weatherPhraseEn(bad)} in ") + Phrases.distance(bad.atMeters - progressNow) + ".")
            }
        }
        val report: (HazardKind) -> Unit = { kind ->
            val loc = gps.value.location ?: gps.value.lastKnown
            if (loc != null) {
                val heading = if (loc.hasBearing() && loc.speed > 2f) loc.bearing.toDouble() else instruments.headingDeg?.toDouble()
                hazards = hazardStore.add(kind, LatLon(loc.latitude, loc.longitude), heading)
                reportedAt = SystemClock.elapsedRealtime()
            }
            reportOpen = false
        }
        val deleteHazard = {
            hazardToDelete?.let { id ->
                hazards = hazardStore.remove(id)
                if (hazardAlert?.first?.id == id) hazardAlert = null
            }
            hazardToDelete = null
        }
        val addFuelStop: (FuelOnRouteStop) -> Unit = { f ->
            session.addStopNow(f.station.position, f.station.name)
            fuelOpen = false
        }

        val markCamera = {
            val loc = gps.value.location
            if (loc != null) {
                val heading = if (loc.hasBearing() && loc.speed > 2f) loc.bearing.toDouble() else instruments.headingDeg?.toDouble()
                cameras = cameraStore.merge(cameraStore.loadOsm(), cameraStore.addMine(LatLon(loc.latitude, loc.longitude), heading), bundledCameras + app.motonav.data.ImportedPois.cameras())
                cameraMarkedAt = SystemClock.elapsedRealtime()
            }
        }
        val deleteCamera = {
            cameraToDelete?.let { id ->
                cameras = cameraStore.merge(cameraStore.loadOsm(), cameraStore.removeMine(id), bundledCameras + app.motonav.data.ImportedPois.cameras())
                if (cameraAlert?.first?.id == id) cameraAlert = null
            }
            cameraToDelete = null
        }
        LaunchedEffect(cameraToDelete) {
            if (cameraToDelete != null) {
                kotlinx.coroutines.delay(8_000)
                cameraToDelete = null
            }
        }
        // Αποθήκευση του προορισμού: "home", "work" ή "fav".
        val saveDestination: (String) -> Unit = { kind ->
            session.destination?.let { d ->
                val place = Place(
                    session.destinationName ?: "Σημείο στον χάρτη",
                    String.format(Locale.US, "%.4f, %.4f", d.lat, d.lon),
                    d.lat, d.lon, "saved",
                )
                scope.launch {
                    when (kind) {
                        "home" -> repo.setHome(place)
                        "work" -> repo.setWork(place)
                        else -> if (kind.startsWith("fav:")) {
                            val listName = kind.removePrefix("fav:").substringBeforeLast("|")
                            val col = kind.substringAfterLast("|").toIntOrNull() ?: 0
                            repo.saveFavorite(place.copy(list = listName, color = col))
                        } else repo.toggleFavorite(place)
                    }
                }
            }
        }
        val followTrack = {
            shownTrack?.let { session.followTrack(it) }
            onClearTrack()
        }
        val friend = GroupLink.members.firstOrNull { it.uid == selectedFriend }
        val friendPill = friend?.let { m ->
            val parts = listOfNotNull(
                m.name,
                m.speedKmh?.let { "${it.toInt()} km/h" },
                FriendsLayer.subtitle(m, GroupLink.now()),
                m.destName?.let { "πάει: $it" },
            )
            parts.joinToString(" · ") + if (!session.navigating) "\nΠάτα εδώ να πας εκεί" else ""
        }
        val onFriendPill = {
            friend?.let { if (!session.navigating) session.pick(it.position, it.name) }
            selectedFriend = null
        }
        // Χειριστήριο τιμονιού (Ρυθμίσεις → Χειριστήριο τιμονιού).
        LaunchedEffect(showSearch, reportOpen, pip) { app.motonav.nav.KeyBus.mapActive = !showSearch && !reportOpen && !pip }
        DisposableEffect(Unit) { onDispose { app.motonav.nav.KeyBus.mapActive = false } }
        LaunchedEffect(Unit) {
            app.motonav.nav.KeyBus.events.collect { a ->
                when (a) {
                    app.motonav.nav.KeyBus.Action.ZOOM_IN -> map?.animateCamera(CameraUpdateFactory.zoomBy(1.0), 300)
                    app.motonav.nav.KeyBus.Action.ZOOM_OUT -> map?.animateCamera(CameraUpdateFactory.zoomBy(-1.0), 300)
                    app.motonav.nav.KeyBus.Action.CENTER -> recenterRef.value?.invoke()
                    app.motonav.nav.KeyBus.Action.MUTE -> session.toggleMute()
                    app.motonav.nav.KeyBus.Action.REPORT -> reportOpen = true
                    app.motonav.nav.KeyBus.Action.SEARCH -> if (!session.navigating) showSearch = true
                }
            }
        }
        val toggleNorth: () -> Unit = {
            fpsBoostKey++
            CameraPrefs.northUp = !CameraPrefs.northUp
            map?.let { centerOnMe(it, session.navigating) }
        }
        val recenter = {
            fpsBoostKey++
            val m = map
            if (m != null && hasLocation && m.locationComponent.isLocationComponentActivated) {
                val lc = m.locationComponent
                val r = session.route
                val st = session.state
                if (session.navigating && lc.cameraMode != CameraMode.NONE && r != null && st != null) {
                    // Πάτημα στην πυξίδα ενώ σε ακολουθεί: όλη η διαδρομή που μένει (για ~12″), μετά ξανά σε σένα.
                    val rest = r.points.drop(st.segIndex.coerceAtMost(r.points.size - 2))
                    if (rest.size >= 2) {
                        lc.cameraMode = CameraMode.NONE
                        trackingDismissedAt = SystemClock.elapsedRealtime() + 5_000
                        val b = LatLngBounds.Builder()
                        rest.forEach { b.include(LatLng(it.lat, it.lon)) }
                        // Από πάνω (χωρίς κλίση/στροφή της πλοήγησης) και με χώρο για τα πάνελ:
                        // με την κλίση των 50° και το padding της πλοήγησης το «χώρεμα» έβγαινε στραβό.
                        val d = density
                        val pads = if (landscape) intArrayOf((440 * d).toInt(), (40 * d).toInt(), (150 * d).toInt(), (40 * d).toInt())
                        else intArrayOf((40 * d).toInt(), (210 * d).toInt(), (40 * d).toInt(), (190 * d).toInt())
                        runCatching {
                            m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
                            val cam = m.getCameraForLatLngBounds(b.build(), pads, 0.0, 0.0)
                            if (cam != null) m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 900)
                        }
                    } else centerOnMe(m, true)
                } else {
                    centerOnMe(m, session.navigating)
                }
            }
        }
        recenterRef.value = recenter

        // Κάτω από τις οδηγίες: κίνδυνος μπροστά, βενζίνη (και υψόμετρο όταν ανοίξει).
        val navExtras: @Composable () -> Unit = {
            hazardAlert?.let { (h, d) ->
                HazardBar(h, d, confirmDelete = hazardToDelete == h.id) {
                    if (hazardToDelete == h.id) { deleteHazard() } else { hazardToDelete = h.id }
                }
            }
            if (hazardToDelete != null && hazardAlert?.first?.id != hazardToDelete) {
                val k = hazards.firstOrNull { it.id == hazardToDelete }?.kind
                StatusPill("${k?.emoji ?: ""} ${k?.label ?: "Αναφορά"}: πάτα εδώ για να σβηστεί.", onClick = deleteHazard)
            }
            if (reportedAt > 0 && SystemClock.elapsedRealtime() - reportedAt < 4_000) StatusPill("Σημειώθηκε. Θα σε ειδοποιήσω όταν ξαναπεράσεις.")
            if (session.navigating && session.stops.isNotEmpty()) NextStopPill(session, stopsOpen) { stopsOpen = !stopsOpen }
            if (session.navigating && display.showFuel) {
                if (fuelOpen) {
                    FuelList(fuelAhead, progressNow, addFuelStop) { fuelOpen = false }
                    elevation?.let { p ->
                        Surface(shape = RoundedCornerShape(Radius.lg), color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                            ElevationChart(p, progressNow, Modifier.padding(Space.md))
                        }
                    }
                } else {
                    // Μόνο όταν βρέθηκε βενζινάδικο μπροστά σου· όσο ψάχνει ή αν δεν βρει, τίποτα.
                    fuelAhead.firstOrNull()?.let { next ->
                        FuelPill(next, progressNow, loading = false) { fuelOpen = true }
                    }
                }
            }
        }

        if (pip) {
            PipOverlay(session.state?.takeIf { session.navigating }, session.rerouting)
        } else CompositionLocalProvider(LocalSizes provides sizes) {
            val st = session.state?.let { it.copy(remainingS = it.remainingS * session.trafficFactor) }
            // Καθυστέρηση από κίνηση στον χρόνο που μένει (0 χωρίς κλειδί TomTom → πράσινο).
            val tripDelayS = session.state?.let { it.remainingS * (session.trafficFactor - 1.0) }?.coerceAtLeast(0.0) ?: 0.0
            val navControls: @Composable () -> Unit = {
                RideIconButton(Icons.Filled.Close, "Τέλος πλοήγησης", session::stop, kind = ButtonKind.STOP, size = sizes.button)
                RideIconButton(
                    ImageVector.vectorResource(if (session.muted) R.drawable.ic_voice_off else R.drawable.ic_voice),
                    if (session.muted) "Άνοιγμα φωνής" else "Σίγαση φωνής",
                    session::toggleMute,
                    kind = if (session.muted) ButtonKind.SECONDARY else ButtonKind.PRIMARY,
                    size = sizes.button,
                )
            }
            val quickButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { sz ->
                RideIconButton(ImageVector.vectorResource(R.drawable.ic_bolt), "Γρήγορες ενέργειες", { quickOpen = true }, size = sz)
            }
            val appsButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { sz ->
                RideIconButton(ImageVector.vectorResource(R.drawable.ic_apps), "Εφαρμογές", { appsOpen = true }, size = sz)
            }
            val browseButtons: @Composable () -> Unit = {
                RideIconButton(Icons.Filled.Search, "Αναζήτηση", { showSearch = true }, size = sizes.button)
                RideIconButton(Icons.Filled.Settings, "Ρυθμίσεις", onOpenSettings, size = sizes.button)
            }
            val recenterButton: @Composable () -> Unit = {
                RideIconButton(Icons.Filled.LocationOn, "Κέντραρε σε μένα", recenter, kind = ButtonKind.PRIMARY, size = sizes.button)
            }
            // Πυξίδα και υψόμετρο αριστερά από την ταχύτητα, κλισιόμετρο πάνω από την ταχύτητα.
            // Ταχύτητα + πυξίδα + υψόμετρο + κλίση σε ένα όργανο. Πάτημα = κέντραρε, παρατεταμένο = βορράς πάνω.
            val gauges: @Composable () -> Unit = {
                SpeedCompass(
                    speedKmh = gps.value.speedKmh,
                    limitKmh = speedLimitNow,
                    headingDeg = instruments.headingDeg,
                    width = sizes.speedBox * 1.15f,
                    height = sizes.speedBox,
                    speedText = sizes.speedText,
                    showCompass = display.showCompass,
                    altitudeM = instruments.altitudeM,
                    gradePct = gradeNow,
                    showAltitude = false, // το υψόμετρο είναι πάνω αριστερά, δίπλα στην ώρα
                    lean = instruments.leanDeg,
                    maxLeft = instruments.maxLeft,
                    maxRight = instruments.maxRight,
                    showLean = display.showLean && !display.car && instruments.leanAvailable,
                    onTap = recenter,
                    onLongPress = toggleNorth,
                    onResetLean = resetMax,
                    limitAtBottom = landscape,
                )
            }
            // Μόνο οι μπάρες του συστήματος, όχι η εγκοπή της κάμερας: τα πάνελ φτάνουν σχεδόν στην άκρη.
            val edges = Modifier
                .fillMaxSize()
                // Χωρίς κενό για την κάμερα της οθόνης στο πλάι: τα κουμπιά πάνε τέρμα στις άκρες (η κάμερα είναι στη μέση και δεν ενοχλεί).
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.navigationBars))
                .padding(Space.xs)
            if (display.customStatus && !pip) {
                StatusStrip(
                    gps.value.satellites,
                    gps.value.enabled,
                    altitudeM = if (display.showAltitude) instruments.altitudeM else null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility),
                    weather = { st -> WeatherInline(session.weather, session.state?.progressM ?: 0.0, st) { weatherOpen = !weatherOpen } },
                )
            }

            if (landscape) {
                Box(edges) {
                    // Πάνω αριστερά: οδηγίες, χρόνος, προορισμός. Μόνο όσο ύψος χρειάζεται, για να μην «τρώει» αγγίγματα.
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            // Προεπισκόπηση: πιο φαρδιά κάρτα, για να χωράνε άνετα χρόνος, επιλογές και κουμπιά.
                            .width(if (!session.navigating && session.destination != null) 400.dp else sizes.sideWidth)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        TopContent(session, gps.value, hasLocation, requestLocation, cameraAlert, cameraMarkedAt, shownTrack != null, onClearTrack, cameraToDelete != null, deleteCamera, { id -> cameraToDelete = id }, followTrack, turnOnGps, friendPill, onFriendPill, recording || voxTalking, display.roadColors, spotsLoading && !session.navigating)
                        navExtras()
                        if (!session.navigating && session.destination != null) {
                            // Η κάρτα «Ξεκίνα» μένει όπου ήταν: με κενό για την κάμερα της οθόνης.
                            Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                                DestinationCard(session, gps.value.location, onAddStop = { session.addingStop = true; showSearch = true }, onSave = saveDestination, spot = selectedSpot?.takeIf { it.latLon == session.destination }, fuel = if (display.showFuel) fuelOnRoute else null, fuelLoading = fuelLoading, fuelFailed = fuelFailed, elevation = elevation, lists = favLists, fuelShown = fuelShown, onFuelTap = { fuelShown = !fuelShown })
                            }
                        }
                    }
                    // Κάτω αριστερά: η μπάρα άφιξης τέρμα αριστερά, και από πάνω της το ηχείο.
                    if (session.navigating) {
                        Column(
                            Modifier.align(Alignment.BottomStart),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            NextPoisBar(nextPois, progressNow, { p -> session.addStopNow(p.place.latLon, p.place.name) })
                            RideIconButton(
                                ImageVector.vectorResource(if (session.muted) R.drawable.ic_voice_off else R.drawable.ic_voice),
                                if (session.muted) "Άνοιγμα φωνής" else "Σίγαση φωνής",
                                session::toggleMute,
                                kind = if (session.muted) ButtonKind.SECONDARY else ButtonKind.PRIMARY,
                                size = sizes.smallButton,
                            )
                            if (st != null) TripBar(st, Modifier.width(330.dp).height(sizes.button), compact = true, delayS = tripDelayS, doneBeforeM = session.tripDoneM)
                        }
                    }
                    // Πάνω δεξιά: κουμπιά και καιρός.
                    Column(
                        Modifier.align(Alignment.TopEnd),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            if (groupActive) TalkButton(sizes.smallButton, { recording = it })
                            RideIconButton(Icons.Filled.Warning, "Αναφορά κινδύνου ή κάμερας", { reportOpen = true }, size = sizes.smallButton)
                            quickButton(sizes.smallButton)
                            appsButton(sizes.smallButton)
                            if (!session.navigating) browseButtons()
                        }
                        // Pill μουσικής: κάτω από τα κουμπιά, με μικρό κενό.
                        MediaPill(docked = true)
                        if (!display.customStatus) WeatherPill(session.weather, session.state?.progressM ?: 0.0)
                        else if (weatherOpen) WeatherPill(session.weather, session.state?.progressM ?: 0.0, startExpanded = true, onClose = { weatherOpen = false })
                    }
                    // Κάτω δεξιά: όργανα και ταχύτητα.
                    // Κάτω δεξιά: ✕ πάνω από την ταχύτητα.
                    Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        if (session.navigating) RideIconButton(Icons.Filled.Close, "Τέλος πλοήγησης", session::stop, kind = ButtonKind.STOP, size = sizes.smallButton)
                        gauges()
                    }
                }
            } else {
                Box(edges) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        TopContent(session, gps.value, hasLocation, requestLocation, cameraAlert, cameraMarkedAt, shownTrack != null, onClearTrack, cameraToDelete != null, deleteCamera, { id -> cameraToDelete = id }, followTrack, turnOnGps, friendPill, onFriendPill, recording || voxTalking, display.roadColors, spotsLoading && !session.navigating)
                        navExtras()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            // Ο καιρός είναι πάνω πάνω (στο καρτελάκι). Εδώ μόνο η λίστα όταν τον πατήσεις.
                            if (!display.customStatus) WeatherPill(session.weather, session.state?.progressM ?: 0.0)
                            else if (weatherOpen) WeatherPill(session.weather, session.state?.progressM ?: 0.0, startExpanded = true, onClose = { weatherOpen = false })
                        }
                    }

                    // Προεπισκόπηση διαδρομής: μόνο η κάρτα κάτω, χωρίς κουμπιά και όργανα, για να φαίνεται η διαδρομή.
                    val preview = !session.navigating && session.destination != null
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { bottomPanelPx = if (preview) it.height else 0 },
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        if (!session.navigating && session.destination != null) {
                            DestinationCard(session, gps.value.location, onAddStop = { session.addingStop = true; showSearch = true }, onSave = saveDestination, spot = selectedSpot?.takeIf { it.latLon == session.destination }, fuel = if (display.showFuel) fuelOnRoute else null, fuelLoading = fuelLoading, fuelFailed = fuelFailed, elevation = elevation, lists = favLists, fuelShown = fuelShown, onFuelTap = { fuelShown = !fuelShown })
                        }
                        // Αριστερά: (κάμερα, σίγαση) + χρόνος, και από κάτω ✕.
                        // Δεξιά, σε στήλη από κάτω προς τα πάνω: ταχύτητα · κλισιόμετρο · πυξίδα+υψόμετρο · κέντραρε.
                        if (!preview) Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                if (session.navigating) {
                                    // Πυξίδα, από κάτω υψόμετρο, από κάτω η κάμερα.
                                    if (groupActive) TalkButton(sizes.smallButton, { recording = it })
                                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                                        RideIconButton(Icons.Filled.Warning, "Αναφορά κινδύνου ή κάμερας", { reportOpen = true }, size = sizes.smallButton)
                                        quickButton(sizes.smallButton)
                                        appsButton(sizes.smallButton)
                                    }
                                    NextPoisBar(nextPois, progressNow, { p -> session.addStopNow(p.place.latLon, p.place.name) })
                                    // Τέρμα κάτω αριστερά: ηχείο, και δίπλα του άφιξη/χρόνος/απόσταση (το ✕ είναι πάνω από την ταχύτητα).
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                        RideIconButton(
                                            ImageVector.vectorResource(if (session.muted) R.drawable.ic_voice_off else R.drawable.ic_voice),
                                            if (session.muted) "Άνοιγμα φωνής" else "Σίγαση φωνής",
                                            session::toggleMute,
                                            kind = if (session.muted) ButtonKind.SECONDARY else ButtonKind.PRIMARY,
                                            size = sizes.smallButton,
                                        )
                                        if (st != null) TripBar(st, Modifier.weight(1f).height(sizes.smallButton), compact = true, delayS = tripDelayS, narrow = true, doneBeforeM = session.tripDoneM)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                if (!session.navigating) {
                                    // Πυξίδα, από κάτω υψόμετρο, από κάτω η κάμερα.
                                    if (groupActive) TalkButton(sizes.smallButton, { recording = it })
                                    RideIconButton(Icons.Filled.Warning, "Αναφορά κινδύνου ή κάμερας", { reportOpen = true }, size = sizes.smallButton)
                                    quickButton(sizes.smallButton)
                                    appsButton(sizes.smallButton)
                                    RideIconButton(Icons.Filled.Search, "Αναζήτηση", { showSearch = true }, size = sizes.smallButton)
                                    RideIconButton(Icons.Filled.Settings, "Ρυθμίσεις", onOpenSettings, size = sizes.smallButton)
                                }
                                if (session.navigating) RideIconButton(Icons.Filled.Close, "Τέλος πλοήγησης", session::stop, kind = ButtonKind.STOP, size = sizes.smallButton)
                                gauges()
                            }
                        }
                    }
                }
            }
        }

        if (!pip) {
            val edge = when {
                display.edgeWarnings && display.overspeedGlow && overspeed -> EdgeAlert.OVERSPEED
                display.flagOver120 && speedNow > 120f -> EdgeAlert.CHECKERED
                display.edgeWarnings && (hazardAlert?.second ?: 1e9) < 300 -> EdgeAlert.HAZARD
                display.edgeWarnings && (cameraAlert?.second ?: 1e9) < 200 -> EdgeAlert.CAMERA
                else -> null
            }
            EdgeGlow(edge)
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().padding(top = 64.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SimulationPill { app.motonav.nav.Simulator.active = false; session.stop() }
                    browse?.let { (label, places) ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(Radius.lg),
                            color = LocalAppColors.current.base.surface,
                            border = BorderStroke(1.dp, LocalAppColors.current.outline),
                        ) {
                            Row(Modifier.padding(start = Space.md, end = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                Text("$label · ${places.size} κοντά σου · πάτα ένα", style = MotoType.label, color = LocalAppColors.current.base.text)
                                TextChoice("✕", selected = false, onClick = { browse = null })
                            }
                        }
                    }
                    RecPill(session)
                    noGoToDelete?.let { ng ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(Radius.lg),
                            color = LocalAppColors.current.base.surface,
                            border = BorderStroke(1.dp, LocalAppColors.current.outline),
                        ) {
                            Row(Modifier.padding(horizontal = Space.md, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                Text("Σβήσιμο περιοχής αποφυγής;", style = MotoType.label, color = LocalAppColors.current.base.text)
                                TextChoice("Ναι", selected = true, onClick = { app.motonav.routing.NoGoAreas.remove(ng); noGoToDelete = null })
                                TextChoice("Όχι", selected = false, onClick = { noGoToDelete = null })
                            }
                        }
                    }
                    if (!session.navigating) SaveRoutePrompt(session, Modifier.widthIn(max = 420.dp).padding(horizontal = Space.md))
                    if (!session.navigating) UpdatePill()
                }
            }
            // Pill μουσικής (Spotify κ.λπ.) πάνω πάνω στο κέντρο.
            if (!landscape) MediaPill()
            MediaHandle()
        }
        if (reportOpen && !pip) ReportSheet(
            onPick = report,
            onClose = { reportOpen = false },
            onCamera = { markCamera(); reportOpen = false },
            onRoadClosed = if (session.navigating && !session.followingTrack) ({ session.roadClosedAhead(); reportOpen = false }) else null,
            onReroute = if (session.navigating) ({ session.rerouteNow(); reportOpen = false }) else null,
        )
        BackHandler(enabled = reportOpen) { reportOpen = false }
        val themeNow by repo.theme.collectAsState(app.motonav.ui.theme.ThemeSettings())
        if (appsOpen && !pip) AppsPanel(landscape, onClose = { appsOpen = false })
        BackHandler(enabled = appsOpen) { appsOpen = false }
        if (quickOpen && !pip) QuickActionsPanel(
            themeMode = themeNow.mode,
            onTheme = { mode -> scope.launch { repo.save(themeNow.copy(mode = mode)) } },
            onToggleNorth = { toggleNorth() },
            landscape = landscape,
            display = display,
            onDisplayChange = { d -> scope.launch { repo.saveDisplay(d) } },
            recording = session.recording,
            recordingPaused = session.recordingPaused,
            muted = session.muted,
            onRecord = { if (!session.recording) session.startRecording() else session.togglePause() },
            onMute = session::toggleMute,
            onHazard = { quickOpen = false; reportOpen = true },
            onMapReport = { quickOpen = false; mapReportOpen = true },
            onSaveHere = {
                val l = gps.value.location ?: gps.value.lastKnown
                if (l == null) {
                    android.widget.Toast.makeText(context, "Χρειάζεται στίγμα GPS.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val name = "Θέση " + java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale("el", "GR")).format(java.util.Date())
                    scope.launch { repo.saveFavorite(app.motonav.search.Place(name, "", l.latitude, l.longitude, "saved")) }
                    android.widget.Toast.makeText(context, "Αποθηκεύτηκε στα Αγαπημένα: $name", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onClose = { quickOpen = false },
        )
        BackHandler(enabled = quickOpen) { quickOpen = false }
        if (mapReportOpen && !pip) {
            val l = gps.value.location ?: gps.value.lastKnown
            MapReportSheet(l?.latitude, l?.longitude, onClose = { mapReportOpen = false })
        }
        BackHandler(enabled = mapReportOpen) { mapReportOpen = false }

        if (showSearch && !pip) {
            SearchScreen(
                near = (gps.value.location ?: gps.value.lastKnown)?.let { LatLon(it.latitude, it.longitude) },
                speedKmh = gps.value.speedKmh,
                recents = recents,
                tomtomKey = display.tomtomKey,
                autoKeyboard = display.searchKeyboard,
                onMapResults = { categoryPins = it },
                onBrowseMap = { label, places -> browse = label to places; showSearch = false },
                onPick = { place ->
                    showSearch = false
                    scope.launch { repo.addRecent(place) }
                    session.pick(place.latLon, place.name)
                },
                onBack = { showSearch = false },
                home = homePlace,
                work = workPlace,
                favorites = favorites,
                onSetHome = { scope.launch { repo.setHome(it) } },
                onSetWork = { scope.launch { repo.setWork(it) } },
                onToggleFavorite = { scope.launch { repo.toggleFavorite(it) } },
                stopMode = session.addingStop,
                onLoop = { hours ->
                    showSearch = false
                    session.planLoop(hours)
                },
                savedRoutes = app.motonav.trips.SavedRoutes.list,
                onFollowSaved = { r ->
                    showSearch = false
                    val pts = app.motonav.trips.SavedRoutes.points(context, r.id)
                    if (pts.size >= 2) session.followTrack(pts, r.name)
                },
                history = session.history,
                onReplay = { h ->
                    showSearch = false
                    session.replay(h)
                },
            )
        }
        BackHandler(enabled = showSearch) { showSearch = false }
    }
}

// ---------------------------------------------------------------- περιεχόμενο

@Composable
private fun TopContent(
    session: NavSession,
    gps: GpsState,
    hasLocation: Boolean,
    requestLocation: () -> Unit,
    cameraAlert: Pair<SpeedCamera, Double>? = null,
    cameraMarkedAt: Long = 0L,
    trackShown: Boolean = false,
    onClearTrack: () -> Unit = {},
    confirmDelete: Boolean = false,
    onDeleteCamera: () -> Unit = {},
    onAskDelete: (String) -> Unit = {},
    onFollowTrack: () -> Unit = {},
    turnOnGps: () -> Unit = {},
    friendPill: String? = null,
    onFriendPill: () -> Unit = {},
    recording: Boolean = false,
    roadColors: Boolean = true,
    spotsLoading: Boolean = false,
) {
    if (spotsLoading) StatusPill("Φορτώνω θέες, νερά, μαγαζιά μηχανών… (μισό λεπτό την πρώτη φορά)")
    if (recording) StatusPill(if (GroupLink.voxMode) "● Σε ακούνε…" else "● Μιλάς… άφησε το κουμπί για αποστολή")
    GroupLink.speaking?.let { StatusPill("🔊 $it μιλάει") }
    friendPill?.let { StatusPill(it, onClick = onFriendPill) }
    val st = session.state
    if (session.addingStop) StatusPill("Διάλεξε στάση: πάτα παρατεταμένα στον χάρτη ή ψάξε. Πάτα εδώ για άκυρο.", onClick = { session.addingStop = false })
    if (trackShown) {
        StatusPill("Διαδρομή από αρχείο (γαλάζια). Πάτα εδώ για να κλείσει.", onClick = onClearTrack)
        if (!session.navigating) StatusPill("Ακολούθησε αυτή τη διαδρομή", onClick = onFollowTrack)
    }
    if (session.navigating && st != null) {
        ManeuverPanel(st, session.rerouting, if (roadColors) signColorFor(session.route, st) else null)
        if (!session.rerouting) session.route?.lanesAt(st.progressM)?.let { LaneBar(it) }
    }
    if (cameraAlert != null) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.xs), contentAlignment = Alignment.Center) {
            val cam = cameraAlert.first
            CameraBar(
                cameraAlert.second, cam.maxspeed, gps.speedKmh,
                if (cam.source == "mine") "Δική σου κάμερα" else cam.title,
                onClick = if (cam.source == "mine") ({ onAskDelete(cam.id) }) else null,
            )
        }
    }
    if (confirmDelete) StatusPill("Δική σου κάμερα. Πάτα εδώ για να σβηστεί.", onClick = onDeleteCamera)
    if (cameraMarkedAt > 0 && SystemClock.elapsedRealtime() - cameraMarkedAt < 4_000) {
        StatusPill("Η κάμερα σημειώθηκε εδώ.")
    }
    when {
        !hasLocation -> StatusPill("Χρειάζομαι την τοποθεσία σου. Πάτα εδώ.", onClick = requestLocation)
        !gps.enabled -> StatusPill("Το GPS είναι κλειστό. Πάτα εδώ να ανοίξει.", onClick = turnOnGps)
        gps.location == null -> StatusPill("Ψάχνω σήμα GPS…")
        session.arrived -> StatusPill("Έφτασες! Η διαδρομή αποθηκεύτηκε.")
    }
    if (session.navigating && session.greekVoiceMissing) {
        StatusPill("Λείπει η ελληνική φωνή: Ρυθμίσεις → Κείμενο σε ομιλία.")
    }
}

/** Λωρίδες πριν τη στροφή: η σωστή με το χρώμα του θέματος, οι άλλες γκρι. */
@Composable
private fun LaneBar(lanes: List<Lane>) {
    val c = LocalAppColors.current
    Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface, border = BorderStroke(1.dp, c.outline)) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            lanes.take(7).forEach { lane ->
                val on = lane.follow != null
                Box(
                    Modifier
                        .size(34.dp)
                        .background(if (on) c.accent.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(Radius.sm)),
                    contentAlignment = Alignment.Center,
                ) {
                    ManeuverIcon(laneTurn(lane.follow ?: lane.directions.firstOrNull().orEmpty()), if (on) c.accent else c.base.textMuted, Modifier.size(26.dp))
                }
            }
        }
    }
}

private fun laneTurn(direction: String): Turn = when (direction) {
    "SLIGHT_RIGHT" -> Turn.SLIGHT_RIGHT
    "RIGHT" -> Turn.RIGHT
    "SHARP_RIGHT" -> Turn.SHARP_RIGHT
    "SLIGHT_LEFT" -> Turn.SLIGHT_LEFT
    "LEFT" -> Turn.LEFT
    "SHARP_LEFT" -> Turn.SHARP_LEFT
    "RIGHT_U_TURN", "LEFT_U_TURN" -> Turn.UTURN
    else -> Turn.STRAIGHT
}

/** Στο μικρό παράθυρο: μόνο η επόμενη στροφή και η απόσταση. */
@Composable
private fun PipOverlay(s: NavState?, rerouting: Boolean) {
    val c = LocalAppColors.current
    val m = s?.next ?: return
    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopStart) {
        Surface(shape = RoundedCornerShape(Radius.sm), color = c.base.surface, border = BorderStroke(1.dp, c.outline)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (rerouting) {
                    Text("Νέα διαδρομή…", style = MotoType.label, color = c.base.text)
                } else {
                    ManeuverIcon(m.turn, c.accent, Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(formatShortDistance(s.distToNextM), style = MotoType.title.copy(fontSize = 16.sp, lineHeight = 18.sp), color = c.base.text)
                }
            }
        }
    }
}

/** Χρώματα ταμπέλας (από τα δείγματα που έστειλες). */
object SignColors {
    val MOTORWAY = Color(0xFF24A046)   // πράσινο: αυτοκινητόδρομος (όπως οι πινακίδες στην Ελλάδα)
    val MAIN = Color(0xFF377DF6)       // γαλάζιο: εθνική / κύρια οδός
    /** Η λωρίδα «Έπειτα…» κάτω από τη γαλάζια ταμπέλα. */
    val MAIN_STRIP = Color(0xFF316BCE)
    val DIRT = Color(0xFFF58142)       // πορτοκαλί: χωματόδρομος
    val ARRIVE = Color(0xFF66023C)     // μπορντό: τελευταία στροφή πριν τον προορισμό / άφιξη
}

/**
 * Το χρώμα της ταμπέλας: ο δρόμος όπου βγαίνεις στην επόμενη στροφή (30 m μετά από αυτήν).
 * Μπορντό όταν η επόμενη κίνηση είναι η άφιξη ή η τελευταία πριν από αυτήν. null = κανονική ταμπέλα.
 */
fun signColorFor(route: Route?, s: NavState): Color? {
    val m = s.next ?: return null
    if (route == null) return null
    if (m.turn == Turn.ARRIVE || s.then?.turn == Turn.ARRIVE) return SignColors.ARRIVE
    return when (route.roadKindAt(m.atMeters + 30.0)) {
        app.motonav.routing.RoadKind.MOTORWAY -> SignColors.MOTORWAY
        app.motonav.routing.RoadKind.MAIN -> SignColors.MAIN
        app.motonav.routing.RoadKind.DIRT -> SignColors.DIRT
        app.motonav.routing.RoadKind.OTHER -> null
    }
}

/** Η λωρίδα «Έπειτα…» κάτω από την ταμπέλα: το ίδιο χρώμα, λίγο πιο σκούρο (στη μαύρη ταμπέλα λίγο πιο ανοιχτό). */
private fun stripColorFor(sign: Color, dark: Boolean): Color =
    if (!dark && sign == SignColors.MAIN) SignColors.MAIN_STRIP
    else if (dark) Color(minOf(1f, sign.red + 0.06f), minOf(1f, sign.green + 0.06f), minOf(1f, sign.blue + 0.07f), sign.alpha)
    else Color(sign.red * 0.86f, sign.green * 0.86f, sign.blue * 0.86f, sign.alpha)

@Composable
private fun ManeuverPanel(s: NavState, rerouting: Boolean, signColor: Color? = null) {
    val base = LocalAppColors.current
    // Χρωματιστή ταμπέλα: λευκά γράμματα και βελάκι πάνω στο χρώμα του δρόμου.
    // Κανονικά μαύρη (το χρώμα του θέματος). Σε μεγαλύτερο δρόμο παίρνει το χρώμα του (αυτοκινητόδρομος, εθνική, χώμα, άφιξη).
    val c = if (signColor == null || rerouting) base else base.copy(
        base = base.base.copy(surface = signColor, text = Color.White, textMuted = Color.White.copy(alpha = 0.85f)),
        accent = Color.White,
        outline = Color.White.copy(alpha = 0.55f),
    )
    val sizes = LocalSizes.current
    // Μεγαλώνει όσο πλησιάζεις τη στροφή: 100% πάνω από 300 m, έως 135% πάνω της.
    val target = if (rerouting || s.next == null) 1f else (1f + 0.35f * (1f - (s.distToNextM / 300.0).toFloat().coerceIn(0f, 1f)))
    val grow by animateFloatAsState(target, animationSpec = tween(600), label = "grow")
    val bigText = sizes.maneuverText.copy(
        fontSize = sizes.maneuverText.fontSize * grow,
        lineHeight = sizes.maneuverText.lineHeight * grow,
    )
    // Όσο φαρδιά χρειάζεται το κείμενο (όχι όλη την οθόνη), αριστερά. Μεγαλώνει μόνο αν χρειαστεί.
    // Χωρίς περίγραμμα, στρογγυλές γωνίες, και από κάτω πιο σκούρα λωρίδα «Έπειτα μετά από …».
    val colored = signColor != null && !rerouting
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = c.base.surface,
        modifier = Modifier.wrapContentWidth(Alignment.Start),
    ) {
      Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
        Row(
            modifier = Modifier
                .padding(start = sizes.panelPadding, top = sizes.panelPadding, bottom = sizes.panelPadding, end = sizes.panelPadding + Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val m = s.next
            if (rerouting || m == null) {
                Text("Νέα διαδρομή…", style = MotoType.title, color = c.base.text)
            } else {
                // Το βελάκι πιάνει όλο το ύψος της καρτέλας: μετράμε πόσο ψηλά είναι τα κείμενα δίπλα του.
                val density = androidx.compose.ui.platform.LocalDensity.current
                var textH by remember { mutableStateOf(0) }
                // Με όριο: αλλιώς στο οριζόντιο (στενή στήλη 300 dp) το μεγάλο βελάκι στένευε το κείμενο, το κείμενο
                // έσπαγε σε περισσότερες γραμμές και ψήλωνε, το βελάκι μεγάλωνε ξανά… ώσπου το κείμενο εξαφανιζόταν.
                val iconSize = maxOf(sizes.maneuverIcon * grow, with(density) { textH.toDp() })
                    .coerceAtMost(sizes.maneuverIcon * grow * 1.5f)
                ManeuverIcon(m.turn, c.accent, Modifier.size(iconSize))
                Spacer(Modifier.width(Space.md))
                Column(Modifier.onSizeChanged { textH = it.height }) {
                    Text(formatShortDistance(s.distToNextM), style = bigText.copy(fontWeight = FontWeight.Bold), color = c.base.text, maxLines = 1, softWrap = false)
                    Text(
                        Phrases.capitalized(Phrases.action(m)),
                        style = MotoType.body.copy(fontSize = MotoType.body.fontSize * grow, lineHeight = MotoType.body.lineHeight * grow),
                        color = c.base.text,
                    )
                    // Όνομα δρόμου ή έξοδος, όταν το ξέρουμε.
                    Phrases.road(m)?.let {
                        Text(it, style = MotoType.label, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
                    }
                }
            }
        }
        // «Έπειτα μετά από 2,8 km ↑»: η κίνηση μετά την επόμενη.
        val nx = s.next
        val af = s.after
        if (!rerouting && nx != null && af != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(stripColorFor(c.base.surface, dark = !colored))
                    .padding(horizontal = sizes.panelPadding + Space.xs, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Έπειτα μετά από ${formatShortDistance(af.atMeters - nx.atMeters)}",
                    style = MotoType.title.copy(fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                    color = c.base.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.sm))
                ManeuverIcon(af.turn, c.accent, Modifier.size(24.dp))
            }
        }
      }
    }
}

/** Χρώμα του χρόνου: πράσινο χωρίς κίνηση, πορτοκαλί με λίγη, κόκκινο με πολλή. */
object TimeColors {
    val FREE = Color(0xFF5FB12E)
    val SLOW = Color(0xFFF5A623)
    val JAM = Color(0xFFE5483B)
    val TRACK = Color(0xFF353541)
    val DONE = Color(0xFFBDBDBD)

    /** delayS: καθυστέρηση από κίνηση στον χρόνο που μένει. */
    fun of(delayS: Double, remainingS: Double): Color = when {
        delayS < 120 -> FREE
        delayS < 600 && delayS < remainingS * 0.25 -> SLOW
        else -> JAM
    }
}

@Composable
private fun TripBar(s: NavState, modifier: Modifier = Modifier, compact: Boolean = false, delayS: Double = 0.0, narrow: Boolean = false, doneBeforeM: Double = 0.0) {
    val c = LocalAppColors.current
    Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = modifier) {
        // Νούμερα και μπάρα μαζί, κεντραρισμένα στο ύψος και στο πλάτος του πινακακιού.
        Column(
            Modifier.fillMaxWidth().then(if (compact) Modifier.fillMaxHeight() else Modifier.padding(vertical = Space.sm)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val eta = LocalTime.now().plusSeconds(s.remainingS.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
                val time = if (compact) formatDurationShort(s.remainingS) else formatDuration(s.remainingS)
                // Χωρίς λέξεις: μεγαλύτερα νούμερα. Η άφιξη έχει μικρή σημαία μπροστά.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ImageVector.vectorResource(R.drawable.ic_flag), contentDescription = "Άφιξη", tint = c.base.textMuted, modifier = Modifier.size(if (narrow) 14.dp else 16.dp))
                    Spacer(Modifier.width(3.dp))
                    Stat(eta, "", compact, narrow = narrow)
                }
                Stat(time, "", compact, color = TimeColors.of(delayS, s.remainingS), big = true, narrow = narrow)
                Stat(formatKm(s.remainingM), "", compact, narrow = narrow)
            }
            // Πρόοδος της διαδρομής: γκρι ό,τι πέρασες, σκούρο ό,τι μένει, άσπρη κουκίδα με ▶ εκεί που είσαι.
            // Όλο το ταξίδι, και μετά από «νέα διαδρομή» (όχι μόνο η τρέχουσα διαδρομή).
            val done = doneBeforeM + s.progressM
            val total = done + s.remainingM
            Spacer(Modifier.height(4.dp))
            RouteProgress(if (total > 0) (done / total).toFloat() else 0f, Modifier.fillMaxWidth().height(12.dp).padding(horizontal = Space.md))
        }
    }
}

@Composable
private fun RouteProgress(fraction: Float, modifier: Modifier) {
    Canvas(modifier) {
        val h = 5.dp.toPx()
        val r = size.height / 2
        val y = size.height / 2
        val x = r + (size.width - 2 * r) * fraction.coerceIn(0f, 1f)
        drawRoundRect(TimeColors.TRACK, Offset(0f, y - h / 2), androidx.compose.ui.geometry.Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawRoundRect(TimeColors.DONE, Offset(0f, y - h / 2), androidx.compose.ui.geometry.Size(x, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawCircle(Color.White, r, Offset(x, y))
        // ▶ μέσα στην κουκίδα
        val a = r * 0.5f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(x - a * 0.6f, y - a)
            lineTo(x + a, y)
            lineTo(x - a * 0.6f, y + a)
            close()
        }
        drawPath(path, Color(0xFF1B1B27))
    }
}

@Composable
private fun Stat(value: String, label: String, compact: Boolean = false, color: Color? = null, big: Boolean = false, narrow: Boolean = false) {
    val c = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val size = when {
            // Όρθια οθόνη: στενή μπάρα, μικρότερα νούμερα.
            narrow && big -> 25.sp
            narrow -> 19.sp
            compact && big -> 26.sp
            compact -> 21.sp
            big -> MotoType.title.fontSize * 1.2f
            else -> MotoType.title.fontSize
        }
        Text(
            value,
            // Χωρίς τον «αέρα» της γραμματοσειράς πάνω/κάτω, για σωστό κεντράρισμα.
            style = MotoType.title.copy(
                fontSize = size,
                lineHeight = size,
                fontWeight = if (big) FontWeight.Bold else MotoType.title.fontWeight,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
            color = color ?: c.base.text,
            maxLines = 1,
        )
        if (label.isNotEmpty()) {
            Text(
                label,
                style = if (compact) MotoType.label.copy(fontSize = 10.sp, lineHeight = 11.sp) else MotoType.label,
                color = c.base.textMuted,
            )
        }
    }
}

@Composable
private fun DestinationCard(
    session: NavSession,
    here: Location?,
    onAddStop: () -> Unit = {},
    onSave: (String) -> Unit = {},
    spot: app.motonav.spots.Spot? = null,
    fuel: List<FuelOnRouteStop>? = null,
    fuelLoading: Boolean = false,
    fuelFailed: Boolean = false,
    elevation: ElevationProfile? = null,
    /** Λίστες σελιδοδεικτών (όνομα, χρώμα), για το ★. */
    lists: List<Pair<String, Int>> = emptyList(),
    fuelShown: Boolean = false,
    onFuelTap: () -> Unit = {},
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val dest = session.destination ?: return
    var saveOpen by remember(dest) { mutableStateOf(false) }
    var savedAs by remember(dest) { mutableStateOf<String?>(null) }
    // Κάρτα που μαζεύει: μένουν χρόνος/χιλιόμετρα και «Ξεκίνα», για να φαίνεται ο χάρτης.
    var collapsed by remember { mutableStateOf(false) }
    var pickList by remember(dest) { mutableStateOf(false) }
    var newList by remember(dest) { mutableStateOf<String?>(null) }
    var reportOpen by remember(dest) { mutableStateOf(false) }
    var nogoOpen by remember(dest) { mutableStateOf(false) }
    var reportText by remember(dest) { mutableStateOf("") }
    var reportState by remember(dest) { mutableStateOf<String?>(null) }
    val cardScope = rememberCoroutineScope()
    // Ώρες / τηλέφωνο / site από το OpenStreetMap, για μέρη με όνομα.
    var info by remember(dest, session.destinationName) { mutableStateOf<app.motonav.search.PlaceInfo?>(null) }
    LaunchedEffect(dest, session.destinationName) {
        info = app.motonav.search.PlaceInfoLookup.lookup(dest, session.destinationName)
    }
    Surface(shape = RoundedCornerShape(Radius.lg), color = c.base.surface, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            // Λαβή: πάτημα = μάζεμα / άνοιγμα της κάρτας.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { collapsed = !collapsed }
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.base.textMuted.copy(alpha = 0.5f)))
            }
            if (spot != null && !session.navigating && !collapsed) SpotPhotoBox(spot)
            val crow = here?.let { Geo.distance(LatLon(it.latitude, it.longitude), dest) }
            val route = session.route
            val err = session.error
            val clipboard = LocalClipboardManager.current
            var copied by remember(dest) { mutableStateOf(false) }
            val coords = String.format(Locale.US, "%.6f, %.6f", dest.lat, dest.lon)

            // Πάνω αριστερά μεγάλα: χρόνος · χιλιόμετρα. Δεξιά ★ και ＋ στάση.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.weight(1f)) {
                    when {
                        session.loopPlanning -> Text("Ψάχνω κυκλική βόλτα… (έως 1–2 λεπτά)", style = MotoType.body, color = c.base.textMuted)
                        session.planning -> Text(
                            when (session.routeType) {
                                RouteType.TWISTY -> "Ψάχνω δρόμο με στροφές…"
                                RouteType.DIRT -> "Ψάχνω χωματόδρομους…"
                                else -> "Υπολογίζω διαδρομή…"
                            },
                            style = MotoType.body,
                            color = c.base.textMuted,
                        )
                        route != null -> {
                            // Ο χρόνος στο χρώμα της κίνησης (πράσινο / πορτοκαλί / κόκκινο), τα χιλιόμετρα λευκά.
                            val delayNow = if (session.trafficKnown) session.trafficDelayS else route.trafficDelayS
                            val time = formatDuration(route.durationS * session.trafficFactor)
                            val km = formatKm(route.lengthM)
                            val big = MotoType.title.copy(fontSize = if (time.length + km.length > 13) 21.sp else 26.sp, lineHeight = 30.sp)
                            Text(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    withStyle(androidx.compose.ui.text.SpanStyle(color = TimeColors.of(delayNow, route.durationS))) { append(time) }
                                    append(" · ")
                                    append(km)
                                },
                                style = big,
                                color = c.base.text,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        err != null -> ErrorText(err)
                        crow != null -> Text("${formatKm(crow)} σε ευθεία", style = MotoType.body, color = c.base.textMuted)
                    }
                }
                // Γεμάτο αστέρι = ήδη αποθηκευμένο· περίγραμμα = όχι ακόμα.
                RideIconButton(
                    if (savedAs != null) Icons.Filled.Star else Icons.Outlined.OutlinedStar,
                    if (savedAs != null) "Αποθηκευμένο" else "Αποθήκευση",
                    { saveOpen = !saveOpen },
                    kind = if (saveOpen) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                    size = 56.dp,
                )
                if (!session.followingTrack) RideIconButton(Icons.Filled.Add, "Στάση", onAddStop, size = 56.dp)
            }
            if (!collapsed) {
            // Από κάτω: ο προορισμός με λευκά, και κάτω του οι συντεταγμένες (πάτημα = αντιγραφή), κίνηση, στροφές.
            session.destinationName?.let { name ->
                Text(name, style = MotoType.body, color = c.base.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    if (copied) "$coords ✓" else coords,
                    style = MotoType.label,
                    color = if (copied) c.accent else c.base.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable {
                            clipboard.setText(AnnotatedString(coords))
                            copied = true
                        },
                )
                if (session.alternativesNone) {
                    Text("⇄ καμία λογική", style = MotoType.label, color = c.base.textMuted)
                }
                if (session.canAskAlternatives || session.alternativesLoading) {
                    Text(
                        if (session.alternativesLoading) "⇄ …" else "⇄ Εναλλακτικές",
                        style = MotoType.label,
                        color = c.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(enabled = !session.alternativesLoading) { session.requestAlternatives() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // Έτοιμη διαδρομή (αποθηκευμένη/GPX): ανάποδα, από το τέλος προς την αρχή.
                if (session.followingTrack && !session.navigating) {
                    Text(
                        "⇅ Ανάποδα",
                        style = MotoType.label,
                        color = c.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { session.reverseTrack() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "⊘",
                    style = MotoType.label,
                    color = if (nogoOpen) c.accent else c.base.textMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { nogoOpen = !nogoOpen }
                        .padding(horizontal = 6.dp),
                )
                Text(
                    "⚑",
                    style = MotoType.label,
                    color = if (reportOpen) c.accent else c.base.textMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { reportOpen = !reportOpen; reportState = null }
                        .padding(horizontal = 6.dp),
                )
                if (route != null) {
                    val delay = if (session.trafficKnown) session.trafficDelayS else route.trafficDelayS
                    if (delay >= 60) {
                        Text("+${(delay / 60).roundToInt()}′ κίνηση", style = MotoType.label, color = TimeColors.of(delay, route.durationS))
                    } else if (session.trafficKey.isNotBlank() && !session.trafficKnown && route.source != "TomTom") {
                        // Ο χρόνος είναι ακόμα χωρίς κίνηση (έρχεται σε λίγα δευτερόλεπτα από το TomTom).
                        Text("χωρίς κίνηση", style = MotoType.label, color = c.base.textMuted)
                    }
                    Text("●".repeat(route.twistinessStars) + "○".repeat(5 - route.twistinessStars), style = MotoType.label, color = c.accent)
                }
            }
            info?.let { inf ->
                inf.hours?.let { Text("🕑 $it", style = MotoType.label, color = c.base.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    inf.phone?.let { ph ->
                        TextChoice("📞 $ph", selected = false, onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + ph.replace(" ", "")))) }
                        })
                    }
                    inf.website?.let { w ->
                        TextChoice("🌐 Site", selected = false, onClick = {
                            val url = if (w.startsWith("http")) w else "https://$w"
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                        })
                    }
                }
            }
            if (nogoOpen) {
                Text(
                    "Περιοχή αποφυγής: οι διαδρομές δεν θα περνάνε από αυτό το σημείο (π.χ. κέντρο, έργα). Σβήνεται με πάτημα πάνω της.",
                    style = MotoType.label,
                    color = c.base.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                    listOf(300 to "300 m", 1000 to "1 km", 3000 to "3 km").forEach { (r, label) ->
                        TextChoice(label, selected = false, onClick = {
                            app.motonav.routing.NoGoAreas.add(dest, r)
                            session.clear()
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (reportOpen) {
                Text(
                    "Λάθος ή κάτι λείπει στον χάρτη εδώ; Γράψ' το και πάει ως σημείωση στο OpenStreetMap (ανώνυμα). Το διορθώνουν εθελοντές.",
                    style = MotoType.label,
                    color = c.base.textMuted,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = reportText,
                    onValueChange = { reportText = it.take(500) },
                    placeholder = { Text("π.χ. Ο δρόμος είναι κλειστός / λείπει βενζινάδικο") },
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    TextChoice("Αποστολή", selected = true, onClick = {
                        if (reportText.isBlank() || reportState == "…") return@TextChoice
                        reportState = "…"
                        cardScope.launch {
                            val ok = app.motonav.search.OsmNotes.create(dest, reportText.trim())
                            reportState = if (ok) "Στάλθηκε. Ευχαριστούμε!" else "Δεν στάλθηκε (χωρίς internet;)"
                            if (ok) reportText = ""
                        }
                    })
                    reportState?.let { Text(it, style = MotoType.label, color = c.base.textMuted) }
                }
            }
            // Προφίλ υψομέτρου και βενζίνη πάνω στη διαδρομή.
            if (route != null && !session.planning) {
                elevation?.let { ElevationChart(it, null, Modifier.fillMaxWidth(), height = 40.dp) }
                if (!fuel.isNullOrEmpty()) {
                    val gap = FuelOnRoute.longestGap(fuel, route.lengthM)
                    val text = when {
                        fuel.isEmpty() && fuelLoading -> "⛽ ψάχνω βενζινάδικα στη διαδρομή…"
                        fuel.isEmpty() && fuelFailed -> "⛽ δεν φόρτωσαν τα βενζινάδικα (server OpenStreetMap)"
                        fuel.isEmpty() -> "⛽ κανένα βενζινάδικο στη διαδρομή"
                        else -> "⛽ ${fuel.size} στη διαδρομή · πρώτο σε ${fuelKm(fuel.first().alongM)} · μεγαλύτερο κενό ${fuelKm(gap)}"
                    }
                    // Πάτημα: εμφάνιση/απόκρυψη των βενζινάδικων στον χάρτη.
                    Text(
                        text,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onFuelTap).padding(vertical = 2.dp),
                        style = MotoType.label.copy(fontWeight = if (fuelShown) FontWeight.Bold else MotoType.label.fontWeight),
                        color = if (gap >= 150_000 && !(fuel.isEmpty() && (fuelLoading || fuelFailed))) c.base.warning else c.base.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (saveOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                    listOf("home" to "Σπίτι", "work" to "Δουλειά").forEach { (kind, label) ->
                        TextChoice(label, selected = savedAs == kind, onClick = { onSave(kind); savedAs = kind; saveOpen = false }, modifier = Modifier.weight(1f))
                    }
                    TextChoice("Σε λίστα…", selected = pickList || savedAs?.startsWith("fav") == true, onClick = { pickList = !pickList }, modifier = Modifier.weight(1.2f))
                }
                if (pickList) {
                    // Λίστες σελιδοδεικτών: πάτημα = αποθήκευση εκεί· «＋ Νέα» = καινούργια λίστα.
                    val all = (lists.ifEmpty { listOf(app.motonav.search.Place.DEFAULT_LIST to 0) })
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        all.forEach { (name, col) ->
                            TextChoice(if (col != 0) "● $name" else name, selected = false, onClick = {
                                onSave("fav:$name|$col"); savedAs = "fav"; pickList = false; saveOpen = false
                            })
                        }
                        TextChoice("＋ Νέα", selected = newList != null, onClick = { newList = if (newList == null) "" else null })
                    }
                    newList?.let { nl ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedTextField(
                                value = nl,
                                onValueChange = { newList = it.take(30) },
                                singleLine = true,
                                placeholder = { Text("Όνομα λίστας") },
                                shape = RoundedCornerShape(Radius.md),
                                modifier = Modifier.weight(1f),
                            )
                            TextChoice("OK", selected = true, onClick = {
                                val name = nl.trim()
                                if (name.isNotEmpty()) {
                                    val col = BOOKMARK_COLORS[lists.size % BOOKMARK_COLORS.size]
                                    onSave("fav:$name|$col"); savedAs = "fav"; pickList = false; saveOpen = false; newList = null
                                }
                            })
                        }
                    }
                }
            }
            // Στάσεις με τη σειρά: ↑ πιο πριν, ✕ βγάλε.
            session.stops.forEachIndexed { i, stop ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        "${i + 1}. ${stop.name ?: "Στάση στον χάρτη"}",
                        style = MotoType.label,
                        color = c.base.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (i > 0) TextChoice("↑", selected = false, onClick = { session.moveStopUp(i) })
                    TextChoice("✕", selected = false, onClick = { session.removeStop(i) })
                }
            }

            // Τύπος διαδρομής: μικρά κουμπιά με εικονίδιο, και δεξιά οι αποφυγές.
            if (session.followingTrack) {
                Text("Ακολουθείς έτοιμη διαδρομή. Αν είσαι μακριά, σε πάει πρώτα στο πιο κοντινό σημείο της· αν βγεις, σε ξαναφέρνει πάνω της.", style = MotoType.label, color = c.base.textMuted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RouteTypeButton(R.drawable.ic_route_fast, "Γρήγορη", session.routeType == RouteType.FASTEST) { session.chooseRouteType(RouteType.FASTEST) }
                    RouteTypeButton(R.drawable.ic_route_twisty, "Στροφές", session.routeType == RouteType.TWISTY) { session.chooseRouteType(RouteType.TWISTY) }
                    if (!app.motonav.routing.Vehicle.car) RouteTypeButton(R.drawable.ic_route_dirt, "Χώμα", session.routeType == RouteType.DIRT) { session.chooseRouteType(RouteType.DIRT) }
                    if (session.trafficKey.isNotBlank()) {
                        RouteTypeButton(R.drawable.ic_route_traffic, "Κίνηση", session.routeType == RouteType.TRAFFIC) { session.chooseRouteType(RouteType.TRAFFIC) }
                    }
                    Spacer(Modifier.weight(1f))
                    // Αποφυγή: πατημένο = χωρίς. Οι Στροφές/Χώμα αποφεύγουν ήδη αυτοκινητόδρομους.
                    if (session.routeType != RouteType.TWISTY && session.routeType != RouteType.DIRT) {
                        AvoidButton(R.drawable.ic_motorway, "Χωρίς αυτοκινητόδρομους", session.avoidMotorways) { session.toggleAvoidMotorways() }
                    }
                    AvoidButton(R.drawable.ic_toll, "Χωρίς διόδια", session.avoidTolls) { session.toggleAvoidTolls() }
                }
            }

            }
            // Μεγάλο «Ξεκίνα» (ή η ενέργεια που χρειάζεται για να βγει διαδρομή) και ✕ δεξιά.
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                val primary: Pair<String, () -> Unit>? = when {
                    route != null -> "Ξεκίνα" to session::start
                    err?.kind == RoutingException.Kind.NOT_INSTALLED -> "Εγκατάσταση BRouter" to { openBRouterInStore(context) }
                    err?.kind == RoutingException.Kind.NO_DATA || err?.kind == RoutingException.Kind.NO_PROFILE ->
                        "Άνοιγμα BRouter" to { openBRouter(context) }
                    err != null && !session.planning -> "Ξανά" to session::plan
                    else -> null
                }
                if (primary != null) {
                    // Παρατεταμένο πάτημα στο «Ξεκίνα»: προσομοίωση (βλέπεις τη διαδρομή να «τρέχει» πριν φύγεις).
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = c.accent,
                        contentColor = c.onAccent,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .combinedClickable(
                                onClick = primary.second,
                                onLongClick = if (route != null) ({
                                    app.motonav.nav.Simulator.speedUp = 1
                                    app.motonav.nav.Simulator.active = true
                                    session.start()
                                }) else null,
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(primary.first, style = MotoType.title.copy(fontSize = 18.sp), color = c.onAccent)
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                RideIconButton(Icons.Filled.Close, "Άκυρο", session::clear, size = 48.dp)
            }
        }
    }
}

/** Τύπος διαδρομής: μικρό κουμπί με εικονίδιο· το επιλεγμένο στο χρώμα του θέματος. */
@Composable
private fun RouteTypeButton(icon: Int, description: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) c.accent else c.base.surface2,
        border = BorderStroke(1.dp, if (selected) c.accent else c.outline),
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description + if (selected) ": επιλεγμένη" else "" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(ImageVector.vectorResource(icon), contentDescription = null, tint = if (selected) c.onAccent else c.base.text, modifier = Modifier.size(24.dp))
        }
    }
}

/** Κουμπί αποφυγής: σκέτο εικονίδιο. Ενεργό = στο χρώμα του θέματος με διαγώνια γραμμή («χωρίς»). */
/** Walkie-talkie: κράτα πατημένο, μίλα, άφησε. Την 1η φορά ζητάει άδεια για το μικρόφωνο. */
@Composable
private fun TalkButton(size: androidx.compose.ui.unit.Dp, onRecording: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var active by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val permitted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    if (GroupLink.voxMode) {
        // Με τη φωνή: πάτημα = κλείσε/άνοιξε το μικρόφωνο. Κόκκινο όταν σε ακούνε τώρα.
        val on = GroupLink.micOn && permitted
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = if (on) c.accent else c.base.surface2,
            contentColor = if (on) c.onAccent else c.base.textMuted,
            border = BorderStroke(if (on) 2.dp else 1.dp, if (on) c.accent else c.outline),
            onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                if (!permitted) {
                    GroupLink.micOn = true
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    GroupLink.micOn = !GroupLink.micOn
                }
            },
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = if (on) "Κλείσε το μικρόφωνο της παρέας" else "Άνοιξε το μικρόφωνο της παρέας" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(ImageVector.vectorResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(28.dp))
                if (!on) {
                    Canvas(Modifier.size(30.dp)) {
                        drawLine(c.base.textMuted, Offset(3f, 3f), Offset(this.size.width - 3f, this.size.height - 3f), strokeWidth = 4f, cap = StrokeCap.Round)
                    }
                }
            }
        }
        return
    }
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (active) Tokens.stop else c.accent,
        contentColor = c.onAccent,
        border = BorderStroke(2.dp, if (active) Tokens.stopBorder else c.accent),
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = "Κράτα πατημένο για να μιλήσεις στην παρέα" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!ok) {
                        askMic.launch(Manifest.permission.RECORD_AUDIO)
                        return@detectTapGestures
                    }
                    if (recorder.start()) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        active = true
                        onRecording(true)
                        tryAwaitRelease()
                        active = false
                        onRecording(false)
                        recorder.stop()?.let { (bytes, ms) -> GroupLink.sendVoice(bytes, ms) }
                    }
                })
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(ImageVector.vectorResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun AvoidButton(icon: Int, description: String, avoided: Boolean, onToggle: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (avoided) c.accent else c.base.surface2,
        contentColor = if (avoided) c.onAccent else c.base.text,
        border = BorderStroke(1.dp, if (avoided) c.accent else c.outline),
        onClick = onToggle,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description + if (avoided) ": ναι" else ": όχι" },
    ) {
        val fg = if (avoided) c.onAccent else c.base.text
        Box(contentAlignment = Alignment.Center) {
            Icon(ImageVector.vectorResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
            if (avoided) {
                Canvas(Modifier.size(30.dp)) {
                    drawLine(c.accent, Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), strokeWidth = 7f, cap = StrokeCap.Round)
                    drawLine(fg, Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), strokeWidth = 3.5f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MotoType.label, color = c.base.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.base.textMuted,
                uncheckedTrackColor = c.base.surface3,
                uncheckedBorderColor = c.base.borderInput,
            ),
        )
    }
}

/** Επιλογή σε κάψουλα: ένα κομμάτι ανά επιλογή, το επιλεγμένο στο χρώμα του θέματος. */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = LocalAppColors.current
    // Εξωτερική γωνία 14dp, κενό 4dp → εσωτερική 10dp: οι καμπύλες είναι ομόκεντρες.
    Surface(shape = RoundedCornerShape(Radius.md), color = c.base.surface2, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                val on = value == selected
                Surface(
                    shape = RoundedCornerShape(Radius.md - 4.dp),
                    color = if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (on) c.onAccent else c.base.text,
                    onClick = { onSelect(value) },
                    // 48dp: τόσο είναι και το ελάχιστο ύψος αφής του Material 3. Με 40dp ζωγραφιζόταν
                    // μικρότερο μέσα σε χώρο 48dp και το κενό πάνω/κάτω έβγαινε μεγαλύτερο από τα πλάγια.
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = MotoType.label, color = if (on) c.onAccent else c.base.text, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(e: RoutingException) {
    val c = LocalAppColors.current
    val text = when (e.kind) {
        RoutingException.Kind.NOT_INSTALLED ->
            "Για διαδρομές χωρίς internet χρειάζεται η δωρεάν εφαρμογή BRouter."
        RoutingException.Kind.NO_DATA ->
            "Λείπουν δεδομένα διαδρομών εδώ. BRouter → Download Manager → τετράγωνα Ελλάδας.\n(${e.message})"
        RoutingException.Kind.NO_PROFILE ->
            "Το BRouter δεν έχει προφίλ αυτοκινήτου (car-fast, car-eco).\n(${e.message})"
        RoutingException.Kind.NO_FIX -> e.message ?: ""
        RoutingException.Kind.TRAFFIC -> e.message ?: ""
        RoutingException.Kind.OTHER -> "Δεν βγήκε διαδρομή: ${e.message}"
    }
    Text(text, style = MotoType.body, color = c.base.text)
}

@Composable
private fun SpeedBox(speedKmh: Float?, limitKmh: Int? = null) {
    val c = LocalAppColors.current
    val sizes = LocalSizes.current
    val over = limitKmh != null && speedKmh != null && speedKmh > limitKmh + 3
    Box {
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = c.base.surface,
            border = BorderStroke(if (over) 2.dp else 1.dp, if (over) c.base.danger else c.outline),
            contentColor = c.base.text,
        ) {
            Column(
                modifier = Modifier
                    .size(width = sizes.speedBox * 1.15f, height = sizes.speedBox)
                    .padding(Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(speedKmh?.roundToInt()?.toString() ?: "—", style = sizes.speedText, color = if (over) c.base.danger else c.base.text)
                Text("km/h", style = MotoType.label, color = c.base.textMuted)
            }
        }
        // Όριο: σήμα ΚΟΚ πάνω αριστερά στο κουτί της ταχύτητας.
        if (limitKmh != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-6).dp, y = (-10).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.5.dp, Color(0xFFD32F2F), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$limitKmh",
                    style = MotoType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF111111),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, onClick: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(Radius.md)
    val content: @Composable () -> Unit = {
        Box(Modifier.heightIn(min = 40.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MotoType.label,
                color = c.base.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            )
        }
    }
    val bg = c.base.surface.copy(alpha = 0.92f)
    if (onClick != null) {
        Surface(shape = shape, color = bg, border = BorderStroke(1.dp, c.outline), onClick = onClick, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Surface(shape = shape, color = bg, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) { content() }
    }
}

// ---------------------------------------------------------------- μορφοποίηση

private val el = Locale("el", "GR")

private fun weatherPhraseEn(p: WeatherPoint): String = when {
    p.sky == Sky.STORM -> "storm"
    p.sky == Sky.SNOW -> "snow"
    p.rainProb >= 50 -> "rain"
    p.windKmh >= 45 -> "strong wind"
    p.tempC <= 2 -> "freezing"
    else -> "bad weather"
}

private fun weatherPhrase(p: WeatherPoint): String = when {
    p.sky == Sky.STORM -> "καταιγίδα"
    p.sky == Sky.SNOW -> "χιόνι"
    p.rainProb >= 50 -> "βροχή"
    p.windKmh >= 45 -> "δυνατός αέρας"
    p.tempC <= 2 -> "παγωνιά"
    else -> "κακός καιρός"
}

private fun formatShortDistance(m: Double): String = when {
    m < 1000 -> "${(m / 10.0).roundToInt() * 10} m"
    else -> String.format(el, "%.1f km", m / 1000.0)
}

private fun formatKm(m: Double): String = when {
    m < 1000 -> "${(m / 10.0).roundToInt() * 10} m"
    m < 100_000 -> String.format(el, "%.1f km", m / 1000.0)
    else -> "${(m / 1000.0).roundToInt()} km"
}

private fun formatDurationShort(s: Double): String {
    val min = (s / 60.0).roundToInt().coerceAtLeast(1)
    return if (min < 60) "$min′" else "${min / 60}ώ ${min % 60}′"
}

private fun formatDuration(s: Double): String {
    val min = (s / 60.0).roundToInt()
    return when {
        min <= 1 -> "1 λεπτό"
        min < 60 -> "$min λεπτά"
        else -> "${min / 60} ώ ${min % 60} λ"
    }
}

// ---------------------------------------------------------------- χάρτης

/**
 * Βελάκι ακριβώς στο κέντρο της οθόνης. Μηδενίζει το κενό (padding) που αφήνει η προεπισκόπηση
 * διαδρομής για την κάρτα, γιατί στο MapLibre μένει στην κάμερα και «ανεβάζει» το κέντρο.
 */
private const val STOPPED_KMH = 5f

/** Σταματημένος (< 5 km/h): κατεύθυνση από την πυξίδα, γιατί το GPS δεν ξέρει προς τα πού κοιτάς. */
private fun withCompassWhenStopped(loc: Location, compassDeg: Float?): Location {
    val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
    if (compassDeg == null || kmh >= STOPPED_KMH) return loc
    return Location(loc).apply { bearing = compassDeg }
}

/**
 * Θέση πάνω στη διαδρομή, όσο είσαι κοντά της (< 25 m). Μία ενημέρωση ανά στίγμα GPS,
 * υπολογισμένη εδώ από το ίδιο στίγμα (όχι από την προηγούμενη κατάσταση), για να κυλάει ομαλά.
 * Η κατεύθυνση μένει του GPS όταν κινείσαι (πιο ομαλή από τις γωνίες των τμημάτων).
 */
/** Πού «κόλλησε» το τελευταίο στίγμα πάνω στη διαδρομή: μέτρα από την αρχή, και πότε μετρήθηκε (ώρα GPS). */
class SnapInfo {
    var alongM: Double? = null
    var fixNanos: Long = 0L
    var speedMps: Float = 0f
}

/**
 * Το βελάκι ανάμεσα στα στίγματα (1 ανά δευτερόλεπτο): προχωράει πάνω στη γραμμή με την ταχύτητά σου,
 * από τη στιγμή που μετρήθηκε το στίγμα ως τώρα (έως 1,5″). Έτσι στις στροφές μένει πάνω στον δρόμο
 * (αντί να «κόβει» ευθεία από στίγμα σε στίγμα) και δεν μένει πίσω λόγω καθυστέρησης του GPS.
 */
/** Πού είναι τώρα το βελάκι πάνω στη γραμμή (μέτρα από την αρχή), με την ταχύτητα από το τελευταίο στίγμα. */
internal fun extrapolatedAlong(info: SnapInfo, nowNanos: Long): Double? {
    val along0 = info.alongM ?: return null
    if (info.speedMps < 5.5f) return null // < 20 km/h
    val dt = (nowNanos - info.fixNanos) / 1e9
    if (dt < 0 || dt > 1.5) return null
    return along0 + info.speedMps * dt
}

internal fun extrapolateOnRoute(route: Route, info: SnapInfo, nowNanos: Long, base: Location): Location? {
    val along = (extrapolatedAlong(info, nowNanos) ?: return null).coerceAtMost(route.lengthM)
    val p = route.pointAt(along)
    val ahead = route.pointAt((along + 10.0).coerceAtMost(route.lengthM))
    return Location(base).apply {
        latitude = p.lat
        longitude = p.lon
        if (Geo.distance(p, ahead) > 1.0) bearing = Geo.bearing(p, ahead).toFloat()
    }
}

private fun snapToRoute(loc: Location, st: NavState?, route: Route?, wasSnapped: Boolean = false, info: SnapInfo? = null): Location {
    info?.alongM = null
    if (st == null || route == null || route.points.size < 2) return loc
    val pts = route.points
    val p = LatLon(loc.latitude, loc.longitude)
    val from = (st.segIndex - 3).coerceAtLeast(0)
    val to = (st.segIndex + 80).coerceAtMost(pts.size - 2)
    var bestD = Double.MAX_VALUE
    var bestI = -1
    var bestT = 0.0
    for (i in from..to) {
        val (t, d) = Geo.project(p, pts[i], pts[i + 1])
        if (d < bestD) { bestD = d; bestI = i; bestT = t }
    }
    // Υστέρηση: για να «ξεκολλήσει» θέλει 40 m, για να ξανακολλήσει 25 m (αλλιώς τρεμοπαίζει στο όριο).
    // Με ταχύτητα (> 60 km/h) το GPS «ξεφεύγει» πιο εύκολα στις στροφές: 55 / 35 m.
    val fast = loc.hasSpeed() && loc.speed * 3.6f > 60f
    val limit = if (wasSnapped) (if (fast) 55.0 else 40.0) else (if (fast) 35.0 else 25.0)
    if (bestI < 0 || bestD > limit) return loc
    val a = pts[bestI]
    val b = pts[bestI + 1]
    info?.let {
        it.alongM = route.cumulative[bestI] + bestT * (route.cumulative[bestI + 1] - route.cumulative[bestI])
        it.fixNanos = loc.elapsedRealtimeNanos
        it.speedMps = if (loc.hasSpeed()) loc.speed else 0f
    }
    return Location(loc).apply {
        latitude = a.lat + (b.lat - a.lat) * bestT
        longitude = a.lon + (b.lon - a.lon) * bestT
        val moving = loc.hasSpeed() && loc.speed * 3.6f >= STOPPED_KMH
        if (moving && !loc.hasBearing()) bearing = Geo.bearing(a, b).toFloat()
    }
}

/**
 * Κρατάει το βελάκι ακίνητο όταν στέκεσαι. Κινείσαι μόνο αν 2 συνεχόμενα στίγματα έχουν ≥ 8 km/h με ακρίβεια ≤ 20 m.
 * Σταματημένος: μένει στη θέση του όσο τα νέα στίγματα πέφτουν μέσα σε max(25 m, 2×ακρίβεια).
 */
class PuckFilter {
    private var goodMoving = 0

    fun hold(distFromShownM: Float?, speedKmh: Float?, accuracyM: Float?): Boolean {
        val good = accuracyM == null || accuracyM <= 20f
        goodMoving = if (good && (speedKmh ?: 0f) >= 8f) goodMoving + 1 else 0
        if (distFromShownM == null) return false
        if (goodMoving >= 2) return false
        return distFromShownM < maxOf(25f, (accuracyM ?: 30f) * 2f)
    }
}

/**
 * Ζουμ στην πλοήγηση: πιο μακριά όσο πιο γρήγορα πας, πιο κοντά λίγο πριν τη στροφή.
 * Λίγο πιο ανοιχτό από πριν (όπως το Google Maps), για να βλέπεις περισσότερο δρόμο μπροστά.
 */
internal fun navZoomFor(speedKmh: Float, distToTurnM: Double?): Double {
    val bySpeed = when {
        speedKmh < 35f -> 17.0
        speedKmh < 60f -> 16.5
        speedKmh < 85f -> 15.9
        speedKmh < 110f -> 15.3
        else -> 14.8
    }
    val nearTurn = distToTurnM != null && distToTurnM < 250.0 && speedKmh < 90f
    return if (nearTurn) maxOf(bySpeed, 16.8) else bySpeed
}

/** Πού θέλουμε το βελάκι στην πλοήγηση: χαμηλά, στο 80% του ύψους, για περισσότερο ορίζοντα μπροστά. */
private const val PUCK_AT = 0.80

/** Το πάνω περιθώριο της κάμερας (σε px) που φέρνει το βελάκι στο PUCK_AT. Το διορθώνουμε με μέτρηση (βλ. κάτω). */
private val navTopByHeight = HashMap<Int, Double>()
private fun navTop(map: MapLibreMap): Double = navTopByHeight[map.height.toInt()] ?: (map.height * (2 * PUCK_AT - 1))

private fun navPadding(map: MapLibreMap): DoubleArray = doubleArrayOf(0.0, navTop(map), 0.0, 0.0)

/**
 * Με κλίση κάμερας το MapLibre δεν βάζει το βελάκι ακριβώς στο κέντρο του περιθωρίου (στο οριζόντιο έπεφτε τέρμα κάτω).
 * Μετράμε πού φαίνεται στην οθόνη και διορθώνουμε το περιθώριο, έως 3 φορές. Η διόρθωση μένει για αυτό το ύψος οθόνης.
 */
private fun correctPuckHeight(map: MapLibreMap, top: Double, attempt: Int) {
    if (attempt >= 3) return
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated || (lc.cameraMode != CameraMode.TRACKING_GPS && lc.cameraMode != CameraMode.TRACKING)) return@postDelayed
        val loc = lc.lastKnownLocation ?: return@postDelayed
        val h = map.height.toDouble()
        val y = map.projection.toScreenLocation(LatLng(loc.latitude, loc.longitude)).y.toDouble()
        val err = y - h * PUCK_AT
        if (kotlin.math.abs(err) < h * 0.03) return@postDelayed
        // Το βελάκι μετακινείται περίπου όσο το μισό της αλλαγής του περιθωρίου.
        val next = (top - 2 * err).coerceIn(-h * 0.3, h * 0.8)
        navTopByHeight[h.toInt()] = next
        runCatching { lc.paddingWhileTracking(doubleArrayOf(0.0, next, 0.0, 0.0), 400L) }
        correctPuckHeight(map, next, attempt + 1)
    }, 900L)
}

/** Βορράς πάνω (πάτημα στην πυξίδα) ή η κατεύθυνσή σου πάνω (κανονικά). */
object CameraPrefs {
    var northUp by mutableStateOf(false)
    var powerSave = false
    /** Κλίση στην πλοήγηση (Ρυθμίσεις → Χάρτης): 56° ή 40°. */
    var navTilt = 56.0
}

/** Η πυξίδα ως κουμπί: πάτημα = βορράς πάνω / ξανά = η κατεύθυνσή σου πάνω. Με βορρά πάνω έχει χρωματιστό δαχτυλίδι. */
@Composable
private fun NorthToggle(size: androidx.compose.ui.unit.Dp, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClickLabel = if (CameraPrefs.northUp) "Η κατεύθυνσή μου πάνω" else "Βορράς πάνω", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (CameraPrefs.northUp) {
            Box(Modifier.size(size).border(3.dp, c.accent, androidx.compose.foundation.shape.CircleShape))
        }
    }
}

private fun centerOnMe(map: MapLibreMap, navigating: Boolean) {
    val lc = map.locationComponent
    if (!lc.isLocationComponentActivated) return
    map.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
    // Ζουμ και κλίση μέσα στην ίδια εντολή: όσο κρατάει η μετάβαση, το MapLibre απορρίπτει
    // ξεχωριστό zoomWhileTracking (γι' αυτό πριν δεν έκανε ζουμ στο «Ξεκίνα»).
    // Πάντα «η κατεύθυνσή σου προς τα πάνω» (TRACKING_GPS): η κατεύθυνση έρχεται από το GPS εν κινήσει
    // και από την πυξίδα όταν είσαι σταματημένος.
    val north = CameraPrefs.northUp
    lc.setCameraMode(
        // Βορράς πάνω: ακολουθεί τη θέση αλλά δεν γυρίζει ο χάρτης (το βελάκι γυρίζει).
        if (north) CameraMode.TRACKING else CameraMode.TRACKING_GPS,
        900L,
        if (navigating) 17.0 else 16.0,
        if (north) 0.0 else null,
        // Λίγο περισσότερη κλίση: φαίνεται πιο μακριά ο δρόμος (ορίζοντας).
        if (navigating) CameraPrefs.navTilt else 0.0,
        object : OnLocationCameraTransitionListener {
            override fun onLocationCameraTransitionFinished(cameraMode: Int) {
                // Μετά τη μετάβαση (στη διάρκειά της το MapLibre απορρίπτει αλλαγές).
                if (navigating) {
                    lc.paddingWhileTracking(navPadding(map), 600L)
                    correctPuckHeight(map, navTop(map), 0)
                }
            }
            override fun onLocationCameraTransitionCanceled(cameraMode: Int) = Unit
        },
    )
}

private fun fitRoute(map: MapLibreMap, route: Route, landscape: Boolean, density: Float, bottomPanelPx: Int = 0, extra: List<Route> = emptyList()) {
    if (route.points.size < 2) return
    if (map.locationComponent.isLocationComponentActivated) map.locationComponent.cameraMode = CameraMode.NONE
    val bounds = LatLngBounds.Builder().apply {
        route.points.forEach { include(LatLng(it.lat, it.lon)) }
        extra.forEach { r -> r.points.forEach { include(LatLng(it.lat, it.lon)) } }
    }.build()
    // Αφήνουμε χώρο για τα πάνελ: αριστερά στην πλάγια, κάτω στην όρθια.
    val update = if (landscape) {
        CameraUpdateFactory.newLatLngBounds(bounds, (450 * density).toInt(), (30 * density).toInt(), (110 * density).toInt(), (30 * density).toInt())
    } else {
        // Κάτω: όσο πιάνει η κάρτα (+ λίγο αέρα), αλλά να μένει τουλάχιστον το 35% της οθόνης για τον χάρτη.
        val top = (110 * density).toInt()
        val wanted = if (bottomPanelPx > 0) bottomPanelPx + (70 * density).toInt() else (340 * density).toInt()
        val bottom = wanted.coerceAtMost((map.height * 0.65f).toInt() - top).coerceAtLeast((120 * density).toInt())
        CameraUpdateFactory.newLatLngBounds(bounds, (40 * density).toInt(), top, (40 * density).toInt(), bottom)
    }
    map.animateCamera(update)
}

@SuppressLint("MissingPermission")
private const val PUCK_IMAGE = "moto-puck"

/** Τέντωμα του βελακιού ώστε με κλίση κάμερας tiltDeg να φαίνεται με τις σωστές αναλογίες. */
internal fun puckStretch(tiltDeg: Double): Float = (1.0 / kotlin.math.cos(Math.toRadians(tiltDeg.coerceIn(0.0, 70.0)))).toFloat()

private fun showMyLocation(context: Context, map: MapLibreMap, style: Style, c: AppColors, marker: MarkerPrefs, tiltDeg: Double = 0.0) {
    style.addImage(PUCK_IMAGE, PuckArt.bitmap(context, marker, c.accent.toArgb(), puckStretch(tiltDeg)))
    val options = LocationComponentOptions.builder(context)
        // Βελάκι με περίγραμμα (εικόνα στο στυλ του χάρτη), με διακριτική σκιά. Χωρίς κύκλο ακρίβειας.
        .gpsDrawable(R.drawable.puck_arrow)
        .foregroundDrawable(R.drawable.puck_arrow)
        .gpsName(PUCK_IMAGE)
        .foregroundName(PUCK_IMAGE)
        .backgroundDrawable(R.drawable.puck_shadow)
        .backgroundTintColor(c.base.bg.copy(alpha = 0.55f).toArgb())
        .accuracyAlpha(0f)
        .minZoomIconScale(0.75f)
        .maxZoomIconScale(1f)
        .elevation(0f)
        .build()
    val lc = map.locationComponent
    if (!lc.isLocationComponentActivated) {
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .locationComponentOptions(options)
                // Τη θέση τη δίνουμε εμείς (forceLocationUpdate) από το ίδιο GPS με την πλοήγηση.
                .useDefaultLocationEngine(false)
                .build()
        )
        lc.isLocationComponentEnabled = true
        lc.renderMode = RenderMode.GPS
    } else {
        lc.applyStyle(options)
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun openBRouter(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(BRouterClient.PACKAGE)
    if (intent != null) context.startActivity(intent) else openBRouterInStore(context)
}

private fun openBRouterInStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${BRouterClient.PACKAGE}"))
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${BRouterClient.PACKAGE}"))
        )
    }
}

// ---------------------------------------------------------------- GPS

data class GpsState(
    val enabled: Boolean = true,
    /** Ζωντανό στίγμα από το GPS. */
    val location: Location? = null,
    /** Τελευταία γνωστή θέση του κινητού, μόνο για να ξεκινήσει ο χάρτης στο σωστό σημείο. */
    val lastKnown: Location? = null,
    val speedKmh: Float? = null,
    /** Δορυφόροι που χρησιμοποιούνται στο στίγμα (GnssStatus). */
    val satellites: Int? = null,
)

/** Στίγμα GPS 1 φορά το δευτερόλεπτο, κατευθείαν από το Android. */
@SuppressLint("MissingPermission")
@Composable
private fun rememberGps(permitted: Boolean, active: Boolean = true): MutableState<GpsState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(GpsState()) }
    DisposableEffect(permitted, active) {
        if (!active) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (app.motonav.nav.Simulator.active) return // προσομοίωση: το «GPS» το δίνει ο Simulator
                state.value = state.value.copy(
                    enabled = true,
                    location = loc,
                    speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else null,
                )
            }

            // Άνοιξες/έκλεισες το GPS με την εφαρμογή ανοιχτή: το βλέπουμε αμέσως.
            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) state.value = state.value.copy(enabled = true)
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) state.value = state.value.copy(enabled = false, location = null, speedKmh = null)
            }

            @Deprecated("Android < 10")
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }
        val gnss = object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                if (state.value.satellites != used) state.value = state.value.copy(satellites = used)
            }

            override fun onStopped() {
                state.value = state.value.copy(satellites = 0)
            }
        }
        if (permitted && lm != null) {
            val last = runCatching {
                listOfNotNull(
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
                    lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER),
                ).maxByOrNull { it.time }
            }.getOrNull()
            val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            state.value = state.value.copy(enabled = enabled, lastKnown = last)
            // Γραφόμαστε ΠΑΝΤΑ, και με κλειστό GPS: μόλις ανοίξει, τα στίγματα έρχονται χωρίς να ξανανοίξεις την εφαρμογή.
            runCatching {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            }
            runCatching { lm.registerGnssStatusCallback(gnss, android.os.Handler(Looper.getMainLooper())) }
        }
        onDispose {
            lm?.removeUpdates(listener)
            runCatching { lm?.unregisterGnssStatusCallback(gnss) }
        }
    }
    return state
}

@Composable
private fun rememberInstruments(location: Location?, active: Boolean = true): Pair<Instruments, () -> Unit> {
    val context = LocalContext.current
    var value by remember { mutableStateOf(Instruments()) }
    val engine = remember { InstrumentEngine(context.applicationContext) { value = it } }
    DisposableEffect(engine, active) {
        if (!active) return@DisposableEffect onDispose { }
        engine.start()
        onDispose { engine.stop() }
    }
    LaunchedEffect(location) { location?.let(engine::onLocation) }
    return value to engine::resetMax
}

/** true όταν το Android λέει ότι το κινητό ζεσταίνεται (thermal status ≥ MODERATE). Android 10+. */
@Composable
private fun rememberThermalHot(): Boolean {
    val context = LocalContext.current
    var hot by remember { mutableStateOf(false) }
    if (Build.VERSION.SDK_INT >= 29) {
        DisposableEffect(Unit) {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            val listener = android.os.PowerManager.OnThermalStatusChangedListener { s ->
                hot = s >= android.os.PowerManager.THERMAL_STATUS_MODERATE
            }
            pm?.let {
                hot = it.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE
                it.addThermalStatusListener(listener)
            }
            onDispose { pm?.removeThermalStatusListener(listener) }
        }
    }
    return hot
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    // Ξεκινά στα 30. Το MapScreen το αλλάζει ανάλογα με την κατάσταση (οδήγηση, ζέστη, μπαταρία).
    val mapView = remember { MapView(context).apply { onCreate(null); setMaximumFps(30) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return mapView
}

private const val KEEP_ON_AFTER_MOVING_MS = 3 * 60_000L

/** Φωτογραφία του μέρους (OpenStreetMap / Wikimedia Commons). Πάτημα: ολόκληρη η εικόνα. */
@Composable
private fun SpotPhotoBox(spot: app.motonav.spots.Spot) {
    val c = LocalAppColors.current
    var state by remember(spot.id) { mutableStateOf<Pair<androidx.compose.ui.graphics.ImageBitmap, app.motonav.spots.PhotoInfo>?>(null) }
    var searching by remember(spot.id) { mutableStateOf(true) }
    var full by remember(spot.id) { mutableStateOf(false) }
    LaunchedEffect(spot.id) {
        state = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val info = app.motonav.spots.SpotPhoto.find(spot) ?: return@runCatching null
                val b = app.motonav.spots.SpotPhoto.bytes(info.url)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) ?: return@runCatching null
                bmp.asImageBitmap() to info
            }.getOrNull()
        }
        searching = false
    }
    val s = state
    when {
        s != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                androidx.compose.foundation.Image(
                    s.first,
                    contentDescription = spot.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable { full = true },
                )
                Text(
                    (if (s.second.nearby) "Φωτογραφία κοντά στο σημείο · " else "") + s.second.credit,
                    style = MotoType.label.copy(fontSize = 11.sp),
                    color = c.base.textMuted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (full) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { full = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .clickable { full = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            s.first,
                            contentDescription = spot.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        searching -> Text("Ψάχνω φωτογραφία…", style = MotoType.label, color = c.base.textMuted)
        else -> Text("Δεν βρέθηκε φωτογραφία για αυτό το σημείο.", style = MotoType.label, color = c.base.textMuted)
    }
}
