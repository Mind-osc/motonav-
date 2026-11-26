package app.motonav.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.motonav.search.Place
import app.motonav.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import app.motonav.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class DisplayPrefs(
    val showLean: Boolean = true,
    val showCompass: Boolean = true,
    val showAltitude: Boolean = true,
    /** Χρώματα κίνησης στον χάρτη (θέλει κλειδί TomTom). */
    val showTraffic: Boolean = true,
    /** Κλειδί TomTom του χρήστη. Μένει μόνο στο κινητό. */
    val tomtomKey: String = "",
    /** Κάμερες στον χάρτη και κίτρινη μπάρα 300 m πριν. */
    val showCameras: Boolean = true,
    /** Φωνή «Κάμερα σε 300 μέτρα». */
    val cameraVoice: Boolean = true,
    /** Ανάγλυφο βουνών και κορυφές με υψόμετρο. */
    val showRelief: Boolean = true,
    /** Κλειδί MapTiler για ισοϋψείς. Μένει μόνο στο κινητό. */
    val maptilerKey: String = "",
    val showContours: Boolean = true,
    /** Χωματόδρομοι με καφέ διακεκομμένη στον χάρτη. */
    val showDirt: Boolean = true,
    /** Μέρη για μηχανόβιους στον χάρτη (θέες, νερό, αυχένες, στέκια, συνεργεία μηχανών…). */
    val showSpots: Boolean = true,
    /** Ποια είδη μερών μηχανόβιων ΔΕΝ θες να φαίνονται (ονόματα του SpotKind). */
    val hiddenSpots: Set<String> = emptySet(),
    /** Εναλλακτικές διαδρομές με τη διαφορά χρόνου, στην προεπισκόπηση. */
    val showAlternatives: Boolean = true,
    /** «Στροφές» με ακόμα περισσότερες στροφές (δέχεται μεγαλύτερη παράκαμψη). */
    val moreCurves: Boolean = false,
    /** Όχημα: αυτοκίνητο (χωρίς «Χώμα», χωρίς κλισιόμετρο, TomTom για αυτοκίνητο). */
    val car: Boolean = false,
    /** Εξοικονόμηση: λιγότερα καρέ, λιγότερη κλίση, χωρίς ανάγλυφο. */
    val powerSave: Boolean = false,
    /** Κλίση του χάρτη στην πλοήγηση: 56° (βλέπεις πιο μακριά, προεπιλογή) ή 40° (λιγότερη). */
    val navTilt: Int = 56,
    /** Χρώμα της γραμμής ανάλογα με την κλίση του δρόμου. */
    val slopeColors: Boolean = false,
    /** «Επόμενα σημεία στη διαδρομή»: ποια είδη (ονόματα του RoutePoiKind). Κενό = κλειστό. */
    val nextPois: Set<String> = setOf("FUEL", "CAFE", "FOOD", "WATER"),
    /** Αποφυγές: πλοία, κύριοι δρόμοι, πολύ στενοί δρόμοι, χωματόδρομοι. */
    val avoidFerries: Boolean = false,
    val avoidMain: Boolean = false,
    val avoidNarrow: Boolean = false,
    val avoidUnpaved: Boolean = false,
    /** Κουμπιά χειριστηρίου τιμονιού: ενέργεια → keyCode (κείμενο «zoom_in=19,zoom_out=20,…»). */
    val keyMap: String = "",
    /** Δική μας ώρα/μπαταρία/δορυφόροι πάνω πάνω, αντί για τη γραμμή του κινητού. */
    val customStatus: Boolean = true,
    /** Μέγεθος όλων των κουμπιών και κειμένων (1 = κανονικό). */
    val uiScale: Float = 1f,
    /** Η ταμπέλα στροφής παίρνει το χρώμα του δρόμου όπου βγαίνεις. */
    val roadColors: Boolean = true,
    /** Παλλόμενες άκρες οθόνης: όριο, κίνδυνος, κάμερα. */
    val edgeWarnings: Boolean = true,
    /** Κόκκινος φωτισμός πάνω όταν περνάς το όριο (μέρος των παλλόμενων άκρων). */
    val overspeedGlow: Boolean = true,
    /** Το πληκτρολόγιο ανοίγει μόνο του όταν ανοίγεις την αναζήτηση (αλλιώς: όταν πατήσεις στο πεδίο). */
    val searchKeyboard: Boolean = false,
    /** Φωνή «Όριο 50» όταν το περνάς, και για κακό καιρό μπροστά. */
    val speedVoice: Boolean = true,
    /** Βενζινάδικα πάνω στη διαδρομή (OpenStreetMap). */
    val showFuel: Boolean = true,
    /** Καρό σημαία στο πάνω μέρος της οθόνης πάνω από 120 km/h. */
    val flagOver120: Boolean = false,
)

class SettingsRepo(private val context: Context) {
    private val mode = stringPreferencesKey("theme_mode")
    private val preset = intPreferencesKey("accent_preset")
    private val hue = floatPreferencesKey("custom_hue")
    private val customColorKey = intPreferencesKey("custom_color")

    val theme: Flow<ThemeSettings> = context.settingsStore.data.map { p ->
        ThemeSettings(
            mode = runCatching { ThemeMode.valueOf(p[mode] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            accentPreset = p[preset] ?: 0,
            customHue = p[hue] ?: 270f,
            customColor = p[customColorKey] ?: 0,
        )
    }

    suspend fun save(s: ThemeSettings) {
        context.settingsStore.edit { p ->
            p[mode] = s.mode.name
            p[preset] = s.accentPreset
            p[hue] = s.customHue
            p[customColorKey] = s.customColor
        }
    }

    // ---- Τι φαίνεται στον χάρτη

    private val showLeanKey = booleanPreferencesKey("show_lean")
    private val showCompassKey = booleanPreferencesKey("show_compass")
    private val showAltitudeKey = booleanPreferencesKey("show_altitude")
    private val showTrafficKey = booleanPreferencesKey("show_traffic")
    private val tomtomKeyKey = stringPreferencesKey("tomtom_key")
    private val showCamerasKey = booleanPreferencesKey("show_cameras")
    private val cameraVoiceKey = booleanPreferencesKey("camera_voice")
    private val reliefKey = booleanPreferencesKey("show_relief")
    private val maptilerKeyKey = stringPreferencesKey("maptiler_key")
    private val contoursKey = booleanPreferencesKey("show_contours")
    private val dirtKey = booleanPreferencesKey("show_dirt")
    private val spotsKey = booleanPreferencesKey("show_spots")
    private val hiddenSpotsKey = stringPreferencesKey("hidden_spots")
    private val altKey = booleanPreferencesKey("show_alternatives")
    private val curvesKey = booleanPreferencesKey("more_curves")
    private val carKey = booleanPreferencesKey("vehicle_car")
    private val powerKey = booleanPreferencesKey("power_save")
    private val tiltKey = intPreferencesKey("nav_tilt")
    private val slopeKey = booleanPreferencesKey("slope_colors")
    private val nextPoisKey = stringPreferencesKey("next_pois")
    private val avFerryKey = booleanPreferencesKey("avoid_ferries")
    private val avMainKey = booleanPreferencesKey("avoid_main")
    private val avNarrowKey = booleanPreferencesKey("avoid_narrow")
    private val avUnpavedKey = booleanPreferencesKey("avoid_unpaved")
    private val keyMapKey = stringPreferencesKey("key_map")
    private val statusKey = booleanPreferencesKey("custom_status")
    private val scaleKey = floatPreferencesKey("ui_scale")
    private val roadColorsKey = booleanPreferencesKey("road_colors")
    private val edgeKey = booleanPreferencesKey("edge_warnings")
    private val overGlowKey = booleanPreferencesKey("overspeed_glow")
    private val searchKbKey = booleanPreferencesKey("search_keyboard")
    private val speedVoiceKey = booleanPreferencesKey("speed_voice")
    private val fuelKey = booleanPreferencesKey("show_fuel")
    private val flagKey = booleanPreferencesKey("flag_over_120")

    val display: Flow<DisplayPrefs> = context.settingsStore.data.map { p ->
        DisplayPrefs(
            showLean = p[showLeanKey] ?: true,
            showCompass = p[showCompassKey] ?: true,
            showAltitude = p[showAltitudeKey] ?: true,
            showTraffic = p[showTrafficKey] ?: true,
            tomtomKey = p[tomtomKeyKey] ?: "",
            showCameras = p[showCamerasKey] ?: true,
            cameraVoice = p[cameraVoiceKey] ?: true,
            showRelief = p[reliefKey] ?: true,
            maptilerKey = p[maptilerKeyKey] ?: "",
            showContours = p[contoursKey] ?: true,
            showDirt = p[dirtKey] ?: true,
            showSpots = p[spotsKey] ?: true,
            hiddenSpots = p[hiddenSpotsKey]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            showAlternatives = p[altKey] ?: true,
            moreCurves = p[curvesKey] ?: false,
            car = p[carKey] ?: false,
            powerSave = p[powerKey] ?: false,
            navTilt = p[tiltKey] ?: 56,
            slopeColors = p[slopeKey] ?: false,
            nextPois = p[nextPoisKey]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: setOf("FUEL", "CAFE", "FOOD", "WATER"),
            avoidFerries = p[avFerryKey] ?: false,
            avoidMain = p[avMainKey] ?: false,
            avoidNarrow = p[avNarrowKey] ?: false,
            avoidUnpaved = p[avUnpavedKey] ?: false,
            keyMap = p[keyMapKey] ?: "",
            customStatus = p[statusKey] ?: true,
            uiScale = p[scaleKey] ?: 1f,
            roadColors = p[roadColorsKey] ?: true,
            edgeWarnings = p[edgeKey] ?: true,
            overspeedGlow = p[overGlowKey] ?: true,
            searchKeyboard = p[searchKbKey] ?: false,
            speedVoice = p[speedVoiceKey] ?: true,
            showFuel = p[fuelKey] ?: true,
            flagOver120 = p[flagKey] ?: false,
        )
    }

    // ---- Βελάκι και γραμμή διαδρομής

    private val mShape = stringPreferencesKey("marker_shape")
    private val mSize = intPreferencesKey("marker_size")
    private val mFill = intPreferencesKey("marker_fill")
    private val mOutline = booleanPreferencesKey("marker_outline")
    private val mOutlineColor = intPreferencesKey("marker_outline_color")
    private val mLine = intPreferencesKey("line_color")
    private val mLineOutline = intPreferencesKey("line_outline_color")
    private val mLineOutlineW = floatPreferencesKey("line_outline_width")

    val marker: Flow<app.motonav.ui.MarkerPrefs> = context.settingsStore.data.map { p ->
        val def = app.motonav.ui.MarkerPrefs()
        app.motonav.ui.MarkerPrefs(
            shape = p[mShape]?.let { s -> app.motonav.ui.PuckShape.entries.firstOrNull { it.name == s } } ?: def.shape,
            sizeDp = p[mSize] ?: def.sizeDp,
            fill = p[mFill],
            outline = p[mOutline] ?: def.outline,
            outlineColor = p[mOutlineColor] ?: def.outlineColor,
            lineColor = p[mLine],
            lineOutlineColor = p[mLineOutline],
            lineOutlineDp = p[mLineOutlineW] ?: def.lineOutlineDp,
        )
    }

    suspend fun saveMarker(m: app.motonav.ui.MarkerPrefs) {
        context.settingsStore.edit { p ->
            p[mShape] = m.shape.name
            p[mSize] = m.sizeDp
            if (m.fill == null) p.remove(mFill) else p[mFill] = m.fill
            p[mOutline] = m.outline
            p[mOutlineColor] = m.outlineColor
            if (m.lineColor == null) p.remove(mLine) else p[mLine] = m.lineColor
            if (m.lineOutlineColor == null) p.remove(mLineOutline) else p[mLineOutline] = m.lineOutlineColor
            p[mLineOutlineW] = m.lineOutlineDp
        }
    }

    suspend fun saveDisplay(d: DisplayPrefs) {
        context.settingsStore.edit { p ->
            p[showLeanKey] = d.showLean
            p[showCompassKey] = d.showCompass
            p[showAltitudeKey] = d.showAltitude
            p[showTrafficKey] = d.showTraffic
            p[tomtomKeyKey] = d.tomtomKey.trim()
            p[showCamerasKey] = d.showCameras
            p[cameraVoiceKey] = d.cameraVoice
            p[reliefKey] = d.showRelief
            p[maptilerKeyKey] = d.maptilerKey.trim()
            p[contoursKey] = d.showContours
            p[dirtKey] = d.showDirt
            p[spotsKey] = d.showSpots
            p[hiddenSpotsKey] = d.hiddenSpots.joinToString(",")
            p[altKey] = d.showAlternatives
            p[curvesKey] = d.moreCurves
            p[carKey] = d.car
            p[powerKey] = d.powerSave
            p[tiltKey] = d.navTilt
            p[slopeKey] = d.slopeColors
            p[nextPoisKey] = d.nextPois.joinToString(",")
            p[avFerryKey] = d.avoidFerries
            p[avMainKey] = d.avoidMain
            p[avNarrowKey] = d.avoidNarrow
            p[avUnpavedKey] = d.avoidUnpaved
            p[keyMapKey] = d.keyMap
            p[statusKey] = d.customStatus
            p[scaleKey] = d.uiScale
            p[roadColorsKey] = d.roadColors
            p[edgeKey] = d.edgeWarnings
            p[overGlowKey] = d.overspeedGlow
            p[searchKbKey] = d.searchKeyboard
            p[speedVoiceKey] = d.speedVoice
            p[fuelKey] = d.showFuel
            p[flagKey] = d.flagOver120
        }
    }

    suspend fun saveKeyMap(text: String) {
        context.settingsStore.edit { p -> p[keyMapKey] = text }
    }

    // ---- Πρόσφατα μέρη (τα 10 τελευταία που διάλεξες στην αναζήτηση)

    private val recentKey = stringPreferencesKey("recent_places")

    val recents: Flow<List<Place>> = context.settingsStore.data.map { p -> decode(p[recentKey]) }

    suspend fun addRecent(place: Place) {
        context.settingsStore.edit { p ->
            val list = decode(p[recentKey]).filterNot { it.name == place.name && it.lat == place.lat && it.lon == place.lon }
            p[recentKey] = encode((listOf(place) + list).take(10))
        }
    }

    // ---- Σπίτι, δουλειά, αγαπημένα

    private val homeKey = stringPreferencesKey("place_home")
    private val workKey = stringPreferencesKey("place_work")
    private val favKey = stringPreferencesKey("place_favorites")
    private val MAX_FAVORITES = 2000

    val home: Flow<Place?> = context.settingsStore.data.map { p -> decode(p[homeKey]).firstOrNull() }
    val work: Flow<Place?> = context.settingsStore.data.map { p -> decode(p[workKey]).firstOrNull() }
    val favorites: Flow<List<Place>> = context.settingsStore.data.map { p -> decode(p[favKey]) }

    suspend fun setHome(place: Place) {
        context.settingsStore.edit { p -> p[homeKey] = encode(listOf(place)) }
    }

    suspend fun setWork(place: Place) {
        context.settingsStore.edit { p -> p[workKey] = encode(listOf(place)) }
    }

    /** Προσθέτει ή βγάζει (ίδιο σημείο σε ακτίνα ~20 m = ίδιο μέρος). */
    suspend fun toggleFavorite(place: Place) {
        context.settingsStore.edit { p ->
            val list = decode(p[favKey])
            val same = { o: Place -> kotlin.math.abs(o.lat - place.lat) < 2e-4 && kotlin.math.abs(o.lon - place.lon) < 2e-4 }
            p[favKey] = encode(if (list.any(same)) list.filterNot(same) else (listOf(place) + list).take(MAX_FAVORITES))
        }
    }

    private val samePoint = { a: Place, b: Place -> kotlin.math.abs(a.lat - b.lat) < 2e-4 && kotlin.math.abs(a.lon - b.lon) < 2e-4 }

    /** Αποθήκευση σε λίστα (αν το σημείο υπάρχει ήδη, μετακινείται). */
    suspend fun saveFavorite(place: Place) {
        context.settingsStore.edit { p ->
            val list = decode(p[favKey]).filterNot { samePoint(it, place) }
            val color = place.color.takeIf { it != 0 } ?: list.firstOrNull { it.listName == place.listName }?.color ?: 0
            p[favKey] = encode((listOf(place.copy(color = color)) + list).take(MAX_FAVORITES))
        }
    }

    suspend fun removeFavorite(place: Place) {
        context.settingsStore.edit { p -> p[favKey] = encode(decode(p[favKey]).filterNot { samePoint(it, place) }) }
    }

    /** Εισαγωγή πολλών (π.χ. από GPX/KML) σε μία λίστα. */
    suspend fun importFavorites(listName: String, color: Int, places: List<Place>) {
        context.settingsStore.edit { p ->
            val old = decode(p[favKey]).filterNot { o -> places.any { samePoint(o, it) } }
            val add = places.map { it.copy(list = listName, color = color, kind = it.kind.ifBlank { "saved" }) }
            p[favKey] = encode((add + old).take(MAX_FAVORITES))
        }
    }

    suspend fun deleteList(listName: String) {
        context.settingsStore.edit { p -> p[favKey] = encode(decode(p[favKey]).filterNot { it.listName == listName }) }
    }

    suspend fun setListColor(listName: String, color: Int) {
        context.settingsStore.edit { p -> p[favKey] = encode(decode(p[favKey]).map { if (it.listName == listName) it.copy(color = color) else it }) }
    }

    suspend fun renameList(from: String, to: String) {
        if (to.isBlank()) return
        context.settingsStore.edit { p -> p[favKey] = encode(decode(p[favKey]).map { if (it.listName == from) it.copy(list = to.trim()) else it }) }
    }

    private fun encode(list: List<Place>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject().put("n", it.name).put("d", it.detail).put("la", it.lat).put("lo", it.lon).put("k", it.kind)
                    .apply { if (it.list.isNotBlank()) put("l", it.list); if (it.color != 0) put("c", it.color) },
            )
        }
    }.toString()

    private fun decode(s: String?): List<Place> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Place(o.optString("n"), o.optString("d"), o.optDouble("la"), o.optDouble("lo"), o.optString("k"), o.optString("l"), o.optInt("c"))
        }
    }.getOrDefault(emptyList())
}
