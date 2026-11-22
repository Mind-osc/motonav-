package app.motonav

import app.motonav.cameras.CameraParser
import app.motonav.cameras.SpeedCamera
import app.motonav.routing.BRouterJson
import app.motonav.trips.Gpx
import app.motonav.trips.TripStats
import app.motonav.trips.TripSummary
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripsAndLimitsTest {
    @Test fun maxspeedFromWayTags() {
        assertEquals(50, BRouterJson.maxspeedFrom("highway=primary maxspeed=50 surface=asphalt"))
        assertEquals(90, BRouterJson.maxspeedFrom("maxspeed=90"))
        assertNull(BRouterJson.maxspeedFrom("highway=residential maxspeed:type=GR:urban"))
    }

    @Test fun speedLimitsAlongRoute() {
        val m = JSONArray(
            """[["Longitude","Latitude","Elevation","Distance","CostPerKm","ElevCost","TurnCost","NodeCost","InitialCost","WayTags","NodeTags","Time","Energy"],
               ["23700000","37900000","10","120","1000","0","0","0","0","highway=primary maxspeed=50","","10","0"],
               ["23701000","37901000","10","300","1000","0","0","0","0","highway=trunk maxspeed=90","","30","0"],
               ["23702000","37902000","10","80","1000","0","0","0","0","highway=service","","40","0"]]"""
        )
        val limits = BRouterJson.parseSpeedLimits(m)
        assertEquals(3, limits.size)
        assertEquals(120.0, limits[0].first, 0.01)
        assertEquals(420.0, limits[1].first, 0.01)
        val r = app.motonav.routing.Route(
            points = emptyList(), elevations = emptyList(), cumulative = DoubleArray(0),
            maneuvers = emptyList(), durationS = 0.0, speedLimits = limits,
        )
        assertEquals(50, r.speedLimitAt(60.0))
        assertEquals(90, r.speedLimitAt(200.0))
        assertNull(r.speedLimitAt(450.0))
    }

    @Test fun gpxRoundTripAndStats() {
        val gpx = """<gpx><trk><trkseg>
            <trkpt lat="37.900000" lon="23.700000"><ele>100.0</ele><time>2026-11-01T10:00:00Z</time></trkpt>
            <trkpt lat="37.901000" lon="23.700000"><ele>110.0</ele><time>2026-11-01T10:00:10Z</time></trkpt>
            <trkpt lat="37.902000" lon="23.700000"><time>2026-11-01T10:00:20Z</time></trkpt>
            </trkseg></trk></gpx>"""
        val pts = Gpx.parse(gpx)
        assertEquals(3, pts.size)
        assertEquals(110.0, pts[1].ele!!, 0.01)
        assertNull(pts[2].ele)
        val s = Gpx.summarize("t", pts)
        assertEquals(222.0, s.distanceM, 3.0)
        assertEquals(20.0, s.movingS, 0.1)
        assertEquals(110.0, s.maxAltM!!, 0.01)
        assertTrue(s.maxSpeedKmh in 38.0..42.0)
        val back = TripSummary.fromJson(TripSummary.toJson(s))!!
        assertEquals(s, back)
    }

    @Test fun leanMaxima() {
        val st = TripStats()
        listOf(-12.0, 30.0, -41.5, 5.0, 28.0).forEach(st::lean)
        st.add(37.9, 23.7, 0, null, null)
        val s = st.summary("x")
        assertEquals(41.5, s.maxLeanLeft, 0.01)
        assertEquals(30.0, s.maxLeanRight, 0.01)
    }

    @Test fun cameraKindsSurviveJson() {
        val list = listOf(
            SpeedCamera("a", 37.99, 23.73, source = "attica", kind = "red", label = "Αλεξάνδρας & Πατησίων"),
            SpeedCamera("b", 38.0, 23.7, maxspeed = 50),
        )
        val back = CameraParser.fromJson(CameraParser.toJson(list))
        assertEquals(list, back)
        assertEquals("Κάμερα φαναριού", back[0].title)
        assertEquals("Κάμερα", back[1].title)
    }
}

class SearchRankTest {
    private val near = app.motonav.geo.LatLon(37.99, 23.73) // κέντρο Αθήνας

    @Test fun townBeatsNearbyStreetWithSameRoot() {
        val street = app.motonav.search.Place("Αχαρνών", "Αθήνα", 37.995, 23.728, "residential")
        val town = app.motonav.search.Place("Αχαρνές", "Ανατολική Αττική", 38.083, 23.735, "town")
        val r = app.motonav.search.PlaceParsers.rank("αχαρνες", listOf(street, town), emptyList(), near)
        assertEquals("Αχαρνές", r.first().name)
    }

    @Test fun normalizeIgnoresAccentsAndFinalSigma() {
        assertEquals("αχαρνεσ", app.motonav.search.PlaceParsers.normalize("Αχαρνές"))
        assertEquals(0, app.motonav.search.PlaceParsers.textScore("ΑΧΑΡΝΕΣ", "Αχαρνές"))
        assertEquals(2, app.motonav.search.PlaceParsers.textScore("Αχαρνές", "Δήμος Αχαρνών"))
    }

    @Test fun duplicatesFromBothSourcesMerge() {
        val a = app.motonav.search.Place("Αχαρνές", "", 38.083, 23.735, "town")
        val b = app.motonav.search.Place("Αχαρνές", "", 38.080, 23.740, "city")
        assertEquals(1, app.motonav.search.PlaceParsers.rank("Αχαρνές", listOf(a), listOf(b), near).size)
    }

    @Test fun nominatimParse() {
        val json = """[{"lat":"38.0833","lon":"23.7333","name":"Αχαρνές","addresstype":"town","display_name":"Αχαρνές, Δήμος Αχαρνών, Αττική","address":{"town":"Αχαρνές","county":"Περιφερειακή Ενότητα Ανατολικής Αττικής","country":"Ελλάς","country_code":"gr"}}]"""
        val p = app.motonav.search.PlaceParsers.nominatim(json).single()
        assertEquals("Αχαρνές", p.name)
        assertEquals("town", p.kind)
        assertEquals("Περιφερειακή Ενότητα Ανατολικής Αττικής", p.detail)
    }
}

class HouseNumberSearchTest {
    private val near = app.motonav.geo.LatLon(38.08, 23.73) // Αχαρνές

    @Test fun splitsNumberEitherSide() {
        assertEquals("αριστοτελους" to "227", app.motonav.search.PlaceParsers.splitHouseNumber("αριστοτελους 227"))
        assertEquals("Αριστοτέλους" to "12α", app.motonav.search.PlaceParsers.splitHouseNumber("12α Αριστοτέλους"))
        assertNull(app.motonav.search.PlaceParsers.splitHouseNumber("Αριστοτέλους"))
        assertNull(app.motonav.search.PlaceParsers.splitHouseNumber("28ης Οκτωβρίου"))
    }

    @Test fun nearbyStreetBeatsFarExactNumber() {
        val far = app.motonav.search.Place("Αριστοτέλους 227", "Άστρος", 37.41, 22.72, "house")
        val nearStreet = app.motonav.search.Place("Αριστοτέλους", "χωρίς τον αριθμό 227 · Αχαρνές", 38.081, 23.731, "residential")
        val r = app.motonav.search.PlaceParsers.rank("αριστοτελους 227", listOf(far, nearStreet), emptyList(), near)
        assertEquals(nearStreet, r.first())
    }

    @Test fun nearbyExactNumberWins() {
        val exact = app.motonav.search.Place("Αριστοτέλους 227", "Αχαρνές", 38.09, 23.74, "house")
        val street = app.motonav.search.Place("Αριστοτέλους", "Αχαρνές", 38.081, 23.731, "residential")
        val r = app.motonav.search.PlaceParsers.rank("αριστοτελους 227", listOf(street, exact), emptyList(), near)
        assertEquals(exact, r.first())
    }
}

class TomTomSearchTest {
    @Test fun parsesPointAddress() {
        val json = """{"results":[{"type":"Point Address","position":{"lat":38.08,"lon":23.73},
            "address":{"streetName":"Αριστοτέλους","streetNumber":"227","municipality":"Αχαρνές","countrySecondarySubdivision":"Ανατολική Αττική"}},
            {"type":"POI","poi":{"name":"Shell"},"position":{"lat":38.0,"lon":23.7},"address":{"streetName":"Φυλής","municipality":"Άνω Λιόσια"}}]}"""
        val r = app.motonav.search.PlaceParsers.tomtom(json)
        assertEquals("Αριστοτέλους 227", r[0].name)
        assertEquals("house", r[0].kind)
        assertEquals("Αχαρνές, Ανατολική Αττική", r[0].detail)
        assertEquals("Shell", r[1].name)
        assertEquals("Φυλής, Άνω Λιόσια", r[1].detail)
    }
}

class AutoThemeTest {
    private fun ms(iso: String) = java.time.Instant.parse(iso).toEpochMilli()

    @Test fun sunOverAthens() {
        val sun = app.motonav.ui.theme.Sun
        assertTrue(sun.elevationDeg(37.98, 23.73, ms("2026-11-09T10:00:00Z")) in 30.0..40.0) // μεσημέρι Νοεμβρίου
        assertTrue(sun.isNight(37.98, 23.73, ms("2026-11-09T18:00:00Z")))                    // 20:00 τον Νοέμβριο
        assertTrue(!sun.isNight(37.98, 23.73, ms("2026-06-21T17:00:00Z")))                   // 20:00 τον Ιούνιο: ακόμα μέρα
        assertTrue(sun.isNight(37.98, 23.73, ms("2026-06-21T19:00:00Z")))                    // 22:00 τον Ιούνιο
    }

    @Test fun tunnelNeedsTimeBothWays() {
        val l = app.motonav.ui.theme.LowLight()
        assertTrue(!l.update(5f, 0))
        assertTrue(!l.update(5f, 1_000))
        assertTrue(l.update(5f, 1_600))      // 1,5 s σκοτάδι → τούνελ
        assertTrue(l.update(30f, 2_000))     // ενδιάμεσο φως: μένει
        assertTrue(l.update(500f, 3_000))
        assertTrue(l.update(500f, 5_000))
        assertTrue(!l.update(500f, 6_100))   // 3 s φως → έξω
    }
}

class StopsTest {
    @Test fun progressNearFindsStopsInOrder() {
        val pts = (0..10).map { app.motonav.geo.LatLon(38.0, 23.0 + it * 0.001) }
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + app.motonav.geo.Geo.distance(pts[i - 1], pts[i])
        val r = app.motonav.routing.Route(pts, pts.map { null }, cum, emptyList(), 0.0)
        val (i, m) = r.progressNear(app.motonav.geo.LatLon(38.0002, 23.0051))
        assertEquals(5, i)
        assertEquals(cum[5], m, 0.01)
        assertEquals(8, r.progressNear(app.motonav.geo.LatLon(38.0, 23.0), fromIndex = 8).first)
    }
}

class BuildBTest {
    private fun ll(lat: Double, lon: Double) = app.motonav.geo.LatLon(lat, lon)

    @Test fun gpxAnyAttributeOrderAndRoutePoints() {
        val a = Gpx.parse("""<gpx><trk><trkseg><trkpt lon="23.70" lat="37.90"/><trkpt lon='23.71' lat='37.91'><ele> 5 </ele><time>2026-11-01T10:00:00.000+02:00</time></trkpt></trkseg></trk></gpx>""")
        assertEquals(2, a.size)
        assertEquals(37.91, a[1].position.lat, 1e-9)
        assertEquals(5.0, a[1].ele!!, 1e-9)
        assertEquals(java.time.Instant.parse("2026-11-01T08:00:00Z").toEpochMilli(), a[1].timeMs)
        val r = Gpx.parse("""<gpx><rte><rtept lat="38.0" lon="23.0"></rtept><rtept lat="38.1" lon="23.1"></rtept></rte></gpx>""")
        assertEquals(2, r.size)
    }

    @Test fun trackRouteFindsRightTurn() {
        // 300 m βόρεια, μετά 300 m ανατολικά
        val pts = (0..30).map { ll(38.0 + it * 0.00009, 23.0) } + (1..30).map { ll(38.0027, 23.0 + it * 0.000114) }
        val r = app.motonav.routing.TrackRoute.build(pts)!!
        val turns = r.maneuvers.filter { it.turn != app.motonav.routing.Turn.ARRIVE }
        assertEquals(1, turns.size)
        assertEquals(app.motonav.routing.Turn.RIGHT, turns[0].turn)
        assertEquals(300.0, turns[0].atMeters, 15.0)
        assertEquals(app.motonav.routing.Turn.ARRIVE, r.maneuvers.last().turn)
    }

    @Test fun streetNamePicksRoadAfterTheTurn() {
        val json = """{"elements":[
          {"type":"way","tags":{"name":"Κάθετη"},"geometry":[{"lat":38.0,"lon":23.0},{"lat":38.002,"lon":23.0}]},
          {"type":"way","tags":{"name":"Αριστοτέλους"},"geometry":[{"lat":38.001,"lon":23.0},{"lat":38.001,"lon":23.002}]}]}"""
        val ways = app.motonav.routing.StreetNames.parse(json)
        val m = app.motonav.routing.Maneuver(0, app.motonav.routing.Turn.RIGHT, 0, 90, 0.0)
        // δύο σημεία 25 m και 55 m μετά τη στροφή, πάνω στην Αριστοτέλους
        val probe = app.motonav.routing.StreetNames.Probe(m, ll(38.001, 23.00028), ll(38.001, 23.00063))
        assertEquals("Αριστοτέλους", app.motonav.routing.StreetNames.match(probe, ways))
    }

    @Test fun tomtomLanesAndExit() {
        val json = """{"routes":[{"summary":{"travelTimeInSeconds":60},
          "legs":[{"points":[{"latitude":38.0,"longitude":23.0},{"latitude":38.001,"longitude":23.0},{"latitude":38.002,"longitude":23.0}]}],
          "sections":[{"sectionType":"LANES","startPointIndex":0,"endPointIndex":1,
            "lanes":[{"directions":["STRAIGHT"]},{"directions":["STRAIGHT","SLIGHT_RIGHT"],"follow":"SLIGHT_RIGHT"}]}],
          "guidance":{"instructions":[{"maneuver":"MOTORWAY_EXIT_RIGHT","pointIndex":1,"routeOffsetInMeters":111,
            "exitNumber":"12","signpostText":"Λαμία","street":"Α1"}]}}]}"""
        val r = app.motonav.traffic.TomTomParser.route(json)
        val lanes = r.lanesAt(50.0)!!
        assertEquals(2, lanes.size)
        assertEquals("SLIGHT_RIGHT", lanes[1].follow)
        assertNull(r.lanesAt(200.0))
        assertEquals("έξοδος 12 · Λαμία", app.motonav.nav.Phrases.road(r.maneuvers.first()))
    }
}

class BuildCTest {
    @Test fun loopPointsSitOnACircleThroughTheStart() {
        val start = app.motonav.geo.LatLon(38.08, 23.73)
        val lengthM = 100_000.0
        val radius = lengthM / app.motonav.routing.LoopPlanner.ROAD_FACTOR / (2 * Math.PI)
        val via = app.motonav.routing.LoopPlanner.loop(start, lengthM, 0)
        assertEquals(2, via.size)
        val centre = app.motonav.routing.LoopPlanner.offset(start, 0.0, radius)
        via.forEach { assertEquals(radius, app.motonav.geo.Geo.distance(centre, it), radius * 0.01) }
        assertEquals(radius, app.motonav.geo.Geo.distance(centre, start), radius * 0.01)
        // τα τρία σημεία απέχουν το ίδιο μεταξύ τους (ισόπλευρο τρίγωνο)
        val side = app.motonav.geo.Geo.distance(via[0], via[1])
        assertEquals(side, app.motonav.geo.Geo.distance(start, via[0]), side * 0.02)
        assertTrue(via.all { it.lat > start.lat }) // γέρνει βόρεια
    }

    @Test fun greeceTileCountIsModest() {
        val g = app.motonav.map.OfflineMaps.GREECE
        val n = app.motonav.map.OfflineMaps.tileCount(g[0], g[1], g[2], g[3], 12)
        assertTrue("$n", n in 10_000..20_000)
        assertEquals(1L, app.motonav.map.OfflineMaps.tileCount(g[0], g[1], g[2], g[3], 0))
        // η Αττική με όλα τα στενά χωράει στο όριο
        val attica = app.motonav.map.OfflineMaps.tileCount(37.6, 23.3, 38.4, 24.2, 14)
        assertTrue("$attica", attica < app.motonav.map.OfflineMaps.AREA_MAX_TILES)
    }
}

class PuckFilterTest {
    @Test fun indoorJitterIsHeld() {
        val f = app.motonav.ui.PuckFilter()
        assertTrue(!f.hold(null, 0f, 40f))          // πρώτο στίγμα: δείξ' το
        assertTrue(f.hold(35f, 6f, 40f))             // 35 m με ακρίβεια 40: μένει
        assertTrue(f.hold(60f, 12f, 35f))            // ψεύτικη ταχύτητα με κακή ακρίβεια: μένει
        assertTrue(f.hold(20f, 9f, 10f))             // ένα καλό στίγμα «κίνησης» δεν φτάνει
        assertTrue(!f.hold(20f, 9f, 10f))            // δεύτερο συνεχόμενο: κινείσαι
        assertTrue(!f.hold(200f, 0f, 30f))           // πολύ μακριά: πραγματική αλλαγή θέσης
    }
}

class AlternativesTest {
    private fun line(vararg p: Pair<Double, Double>): app.motonav.routing.Route {
        val pts = p.map { app.motonav.geo.LatLon(it.first, it.second) }
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + app.motonav.geo.Geo.distance(pts[i - 1], pts[i])
        return app.motonav.routing.Route(pts, pts.map { null }, cum, emptyList(), cum.last() / 15.0)
    }

    @Test fun keepsDifferentDropsSame() {
        val main = line(38.0 to 23.0, 38.0 to 23.01, 38.0 to 23.02)
        val same = line(38.0 to 23.0, 38.0001 to 23.01, 38.0 to 23.02)            // ~11 m δίπλα
        val north = line(38.0 to 23.0, 38.004 to 23.01, 38.0 to 23.02)            // ~450 m βόρεια, +11%
        val north2 = line(38.0 to 23.0, 38.0042 to 23.01, 38.0 to 23.02)          // σχεδόν ίδια με τη north
        val south = line(38.0 to 23.0, 37.996 to 23.01, 38.0 to 23.02)
        val alts = app.motonav.routing.Alternatives.build(main, listOf(same, north, north2, south))
        assertEquals(2, alts.size)
        assertEquals(38.004, alts[0].labelAt.lat, 1e-9)
        assertEquals(37.996, alts[1].labelAt.lat, 1e-9)
    }

    @Test fun dropsAbsurdDetours() {
        // 1,8 km κύρια· «εναλλακτική» με κύκλο 1 km βόρεια = σχεδόν διπλάσια: όχι.
        val main = line(38.0 to 23.0, 38.0 to 23.01, 38.0 to 23.02)
        val loop = line(38.0 to 23.0, 38.01 to 23.01, 38.0 to 23.02)
        assertEquals(0, app.motonav.routing.Alternatives.build(main, listOf(loop)).size)
    }


    @Test fun deltaLabels() {
        assertEquals("+3′", app.motonav.routing.Alternatives.deltaLabel(780.0, 600.0))
        assertEquals("−2′", app.motonav.routing.Alternatives.deltaLabel(480.0, 600.0))
        assertEquals("ίδια ώρα", app.motonav.routing.Alternatives.deltaLabel(620.0, 600.0))
    }

    @Test fun tomtomSeveralRoutes() {
        val one = """{"summary":{"travelTimeInSeconds":%d},"legs":[{"points":[{"latitude":38.0,"longitude":23.0},{"latitude":38.0%d,"longitude":23.0}]}]}"""
        val json = """{"routes":[${one.format(60, 1)},${one.format(90, 2)}]}"""
        val rs = app.motonav.traffic.TomTomParser.routes(json)
        assertEquals(2, rs.size)
        assertEquals(90.0, rs[1].durationS, 0.1)
    }
}

class GroupCodesTest {
    private val G = app.motonav.group.GroupCodes

    @Test fun codesAreValidAndNormalize() {
        val c = G.newCode(java.util.Random(1))
        assertEquals(16, c.length)
        assertEquals(c, G.normalize(c))
        assertEquals(c, G.normalize(G.pretty(c).lowercase()))
        assertEquals(c, G.normalize(G.link(c)))
        assertNull(G.normalize("ABC"))
        assertNull(G.normalize("ABCDEFGHJKMNPQ0O"))   // 0 και O δεν υπάρχουν στο αλφάβητο
        assertNull(G.normalize("ABCDEFGH"))           // παλιός 8ψήφιος: όχι πια
        assertEquals("ABCD-EFGH-JKMN-PQRS", G.pretty("ABCDEFGHJKMNPQRS"))
    }

    @Test fun routeRoundTripIsCompact() {
        val pts = (0 until 1000).map { app.motonav.geo.LatLon(38.0 + it * 1e-4, 23.0) }
        val s = G.encodeRoute(pts)
        val back = G.decodeRoute(s)
        assertTrue(back.size <= 121)
        assertEquals(pts.last().lat, back.last().lat, 1e-5)
        assertTrue(s.length <= 4000)
        assertEquals(0, G.decodeRoute(null).size)
    }

    @Test fun sendRate() {
        assertTrue(!G.shouldSend(2_000, 50f, false))
        assertTrue(G.shouldSend(3_000, 50f, false))
        assertTrue(!G.shouldSend(20_000, 0f, false))
        assertTrue(G.shouldSend(30_000, 0f, false))
        assertTrue(G.shouldSend(0, 0f, true))
    }
}

class VoxDetectorTest {
    @Test fun startsAfter80msAndEndsAfterSilence() {
        val d = app.motonav.group.VoxDetector(-30.0)
        val ev = ArrayList<app.motonav.group.VoxDetector.Event>()
        repeat(3) { ev += d.feed(-20.0) }
        assertTrue(ev.all { it == app.motonav.group.VoxDetector.Event.NONE })
        assertEquals(app.motonav.group.VoxDetector.Event.START, d.feed(-20.0))             // 4ο δυνατό κομμάτι
        repeat(59) { assertEquals(app.motonav.group.VoxDetector.Event.NONE, d.feed(-50.0)) }
        assertEquals(app.motonav.group.VoxDetector.Event.END, d.feed(-50.0))               // 60 ήσυχα = 1,2 s
    }

    @Test fun shortNoiseDoesNotStart() {
        val d = app.motonav.group.VoxDetector(-30.0)
        listOf(-20.0, -20.0, -50.0, -20.0, -20.0, -50.0).forEach { assertEquals(app.motonav.group.VoxDetector.Event.NONE, d.feed(it)) }
    }

    @Test fun maxLengthCutsAt15s() {
        val d = app.motonav.group.VoxDetector(-30.0)
        repeat(4) { d.feed(-10.0) }
        var ended = false
        repeat(800) { if (d.feed(-10.0) == app.motonav.group.VoxDetector.Event.END) ended = true }
        assertTrue(ended)
    }

    @Test fun levelsAndThresholds() {
        assertEquals(-100.0, app.motonav.group.VoxDetector.levelDb(ShortArray(10), 10), 0.01)
        val loud = ShortArray(320) { if (it % 2 == 0) 16384 else -16384 }
        assertEquals(-6.02, app.motonav.group.VoxDetector.levelDb(loud, 320), 0.1)
        assertTrue(app.motonav.group.VoxDetector.thresholdFor(1) > app.motonav.group.VoxDetector.thresholdFor(5))
    }
}

class GroupCryptoTest {
    private val C = app.motonav.group.GroupCrypto

    @Test fun roundTripAndWrongKey() {
        val a = C.derive("ABCDEFGHJKMNPQRS")
        val b = C.derive("ABCDEFGHJKMNPQRT")
        assertEquals(32, a.path.length)
        assertTrue(!a.path.contains("ABCD", ignoreCase = true))
        assertEquals(a.path, C.derive("ABCDEFGHJKMNPQRS").path)   // ίδιος κωδικός → ίδια παρέα
        assertTrue(a.path != b.path)
        val enc = C.encryptText(a, "Γιάννης 38.08,23.73")
        assertTrue(!enc.contains("Γιάννης"))
        assertTrue(enc != C.encryptText(a, "Γιάννης 38.08,23.73"))   // νέο IV κάθε φορά
        assertEquals("Γιάννης 38.08,23.73", C.decryptText(a, enc))
        assertNull(C.decryptText(b, enc))                              // άλλη παρέα: τίποτα
        assertNull(C.decryptText(a, enc.dropLast(4) + "AAAA"))         // πειραγμένο: τίποτα
    }
}

class OverpassTest {
    @Test fun retryOnlyOnServerTrouble() {
        val O = app.motonav.net.Overpass
        assertTrue(O.retryable(502))
        assertTrue(O.retryable(429))
        assertTrue(O.retryable(504))
        assertTrue(!O.retryable(400))
        assertTrue(O.SERVERS.size >= 3)
        assertEquals("Οι servers του χάρτη είναι φορτωμένοι. Δοκίμασε ξανά σε λίγο.", app.motonav.net.friendlyError(app.motonav.net.OverpassBusy(502)))
        assertEquals("Δεν υπάρχει internet αυτή τη στιγμή.", app.motonav.net.friendlyError(app.motonav.net.NoInternet()))
    }
}

class BatteryTest {
    @Test fun segmentsAndColours() {
        assertEquals(0, app.motonav.ui.batterySegments(0))
        assertEquals(1, app.motonav.ui.batterySegments(3))
        assertEquals(1, app.motonav.ui.batterySegments(20))
        assertEquals(2, app.motonav.ui.batterySegments(21))
        assertEquals(5, app.motonav.ui.batterySegments(100))
        assertTrue(app.motonav.ui.batteryColor(10) != app.motonav.ui.batteryColor(95))
    }
}

class PuckStretchTest {
    @Test fun stretch() {
        assertEquals(1.0f, app.motonav.ui.puckStretch(0.0), 1e-4f)
        assertEquals(1.556f, app.motonav.ui.puckStretch(50.0), 0.01f)
    }
}

class RoadKindTest {
    @Test fun fromWayTags() {
        val f = app.motonav.routing.BRouterJson::roadKindFrom
        assertEquals(app.motonav.routing.RoadKind.MOTORWAY, f("highway=motorway maxspeed=130"))
        assertEquals(app.motonav.routing.RoadKind.MOTORWAY, f("highway=motorway_link"))
        assertEquals(app.motonav.routing.RoadKind.MAIN, f("highway=trunk ref=EO1"))
        assertEquals(app.motonav.routing.RoadKind.MAIN, f("highway=primary"))
        assertEquals(app.motonav.routing.RoadKind.DIRT, f("highway=track tracktype=grade3"))
        assertEquals(app.motonav.routing.RoadKind.DIRT, f("highway=unclassified surface=gravel"))
        assertEquals(app.motonav.routing.RoadKind.OTHER, f("highway=residential surface=asphalt"))
    }
}

class TurnHoldTest {
    @Test fun turnStaysUntil20mPast() {
        // ευθεία 500 m βόρεια, στροφή δεξιά, 500 m ανατολικά
        val north = (0..50).map { app.motonav.geo.LatLon(38.0 + it * 0.00009, 23.0) }
        val east = (1..50).map { app.motonav.geo.LatLon(38.0045, 23.0 + it * 0.000114) }
        val r = app.motonav.routing.TrackRoute.build(north + east)!!
        val turn = r.maneuvers.first { it.turn != app.motonav.routing.Turn.ARRIVE }
        val e = app.motonav.nav.NavigationEngine(r)
        // πάνω στη στροφή (σταματημένος): ακόμα η ίδια στροφή, 0 m
        val at = e.update(app.motonav.geo.LatLon(38.0045, 23.00002), 5f)
        assertEquals(turn, at.next)
        // 10 m μετά: ακόμα η ίδια
        assertEquals(turn, e.update(app.motonav.geo.LatLon(38.0045, 23.000114), 5f).next)
        // 40 m μετά: η επόμενη (άφιξη)
        assertEquals(app.motonav.routing.Turn.ARRIVE, e.update(app.motonav.geo.LatLon(38.0045, 23.00046), 5f).next?.turn)
    }
}

class RouteTrafficTest {
    private fun line(): app.motonav.routing.Route {
        val pts = (0..10).map { app.motonav.geo.LatLon(38.0, 23.0 + it * 0.001) }
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + app.motonav.geo.Geo.distance(pts[i - 1], pts[i])
        return app.motonav.routing.Route(pts, pts.map { null }, cum, emptyList(), 100.0)
    }

    @Test
    fun parsesTrafficSectionsOnPolyline() {
        val pts = (0..10).joinToString(",") { """{"latitude":38.0,"longitude":${23.0 + it * 0.001}}""" }
        val json = """{"routes":[{"summary":{"travelTimeInSeconds":300,"trafficDelayInSeconds":60},
            "legs":[{"points":[$pts]}],
            "sections":[{"sectionType":"TRAVEL_MODE","startPointIndex":0,"endPointIndex":10},
              {"sectionType":"TRAFFIC","startPointIndex":2,"endPointIndex":5,"magnitudeOfDelay":2,"simpleCategory":"JAM"},
              {"sectionType":"TRAFFIC","startPointIndex":7,"endPointIndex":9,"magnitudeOfDelay":0,"simpleCategory":"ROAD_CLOSURE"}]}]}"""
        val eta = app.motonav.traffic.TomTomParser.eta(json)
        assertEquals(60.0, eta.delayS, 0.01)
        assertEquals(2, eta.jams.size)
        assertEquals(2, eta.jams[0].level)
        assertEquals(3, eta.jams[1].level)
        assertEquals(4, eta.jams[0].points.size)

        val r = line()
        val mapped = app.motonav.traffic.RouteTraffic.map(r, eta.jams)
        assertEquals(2, mapped.size)
        assertEquals(r.cumulative[2], mapped[0].fromM, 1.0)
        assertEquals(r.cumulative[5], mapped[0].toM, 1.0)
        assertEquals(r.cumulative[7], mapped[1].fromM, 1.0)
    }

    @Test
    fun sliceCutsBetweenMeters() {
        val r = line()
        val s = r.slice(r.cumulative[2] + 10, r.cumulative[4] - 10)
        assertEquals(3, s.size) // αρχή, σημείο 3, τέλος
        assertTrue(r.slice(50.0, 40.0).isEmpty())
    }

    @Test
    fun noTrafficSectionsMeansNoJams() {
        assertTrue(app.motonav.traffic.TomTomParser.eta("""{"routes":[{"summary":{"travelTimeInSeconds":1}}]}""").jams.isEmpty())
        assertEquals(0, app.motonav.traffic.TomTomParser.jamLevel(0, "ROAD_WORK"))
    }
}

class JamMergeTest {
    @Test
    fun closeJamsBecomeOne() {
        val m = app.motonav.traffic.RouteJam.merge(listOf(
            app.motonav.traffic.RouteJam(0.0, 100.0, 2),
            app.motonav.traffic.RouteJam(130.0, 200.0, 3),
            app.motonav.traffic.RouteJam(500.0, 600.0, 1),
        ))
        assertEquals(2, m.size)
        assertEquals(200.0, m[0].toM, 0.01)
        assertEquals(3, m[0].level)
    }
}

class TomTomRouteTrafficTest {
    @Test
    fun routeCarriesItsTrafficSections() {
        val pts = (0..10).joinToString(",") { """{"latitude":38.0,"longitude":${23.0 + it * 0.001}}""" }
        val json = """{"routes":[{"summary":{"travelTimeInSeconds":300,"trafficDelayInSeconds":40},
            "legs":[{"points":[$pts]}],
            "sections":[{"sectionType":"TRAFFIC","startPointIndex":3,"endPointIndex":6,"magnitudeOfDelay":3,"simpleCategory":"JAM"}]}]}"""
        val r = app.motonav.traffic.TomTomParser.route(json)
        assertEquals(1, r.trafficJams.size)
        assertEquals(3, r.trafficJams[0].level)
        assertEquals(r.cumulative[3], r.trafficJams[0].fromM, 0.01)
    }
}

class BRouterParamsTest {
    /** Το BRouter διαβάζει τις τιμές ως αριθμούς: «true» βγάζει ParseException. */
    @Test
    fun noBooleanWordsInExtraParams() {
        val src = java.io.File("src/main/java/app/motonav/routing/BRouterClient.kt").readText()
        assertTrue(!Regex("""[a-z_]+=(true|false)""").containsMatchIn(src))
    }
}

class SpotsTest {
    @Test
    fun classifiesBikerSpots() {
        val json = """{"elements":[
          {"type":"node","id":1,"lat":38.1,"lon":23.7,"tags":{"tourism":"viewpoint","name":"Θέα Πάρνηθας"}},
          {"type":"node","id":2,"lat":38.2,"lon":23.8,"tags":{"mountain_pass":"yes","ele":"1150"}},
          {"type":"node","id":3,"lat":38.3,"lon":23.9,"tags":{"natural":"spring"}},
          {"type":"way","id":4,"center":{"lat":38.0,"lon":23.6},"tags":{"shop":"motorcycle_repair","name":"Moto Service"}},
          {"type":"node","id":5,"lat":38.0,"lon":23.5,"tags":{"amenity":"cafe","biker":"yes","name":"Riders"}},
          {"type":"node","id":6,"lat":38.0,"lon":23.5,"tags":{"amenity":"bench"}}
        ]}"""
        val s = app.motonav.spots.Spots.parse(json)
        assertEquals(5, s.size)
        assertEquals(app.motonav.spots.SpotKind.VIEW, s[0].kind)
        assertEquals(app.motonav.spots.SpotKind.PASS, s[1].kind)
        assertTrue(s[1].detail.contains("1150 m"))
        assertEquals("Νερό", s[2].name)
        assertEquals(app.motonav.spots.SpotKind.MOTO_SHOP, s[3].kind)
        assertEquals(app.motonav.spots.SpotKind.BIKER_CAFE, s[4].kind)
    }

    @Test
    fun cellsAndCache() {
        val cells = app.motonav.spots.Spots.cells(38.05, 23.65, 38.15, 23.75)
        assertEquals(4, cells.size)
        val cache = app.motonav.spots.SpotCache()
        cache.add(cells.take(2), emptyList())
        assertEquals(2, cache.missing(cells, 0).size)
        cache.failed(cells.drop(2), 1000)
        assertEquals(0, cache.missing(cells, 2000).size)
        assertEquals(2, cache.missing(cells, 200_000).size)
        assertTrue(app.motonav.spots.Spots.query(38.0, 23.6, 38.2, 23.8).contains("[bbox:38.0000,23.6000,38.2000,23.8000]"))
        val back = app.motonav.spots.SpotCache.fromJson(app.motonav.spots.SpotCache.toJson(listOf(
            app.motonav.spots.Spot("node1", app.motonav.spots.SpotKind.WATER, "Βρύση", "Νερό", 38.1, 23.7))))
        assertEquals("Βρύση", back.single().name)
    }
}

class SpotPhotoTest {
    @Test
    fun picksNearestCommonsImageWithCredit() {
        val json = """{"query":{"pages":{
          "-2":{"index":2,"imageinfo":[{"thumburl":"https://up/b.jpg"}]},
          "-1":{"index":1,"imageinfo":[{"thumburl":"https://up/a.jpg","extmetadata":{"Artist":{"value":"<a href='x'>Νίκος</a>"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]},
          "-3":{"index":0,"imageinfo":[{"thumburl":"https://up/c.svg"}]}
        }}}"""
        val p = app.motonav.spots.SpotPhoto.parse(json, nearby = true)!!
        assertEquals("https://up/a.jpg", p.url)
        assertEquals("Νίκος · CC BY-SA 4.0 · Wikimedia Commons", p.credit)
    }

    @Test
    fun spotKeepsPhotoTag() {
        val s = app.motonav.spots.Spots.parse("""{"elements":[{"type":"node","id":9,"lat":38,"lon":23,"tags":{"natural":"spring","wikimedia_commons":"File:Vrysi.jpg"}}]}""")
        assertEquals("File:Vrysi.jpg", s.single().photo)
    }
}

class CategoryTomTomTest {
    @Test
    fun fuelUsesTomTomPetrolCategory() {
        assertEquals("7311", app.motonav.search.Category.FUEL.tomtom)
        assertEquals(null, app.motonav.search.Category.WATER.tomtom)
        val json = """{"results":[{"type":"POI","poi":{"name":"Shell"},"position":{"lat":38.1,"lon":23.7},"address":{"streetName":"Λ. Πάρνηθος","municipality":"Αχαρνές"}}]}"""
        assertEquals("Shell", app.motonav.search.PlaceParsers.tomtom(json).single().name)
    }
}
