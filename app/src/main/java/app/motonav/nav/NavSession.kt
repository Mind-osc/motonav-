package app.motonav.nav

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.motonav.geo.LatLon
import app.motonav.routing.BRouterClient
import app.motonav.routing.Route
import app.motonav.routing.RouteType
import app.motonav.routing.RoutingException
import app.motonav.traffic.TomTomClient
import app.motonav.weather.RouteSampler
import app.motonav.weather.WeatherClient
import app.motonav.weather.WeatherPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Η κατάσταση από τον προορισμό ως την άφιξη:
 * προορισμός → υπολογισμός → προεπισκόπηση → πλοήγηση → άφιξη.
 */
class NavSession(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    val router = BRouterClient(appContext)
    private val voice = VoiceGuide(appContext)
    private val announcer = Announcer(voice)

    var destination by mutableStateOf<LatLon?>(null)
        private set
    var destinationName by mutableStateOf<String?>(null)
        private set

    /** Ενδιάμεσες στάσεις, με τη σειρά που θα περάσεις. */
    data class Stop(val position: LatLon, val name: String?)
    var stops by mutableStateOf<List<Stop>>(emptyList())
        private set
    /** true: το επόμενο σημείο που θα διαλέξεις μπαίνει ως στάση πριν τον προορισμό. */
    var addingStop by mutableStateOf(false)
    /** Πού πέφτει κάθε στάση πάνω στη διαδρομή (μέτρα από την αρχή), για να ξέρουμε πότε την πέρασες. */
    private var stopProgress: List<Double> = emptyList()
    var route by mutableStateOf<Route?>(null)
        private set
    var planning by mutableStateOf(false)
        private set
    var error by mutableStateOf<RoutingException?>(null)
        private set
    var navigating by mutableStateOf(false)
        private set
    var state by mutableStateOf<NavState?>(null)
        private set
    var rerouting by mutableStateOf(false)
        private set
    var arrived by mutableStateOf(false)
        private set
    // Η σίγαση θυμάται την τελευταία σου επιλογή.
    private val prefs = appContext.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE)
    var muted by mutableStateOf(prefs.getBoolean("muted", false))
        private set

    init {
        voice.muted = muted
    }

    var avoidMotorways by mutableStateOf(false)
        private set

    /** Εναλλακτικές διαδρομές στην προεπισκόπηση (ξεθωριασμένες στον χάρτη, πατάς για να διαλέξεις). */
    var alternatives by mutableStateOf<List<app.motonav.routing.Alternative>>(emptyList())
        private set
    var showAlternatives by mutableStateOf(true)
    private var altJob: Job? = null

    private fun clearAlternatives() {
        altJob?.cancel()
        alternatives = emptyList()
    }

    /** Ψάχνει εναλλακτικές για την κύρια διαδρομή, στο παρασκήνιο: η κύρια φαίνεται αμέσως. */
    private fun findAlternatives(main: Route, from: LatLon, dest: LatLon, heading: Float?) {
        clearAlternatives()
        if (!showAlternatives || followingTrack || navigating) return
        val via = stops.map { it.position }
        altJob = scope.launch {
            try {
                val raw = if (routeType == RouteType.TRAFFIC) {
                    TomTomClient(trafficKey).routes(from, dest, heading, avoidTolls, avoidMotorways, via, 3).drop(1)
                } else {
                    router.alternatives(listOf(from) + via + dest, heading, avoidMotorways, routeType, avoidTolls)
                }
                val alts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    app.motonav.routing.Alternatives.build(main, raw)
                }
                if (route === main && !navigating) alternatives = alts
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // χωρίς εναλλακτικές: μένει η κύρια
            }
        }
    }

    /** Πάτησες εναλλακτική: γίνεται κύρια, και η παλιά κύρια γίνεται εναλλακτική. */
    fun selectAlternative(index: Int) {
        if (navigating) return
        val old = route ?: return
        val alt = alternatives.getOrNull(index) ?: return
        val next = alt.route
        val others = alternatives.filterIndexed { i, _ -> i != index }.map { it.route } + old
        route = next
        indexStops(next)
        fetchStreetNames(next)
        resetTraffic()
        refreshTraffic(force = true)
        refreshWeather(force = true)
        alternatives = app.motonav.routing.Alternatives.build(next, others)
    }
    var routeType by mutableStateOf(RouteType.FASTEST)
        private set
    var avoidTolls by mutableStateOf(false)
        private set

    fun toggleAvoidTolls() {
        avoidTolls = !avoidTolls
        if (!navigating && destination != null) plan()
    }

    /** Καιρός στα σημεία της διαδρομής (ή μόνο εδώ, χωρίς διαδρομή). */
    var weather by mutableStateOf<List<WeatherPoint>>(emptyList())
        private set
    private val weatherClient = WeatherClient()
    private var weatherJob: Job? = null
    private var lastWeatherAt = 0L
    private var weatherForRoute: Route? = null

    /** Κλειδί TomTom από τις Ρυθμίσεις. Κενό = χωρίς live κίνηση. */
    var trafficKey by mutableStateOf("")

    /** Χρόνος με κίνηση: ο συντελεστής πολλαπλασιάζει τον χρόνο που μένει (1.0 = όπως χωρίς κίνηση). */
    var trafficFactor by mutableStateOf(1.0)
        private set
    var trafficDelayS by mutableStateOf(0.0)
        private set
    var trafficKnown by mutableStateOf(false)
        private set
    /** Πού έχει κίνηση πάνω στη διαδρομή (για να βαφτεί η γραμμή). */
    var routeTraffic by mutableStateOf(app.motonav.traffic.RouteTraffic.NONE)
        private set
    private var trafficJob: Job? = null
    private var lastTrafficAt = 0L

    val greekVoiceMissing: Boolean get() = voice.greekMissing

    private var engine: NavigationEngine? = null
    private var recorder: TripRecorder? = null
    private var planJob: Job? = null
    private var lastRerouteAt = 0L
    private var lastFix: Location? = null

    /** Πόσο παλιό είναι το τελευταίο στίγμα (για το Android Auto: να μη στέλνουμε διπλά στίγματα). */
    val lastFixAgeMs: Long
        get() = lastFix?.let { (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 } ?: Long.MAX_VALUE
    val lastLocation: Location? get() = lastFix

    /** Παρατεταμένο πάτημα στον χάρτη. */
    /** true: ακολουθούμε έτοιμο ίχνος (GPX). Δεν υπολογίζεται άλλη διαδρομή, ούτε αν βγεις από αυτό. */
    var followingTrack by mutableStateOf(false)
        private set
    private var offTrackSaid = false
    private var namesJob: Job? = null

    /** Ονόματα δρόμων στις στροφές (μόνο για BRouter· το TomTom τα έχει ήδη). Αν δεν έχει internet, τίποτα. */
    private fun fetchStreetNames(r: Route) {
        namesJob?.cancel()
        if (r.source != "BRouter") return
        namesJob = scope.launch {
            try {
                app.motonav.routing.StreetNames.fill(r)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun followTrack(points: List<LatLon>, name: String = "Διαδρομή GPX") {
        if (navigating) return
        val r = app.motonav.routing.TrackRoute.build(points) ?: return
        planJob?.cancel()
        planning = false
        error = null
        followingTrack = true
        destination = r.points.last()
        destinationName = name
        stops = emptyList()
        addingStop = false
        arrived = false
        resetTraffic()
        route = r
        refreshWeather(force = true)
    }

    /** true όσο ψάχνει κυκλική βόλτα (παίρνει περισσότερο από μια απλή διαδρομή). */
    var loopPlanning by mutableStateOf(false)
        private set

    /** Κυκλική βόλτα με στροφές που γυρίζει εδώ. Τα ενδιάμεσα σημεία της γίνονται στάσεις, για να μείνει κύκλος αν ξαναϋπολογιστεί. */
    fun planLoop(hours: Int) {
        if (navigating) return
        val fix = lastFix ?: return
        val start = LatLon(fix.latitude, fix.longitude)
        planJob?.cancel()
        followingTrack = false
        addingStop = false
        error = null
        arrived = false
        destination = start
        destinationName = if (hours == 1) "Βόλτα 1 ώρας" else "Βόλτα $hours ωρών"
        stops = emptyList()
        route = null
        routeType = RouteType.TWISTY
        planning = true
        loopPlanning = true
        planJob = scope.launch {
            try {
                resetTraffic()
                val (r, via) = router.loop(start, hours * 3600.0, avoidTolls)
                stops = via.mapIndexed { i, p -> Stop(p, "Βόλτα · σημείο ${i + 1}") }
                indexStops(r)
                route = r
                fetchStreetNames(r)
                refreshWeather(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RoutingException) {
                error = e
            } catch (e: Exception) {
                error = RoutingException(e.message ?: e.toString(), RoutingException.Kind.OTHER)
            } finally {
                planning = false
                loopPlanning = false
            }
        }
    }

    fun pick(p: LatLon, name: String? = null) {
        if (navigating) return
        followingTrack = false
        if (addingStop && destination != null) {
            stops = stops + Stop(p, name)
        } else {
            destination = p
            destinationName = name
            stops = emptyList()
        }
        addingStop = false
        route = null
        arrived = false
        plan()
    }

    /**
     * Στάση και στη μέση της πλοήγησης (π.χ. βενζινάδικο από τη λίστα): μπαίνει στη σωστή σειρά
     * ανάμεσα στις άλλες στάσεις και βγαίνει αμέσως νέα διαδρομή από εκεί που είσαι.
     */
    fun addStopNow(p: LatLon, name: String?) {
        val r = route
        if (!navigating || r == null) {
            if (destination == null) return
            addingStop = true
            pick(p, name)
            return
        }
        indexStops(r)
        val along = app.motonav.routing.OnRoute.project(r, p)?.alongM ?: r.lengthM
        val idx = stopProgress.indexOfFirst { it > along }.let { if (it < 0) stops.size else it }
        stops = stops.toMutableList().apply { add(idx, Stop(p, name)) }
        indexStops(r)
        val fix = lastFix ?: return
        if (!rerouting) reroute(fix)
    }

    fun removeStop(index: Int) {
        if (navigating || index !in stops.indices) return
        stops = stops.filterIndexed { i, _ -> i != index }
        plan()
    }

    /** Ανεβάζει τη στάση μία θέση πιο πριν. */
    fun moveStopUp(index: Int) {
        if (navigating || index !in 1..stops.lastIndex) return
        stops = stops.toMutableList().apply { add(index - 1, removeAt(index)) }
        plan()
    }

    private fun indexStops(r: Route) {
        var from = 0
        stopProgress = stops.map { s ->
            val (i, m) = r.progressNear(s.position, from)
            from = i
            m
        }
    }

    fun chooseRouteType(type: RouteType) {
        if (routeType == type) return
        routeType = type
        if (!navigating && destination != null) plan()
    }

    fun toggleAvoidMotorways() {
        avoidMotorways = !avoidMotorways
        if (!navigating && destination != null) plan()
    }

    fun clear() {
        stop()
        planJob?.cancel()
        planning = false
        clearAlternatives()
        followingTrack = false
        destination = null
        destinationName = null
        stops = emptyList()
        addingStop = false
        route = null
        error = null
        arrived = false
    }

    fun plan() {
        if (followingTrack) return
        clearAlternatives()
        val dest = destination ?: return
        val fix = lastFix
        if (fix == null) {
            error = RoutingException("Περιμένω σήμα GPS για να ξέρω από πού ξεκινάς.", RoutingException.Kind.NO_FIX)
            return
        }
        planJob?.cancel()
        error = null
        planning = true
        planJob = scope.launch {
            try {
                val from = LatLon(fix.latitude, fix.longitude)
                val heading = if (fix.hasBearing() && fix.speed > 3f) fix.bearing else null
                resetTraffic()
                route = computeRoute(from, dest, heading).also {
                    indexStops(it)
                    fetchStreetNames(it)
                    findAlternatives(it, from, dest, heading)
                }
                refreshWeather(force = true)
                refreshTraffic(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RoutingException) {
                error = e
            } catch (e: Exception) {
                error = RoutingException(e.message ?: e.toString(), RoutingException.Kind.OTHER)
            } finally {
                planning = false
            }
        }
    }

    fun start() {
        val r = route ?: return
        clearAlternatives()
        engine = NavigationEngine(r)
        announcer.reset()
        arrived = false
        navigating = true
        NavBus.navigating.value = true
        recorder = TripRecorder(appContext).also { it.start() }
        // Η φράση εκκίνησης (Ρυθμίσεις → Φωνή πλοήγησης) δεν κόβεται από την πρώτη οδηγία.
        val phrase = ElevenStore.startPhrase(appContext)
            .replace("{προορισμός}", destinationName ?: "τον προορισμό")
        voice.sayProtected(phrase)
        NavService.start(appContext, "Ξεκινάμε")
    }

    fun stop() {
        if (navigating) NavService.stop(appContext)
        navigating = false
        NavBus.navigating.value = false
        state = null
        engine = null
        rerouting = false
        recorder?.finish()
        recorder = null
        voice.silence()
    }

    fun toggleMute() {
        muted = !muted
        voice.muted = muted
        prefs.edit().putBoolean("muted", muted).apply()
        if (muted) voice.silence()
    }

    /** Κάθε στίγμα GPS (1 Hz). */
    fun onLocation(loc: Location) {
        val first = lastFix == null
        lastFix = loc
        if (first && destination != null && route == null && !planning) plan()
        refreshWeather()
        refreshTraffic()

        recorder?.add(loc)
        val e = engine ?: return
        val s = e.update(LatLon(loc.latitude, loc.longitude), loc.accuracy)
        state = s
        if (s.arrived) {
            announcer.arrived()
            stop()
            arrived = true
            route = null
            destination = null
            destinationName = null
            stops = emptyList()
            followingTrack = false
            return
        }
        // Πέρασες στάση: βγαίνει από τη λίστα, για να μη σε γυρίσει πίσω μια νέα διαδρομή.
        if (!rerouting && stops.isNotEmpty() && stopProgress.isNotEmpty() && s.progressM >= stopProgress.first() - 40.0) {
            val name = stops.first().name
            stops = stops.drop(1)
            stopProgress = stopProgress.drop(1)
            voice.say(if (name != null) "Στάση: $name." else "Έφτασες στη στάση.", interrupt = false)
        }
        if (!rerouting) announcer.onState(s, loc.speed)
        val now = SystemClock.elapsedRealtime()
        if (followingTrack) {
            // Σε ίχνος GPX δεν αλλάζουμε διαδρομή: μόνο ειδοποίηση, μία φορά κάθε φορά που βγαίνεις.
            if (s.offRoute && !offTrackSaid) {
                offTrackSaid = true
                voice.say("Βγήκες από τη διαδρομή.", interrupt = false)
            } else if (s.onRoute) {
                offTrackSaid = false
            }
        } else if (s.offRoute && !rerouting && now - lastRerouteAt > 5_000) reroute(loc)
    }

    private fun reroute(loc: Location) {
        val dest = destination ?: return
        rerouting = true
        lastRerouteAt = SystemClock.elapsedRealtime()
        voice.say("Υπολογίζω νέα διαδρομή.")
        scope.launch {
            try {
                val r = computeRoute(
                    LatLon(loc.latitude, loc.longitude),
                    dest,
                    if (loc.hasBearing()) loc.bearing else null,
                )
                if (navigating) {
                    route = r
                    indexStops(r)
                    fetchStreetNames(r)
                    engine = NavigationEngine(r)
                    announcer.reset()
                    resetTraffic()
                    refreshTraffic(force = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Κρατάμε την παλιά διαδρομή και ξαναδοκιμάζουμε σε λίγα δευτερόλεπτα.
            } finally {
                rerouting = false
            }
        }
    }

    /**
     * Καιρός: με διαδρομή, σημεία ανά ~15 km την ώρα που θα περάσεις. Χωρίς διαδρομή, μόνο εδώ.
     * Ανανέωση κάθε 15 λεπτά, ή αμέσως όταν αλλάξει η διαδρομή.
     */
    fun refreshWeather(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val r = route
        val routeChanged = r !== weatherForRoute
        if (!force && !routeChanged && now - lastWeatherAt < 15 * 60_000L) return
        if (weatherJob?.isActive == true && !force) return
        val fix = lastFix ?: return
        lastWeatherAt = now
        weatherForRoute = r
        val startS = System.currentTimeMillis() / 1000
        val samples = if (r != null) {
            RouteSampler.sample(r, startS)
        } else {
            listOf(Triple(LatLon(fix.latitude, fix.longitude), 0.0, startS))
        }
        weatherJob?.cancel()
        weatherJob = scope.launch {
            try {
                weather = weatherClient.forecast(samples)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // χωρίς internet: κρατάμε ό,τι είχαμε, ξαναδοκιμάζουμε σε 2 λεπτά
                lastWeatherAt = SystemClock.elapsedRealtime() - 13 * 60_000L
            }
        }
    }

    private suspend fun computeRoute(from: LatLon, dest: LatLon, heading: Float?): Route {
        val via = stops.map { it.position }
        if (routeType == RouteType.TRAFFIC) {
            if (trafficKey.isBlank()) {
                throw RoutingException(
                    "Για διαδρομή με κίνηση βάλε κλειδί TomTom στις Ρυθμίσεις → Live κίνηση.",
                    RoutingException.Kind.TRAFFIC,
                )
            }
            return try {
                TomTomClient(trafficKey).route(from, dest, heading, avoidTolls, avoidMotorways, via)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Εν κινήσει χωρίς internet: συνεχίζουμε offline με το BRouter.
                if (navigating) router.route(listOf(from) + via + dest, heading, avoidMotorways, RouteType.FASTEST, avoidTolls) else throw e
            }
        }
        return router.route(listOf(from) + via + dest, heading, avoidMotorways, routeType, avoidTolls)
    }

    private fun resetTraffic() {
        trafficJob?.cancel()
        trafficFactor = 1.0
        trafficDelayS = 0.0
        trafficKnown = false
        lastTrafficAt = 0L
    }

    /**
     * Χρόνος με την τωρινή κίνηση για ό,τι μένει από τη διαδρομή (TomTom).
     * Μετά τον υπολογισμό και κάθε 5 λεπτά στην πλοήγηση.
     */
    fun refreshTraffic(force: Boolean = false) {
        val key = trafficKey
        val r = route ?: return
        if (key.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastTrafficAt < 5 * 60_000L) return
        if (trafficJob?.isActive == true && !force) return
        lastTrafficAt = now

        // Διαδρομή TomTom: ο χρόνος και η κίνηση ήρθαν ήδη μαζί της, δεν ξαναρωτάμε.
        if (r.source == "TomTom" && routeTraffic.route !== r) {
            trafficJob?.cancel()
            routeTraffic = app.motonav.traffic.RouteTraffic(r, r.trafficJams)
            trafficFactor = 1.0
            trafficDelayS = r.trafficDelayS
            trafficKnown = true
            return
        }

        val progress = state?.progressM ?: 0.0
        val startIdx = r.cumulative.indexOfFirst { it >= progress }.let { if (it < 0) r.points.lastIndex else it }
        val remainingPts = buildList {
            lastFix?.takeIf { navigating }?.let { add(LatLon(it.latitude, it.longitude)) }
            addAll(r.points.subList(startIdx, r.points.size))
        }
        if (remainingPts.size < 2) return
        val baseRemaining = r.durationS * (r.lengthM - progress).coerceAtLeast(0.0) / r.lengthM.coerceAtLeast(1.0)
        trafficJob?.cancel()
        trafficJob = scope.launch {
            try {
                val eta = TomTomClient(key).eta(remainingPts)
                if (route === r && baseRemaining > 30) {
                    trafficFactor = (eta.durationS / baseRemaining).coerceIn(0.5, 4.0)
                    trafficDelayS = eta.delayS
                    trafficKnown = true
                }
                if (route === r) {
                    routeTraffic = app.motonav.traffic.RouteTraffic(
                        r,
                        app.motonav.traffic.RouteTraffic.map(r, eta.jams, (startIdx - 1).coerceAtLeast(0)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // χωρίς internet ή κλειδί: μένει ο χρόνος χωρίς κίνηση, ξαναδοκιμάζουμε σε 1 λεπτό
                lastTrafficAt = SystemClock.elapsedRealtime() - 4 * 60_000L
            }
        }
    }

    /** Το «Έφτασες!» φεύγει μετά από λίγο. */
    fun clearArrived() {
        arrived = false
    }

    /** Κλίση από τα όργανα, για τα μέγιστα στο ημερολόγιο. */
    fun recordLean(deg: Float) {
        recorder?.lean(deg)
    }

    /** Σύντομη φωνητική ειδοποίηση (π.χ. κάμερα), και εκτός πλοήγησης. Σέβεται τη σίγαση. */
    fun announce(text: String) {
        voice.say(text, interrupt = false)
    }

    fun dispose() {
        stop()
        voice.shutdown()
    }
}
