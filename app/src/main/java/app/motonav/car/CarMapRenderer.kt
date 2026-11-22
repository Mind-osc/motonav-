package app.motonav.car

import android.app.Presentation
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import app.motonav.nav.NavSession
import app.motonav.ui.PuckArt
import app.motonav.ui.MarkerPrefs
import app.motonav.ui.RouteLayers
import app.motonav.ui.theme.ThemeSettings
import app.motonav.ui.theme.resolveColors
import org.maplibre.android.MapLibre
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

/**
 * Ο χάρτης στην οθόνη του Android Auto: ένα MapView μέσα σε Presentation, πάνω σε VirtualDisplay
 * που ζωγραφίζει στην επιφάνεια που μας δίνει το αυτοκίνητο.
 */
class CarMapRenderer(private val carContext: CarContext) : SurfaceCallback {
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var visible: Rect? = null
    private var lastRoute: Any? = null

    var session: NavSession? = null

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        release()
        MapLibre.getInstance(carContext.applicationContext)
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "MotoNavCar", container.width, container.height, container.dpi, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        display = vd
        val p = Presentation(carContext, vd.display)
        val options = MapLibreMapOptions.createFromAttributes(p.context).textureMode(true)
        val mv = MapView(p.context, options)
        p.setContentView(mv)
        p.show()
        presentation = p
        mapView = mv
        mv.onCreate(null)
        mv.onStart()
        mv.onResume()
        mv.getMapAsync { m ->
            map = m
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.setStyle(Style.Builder().fromUri(STYLE)) { s ->
                val colors = resolveColors(ThemeSettings())
                RouteLayers.install(s, colors)
                s.addImage(PUCK_IMG, PuckArt.bitmap(carContext, MarkerPrefs(sizeDp = 56), colors.accent.toArgb()))
                s.addSource(GeoJsonSource(PUCK_SRC))
                s.addLayer(
                    SymbolLayer(PUCK_LAYER, PUCK_SRC).withProperties(
                        PropertyFactory.iconImage(PUCK_IMG),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                        PropertyFactory.iconRotate(org.maplibre.android.style.expressions.Expression.get("b")),
                    ),
                )
                style = s
                lastRoute = null
                update()
            }
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = visibleArea
        update()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release()

    /** Διαδρομή, βελάκι και κάμερα από την τρέχουσα πλοήγηση. */
    fun update() {
        val m = map ?: return
        val s = style ?: return
        val sess = session
        val route = sess?.route
        if (route !== lastRoute) {
            RouteLayers.setRoute(s, route)
            RouteLayers.setDestination(s, sess?.destination)
            lastRoute = route
        }
        val st = sess?.state
        if (sess?.navigating == true && route != null && st != null) RouteLayers.setRemaining(s, route, st.segIndex, st.snapped)
        val loc: Location = sess?.lastLocation ?: return
        val here = st?.snapped?.takeIf { st.onRoute }
        val lat = here?.lat ?: loc.latitude
        val lon = here?.lon ?: loc.longitude
        val bearing = if (loc.hasBearing()) loc.bearing.toDouble() else m.cameraPosition.bearing
        (s.getSource(PUCK_SRC) as? GeoJsonSource)?.setGeoJson(
            org.maplibre.geojson.Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply { addNumberProperty("b", bearing) },
        )
        // Το βελάκι χαμηλά, μέσα στο ελεύθερο κομμάτι της οθόνης (όχι κάτω από τις κάρτες του αυτοκινήτου).
        val v = visible
        if (v != null) {
            val h = mapView?.height ?: 0
            val w = mapView?.width ?: 0
            if (h > 0 && w > 0) {
                val puckY = v.top + (v.height() * 0.72).toInt()
                m.setPadding(v.left, (puckY - (h - puckY)).coerceAtLeast(0), w - v.right, 0)
            }
        }
        val navigating = sess.navigating
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(lat, lon))
                    .zoom(if (navigating) 16.3 else 14.5)
                    .tilt(if (navigating) 50.0 else 0.0)
                    .bearing(if (navigating) bearing else 0.0)
                    .build(),
            ),
            900,
        )
    }

    fun release() {
        mapView?.let { runCatching { it.onPause(); it.onStop(); it.onDestroy() } }
        presentation?.dismiss()
        display?.release()
        mapView = null
        presentation = null
        display = null
        map = null
        style = null
    }

    private companion object {
        const val STYLE = "https://tiles.openfreemap.org/styles/dark"
        const val PUCK_IMG = "car-puck"
        const val PUCK_SRC = "car-puck-src"
        const val PUCK_LAYER = "car-puck-layer"
    }
}
