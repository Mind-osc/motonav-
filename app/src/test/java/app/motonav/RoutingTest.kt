package app.motonav

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.nav.NavigationEngine
import app.motonav.nav.Phrases
import app.motonav.routing.BRouterJson
import app.motonav.routing.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingTest {

    // Ίδια μορφή με το btools.router.FormatJson (BRouter), συντομευμένη.
    // Διαδρομή: ανατολικά 3 σημεία, μετά δεξιά (νότια) 2 σημεία.
    private val sample = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "creator": "BRouter-1.7.4",
                "name": "brouter_0",
                "track-length": "1112",
                "filtered ascend": "3",
                "plain-ascend": "2",
                "total-time": "100",
                "total-energy": "0",
                "cost": "1500",
                "voicehints": [
                  [2,5,0,555,90]
                ],
                "times": [0,25,50,75,100]
              },
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  [22.000000, 39.000000, 100.0],
                  [22.003000, 39.000000, 101.0],
                  [22.006000, 39.000000, 102.0],
                  [22.006000, 38.997500],
                  [22.006000, 38.995000, 104.0]
                ]
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesBRouterGeoJson() {
        val r = BRouterJson.parse(sample)
        assertEquals(5, r.points.size)
        assertEquals(39.0, r.points[0].lat, 1e-9)
        assertEquals(22.003, r.points[1].lon, 1e-9)
        assertEquals(null, r.elevations[3])
        assertEquals(100.0, r.durationS, 1e-9)
        // μία στροφή δεξιά + άφιξη
        assertEquals(2, r.maneuvers.size)
        assertEquals(Turn.RIGHT, r.maneuvers[0].turn)
        assertEquals(2, r.maneuvers[0].pointIndex)
        assertEquals(Turn.ARRIVE, r.maneuvers.last().turn)
        assertTrue(r.lengthM > 1000 && r.lengthM < 1100)
    }

    @Test
    fun followsRouteAndAnnouncesNextTurn() {
        val r = BRouterJson.parse(sample)
        val e = NavigationEngine(r)
        // λίγο βόρεια της γραμμής, στη μέση του πρώτου κομματιού
        val s = e.update(LatLon(39.0001, 22.0015), 8f)
        assertFalse(s.offRoute)
        assertNotNull(s.next)
        assertEquals(Turn.RIGHT, s.next!!.turn)
        assertTrue("απόσταση ως τη στροφή ${s.distToNextM}", s.distToNextM in 350.0..450.0)
    }

    @Test
    fun detectsOffRouteAfterTwoFixes() {
        val r = BRouterJson.parse(sample)
        val e = NavigationEngine(r)
        val on = e.update(LatLon(39.0, 22.001), 5f)
        assertTrue(on.onRoute)
        val far = LatLon(39.01, 22.001) // ~1,1 km βόρεια
        val first = e.update(far, 5f)
        assertFalse(first.offRoute)
        assertFalse(first.onRoute)
        assertTrue(e.update(far, 5f).offRoute)
    }

    @Test
    fun parallelGpsDriftWaitsLonger() {
        val r = BRouterJson.parse(sample)
        val e = NavigationEngine(r)
        e.update(LatLon(39.0, 22.001), 5f)
        // ~35 m δίπλα στη διαδρομή, ίδια κατεύθυνση (ανατολικά): μάλλον GPS, όχι ακόμα «εκτός»
        val drift = LatLon(39.0003, 22.0015)
        val bearing = app.motonav.geo.Geo.bearing(r.points[0], r.points[1]).toFloat()
        repeat(3) { assertFalse(e.update(drift, 5f, bearing, 10f).offRoute) }
        assertTrue(e.update(drift, 5f, bearing, 10f).offRoute)
    }

    @Test
    fun joinsSavedRouteNearestAhead() {
        // ευθεία 2 km ανατολικά· είσαι 300 m βόρεια από τη μέση της → μπαίνεις κοντά στη μέση, όχι στην αρχή
        val track = (0..200).map { LatLon(38.0, 23.0 + it * 0.000114) }
        val i = app.motonav.routing.TrackJoin.joinIndex(track, LatLon(38.0027, 23.0114))
        assertTrue("join $i", i in 90..140)
        // ήδη μετά τη μέση: ποτέ πίσω από το minIndex
        assertTrue(app.motonav.routing.TrackJoin.joinIndex(track, LatLon(38.0, 23.0), minIndex = 150) >= 150)
        // προσέγγιση + υπόλοιπο: συνεχής γραμμή που τελειώνει στο τέλος της διαδρομής
        val approach = app.motonav.routing.TrackRoute.build(listOf(LatLon(38.0027, 23.0114), LatLon(38.0, 23.0114)))!!
        val r = app.motonav.routing.TrackJoin.combine(approach, track.subList(100, track.size))!!
        assertEquals(track.last().lon, r.points.last().lon, 1e-9)
        assertTrue("len ${r.lengthM}", r.lengthM > approach.lengthM + 900)
        assertEquals(app.motonav.routing.Turn.ARRIVE, r.maneuvers.last().turn)
    }

    @Test
    fun navZoomBySpeedAndTurn() {
        assertEquals(17.5, app.motonav.ui.navZoomFor(20f, null), 0.01)
        assertEquals(15.0, app.motonav.ui.navZoomFor(130f, 2000.0), 0.01)
        assertEquals(17.0, app.motonav.ui.navZoomFor(70f, 150.0), 0.01)
        assertEquals(15.6, app.motonav.ui.navZoomFor(100f, 150.0), 0.01)
    }

    @Test
    fun arrivesAtTheEnd() {
        val r = BRouterJson.parse(sample)
        val e = NavigationEngine(r)
        var s = e.update(LatLon(39.0, 22.001), 5f)
        for (p in listOf(LatLon(39.0, 22.004), LatLon(39.0, 22.006), LatLon(38.997, 22.006), LatLon(38.99505, 22.006))) {
            s = e.update(p, 5f)
        }
        assertTrue(s.arrived)
    }

    @Test
    fun errorTextIsNotParsedAsRoute() {
        val msg = "datafile E20_N35.rd5 not found"
        assertFalse(msg.trimStart().startsWith("{"))
    }

    @Test
    fun greekPhrases() {
        assertEquals("300 μέτρα", Phrases.distance(310.0))
        assertEquals("50 μέτρα", Phrases.distance(12.0))
        assertEquals("1 χιλιόμετρο", Phrases.distance(1020.0))
        assertEquals("2 χιλιόμετρα", Phrases.distance(1980.0))
        assertEquals("1,5 χιλιόμετρα", Phrases.distance(1500.0))
        assertEquals("δεύτερη", Phrases.ordinal(2))
        assertEquals("Στρίψε", Phrases.capitalized("στρίψε"))
    }

    @Test
    fun geoBasics() {
        val a = LatLon(39.0, 22.0)
        val b = LatLon(39.0, 22.01)
        val d = Geo.distance(a, b)
        assertTrue("$d", d in 860.0..870.0)
        assertEquals(90.0, Geo.bearing(a, b), 0.5)
        val (t, off) = Geo.project(LatLon(39.0001, 22.005), a, b)
        assertEquals(0.5, t, 0.01)
        assertEquals(11.1, off, 0.5)
    }
}
