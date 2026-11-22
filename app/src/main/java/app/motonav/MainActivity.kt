package app.motonav

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.lifecycle.lifecycleScope
import app.motonav.nav.NavBus
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import app.motonav.data.DisplayPrefs
import app.motonav.data.SettingsRepo
import app.motonav.ui.SettingsScreen
import app.motonav.ui.TripLogScreen
import app.motonav.geo.LatLon
import app.motonav.ui.AppearanceScreen
import app.motonav.ui.MapScreen
import app.motonav.ui.theme.MotoTheme
import app.motonav.ui.theme.ThemeMode
import app.motonav.ui.theme.rememberAutoDark
import app.motonav.ui.theme.ThemeSettings
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    /** Μικρό παράθυρο πάνω από άλλες εφαρμογές (picture-in-picture). */
    private var inPip by mutableStateOf(false)

    private fun pipParams(auto: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder().setAspectRatio(Rational(3, 4)).apply {
            if (Build.VERSION.SDK_INT >= 31) {
                setAutoEnterEnabled(auto)
                setSeamlessResizeEnabled(false)
            }
        }.build()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+: μπαίνει μόνο του (setAutoEnterEnabled). Παλιότερα: εδώ.
        if (Build.VERSION.SDK_INT < 31 && NavBus.navigating.value) {
            runCatching { enterPictureInPictureMode(pipParams(false)) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    /** Άνοιξε από QR/link παρέας: δείχνουμε την οθόνη της παρέας. */
    private var openGroup by mutableStateOf(false)

    private fun handleLink(intent: android.content.Intent?) {
        val data = intent?.data?.toString() ?: return
        if (data.startsWith("motonav://join")) {
            app.motonav.group.GroupLink.join(data)
            openGroup = true
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.motonav.group.GroupLink.init(applicationContext)
        handleLink(intent)
        if (Build.VERSION.SDK_INT >= 31) {
            lifecycleScope.launch {
                NavBus.navigating.collect { nav -> runCatching { setPictureInPictureParams(pipParams(nav)) } }
            }
        }
        enableEdgeToEdge()
        // Η οθόνη μένει αναμμένη μόνο στην πλοήγηση ή όταν κινείσαι (το ρυθμίζει ο χάρτης).
        MapLibre.getInstance(this)
        val repo = SettingsRepo(applicationContext)

        setContent {
            val settings by repo.theme.collectAsStateWithLifecycle(initialValue = ThemeSettings())
            val display by repo.display.collectAsStateWithLifecycle(initialValue = DisplayPrefs())
            val scope = rememberCoroutineScope()
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showAppearance by rememberSaveable { mutableStateOf(false) }
            var showTrips by rememberSaveable { mutableStateOf(false) }
            var showOffline by rememberSaveable { mutableStateOf(false) }
            var showMarker by rememberSaveable { mutableStateOf(false) }
            var showGroup by rememberSaveable { mutableStateOf(false) }
            var settingsPage by rememberSaveable { mutableStateOf("main") }
            if (openGroup) { showGroup = true; openGroup = false }
            val marker by repo.marker.collectAsStateWithLifecycle(initialValue = app.motonav.ui.MarkerPrefs())
            var shownTrack by remember { mutableStateOf<List<LatLon>?>(null) }

            val autoDark = rememberAutoDark(
                enabled = settings.mode == ThemeMode.AUTO &&
                    androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
                        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED),
            )
            // Κρύβουμε τη γραμμή του κινητού στον χάρτη (φαίνεται για λίγο αν σύρεις από πάνω).
            val overlay = showSettings || showAppearance || showTrips || showOffline || showMarker || showGroup
            androidx.compose.runtime.LaunchedEffect(display.customStatus, overlay, inPip) {
                val ctl = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                ctl.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (display.customStatus && !overlay && !inPip) ctl.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                else ctl.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
            // Μέγεθος όλου του UI (Ρυθμίσεις → Στον χάρτη): μεγαλώνει/μικραίνει κουμπιά και κείμενα, όχι τον χάρτη.
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density * display.uiScale, baseDensity.fontScale),
            ) {
            MotoTheme(settings, autoDark) {
                Box(Modifier.fillMaxSize()) {
                    // Ο χάρτης μένει πάντα ζωντανός από κάτω, για να μη ξαναφορτώνει.
                    MapScreen(
                        display = display,
                        onOpenSettings = { settingsPage = "main"; showSettings = true },
                        shownTrack = shownTrack,
                        onClearTrack = { shownTrack = null },
                        pip = inPip,
                    )
                    if (showSettings && !inPip) {
                        SettingsScreen(
                            display = display,
                            onDisplayChange = { d -> scope.launch { repo.saveDisplay(d) } },
                            onOpenAppearance = { showAppearance = true },
                            onOpenTrips = { showTrips = true },
                            onOpenOffline = { showOffline = true },
                            onOpenMarker = { showMarker = true },
                            onOpenGroup = { showGroup = true },
                            page = settingsPage,
                            onPage = { settingsPage = it },
                            onBack = { showSettings = false },
                        )
                    }
                    if (showGroup && !inPip) {
                        val ctx = this@MainActivity
                        app.motonav.ui.GroupScreen(
                            onScan = {
                                runCatching {
                                    val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                        .build()
                                    com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, opts).startScan()
                                        .addOnSuccessListener { b -> b.rawValue?.let { app.motonav.group.GroupLink.join(it) } }
                                }
                            },
                            onBack = { showGroup = false },
                        )
                    }
                    if (showMarker && !inPip) {
                        app.motonav.ui.MarkerScreen(marker, onChange = { m -> scope.launch { repo.saveMarker(m) } }, onBack = { showMarker = false })
                    }
                    if (showOffline && !inPip) {
                        app.motonav.ui.OfflineMapsScreen(onBack = { showOffline = false })
                    }
                    if (showTrips && !inPip) {
                        TripLogScreen(
                            onShowOnMap = { pts ->
                                shownTrack = pts
                                showTrips = false
                                showSettings = false
                            },
                            onBack = { showTrips = false },
                        )
                    }
                    if (showAppearance && !inPip) {
                        AppearanceScreen(
                            settings = settings,
                            onChange = { s -> scope.launch { repo.save(s) } },
                            onBack = { showAppearance = false },
                        )
                    }
                }
                BackHandler(enabled = showSettings && !showAppearance && !showTrips && !showOffline && !showMarker && !showGroup) {
                    if (settingsPage != "main") settingsPage = "main" else showSettings = false
                }
                BackHandler(enabled = showMarker) { showMarker = false }
                BackHandler(enabled = showGroup) { showGroup = false }
                BackHandler(enabled = showOffline) { showOffline = false }
                BackHandler(enabled = showTrips) { showTrips = false }
                BackHandler(enabled = showAppearance) { showAppearance = false }
            }
            }
        }
    }
}
