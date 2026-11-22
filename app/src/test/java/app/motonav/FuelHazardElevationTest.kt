package app.motonav

import app.motonav.fuel.FuelOnRoute
import app.motonav.fuel.FuelStation
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.hazards.Hazard
import app.motonav.hazards.HazardKind
import app.motonav.hazards.Hazards
import app.motonav.routing.ElevationProfile
import app.motonav.routing.OnRoute
import app.motonav.routing.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelHazardElevationTest {
    private val here = LatLon(38.0, 23.7)
    private fun north(m: Double, eastDeg: Double = 0.0) = LatLon(here.lat + m / 111_195.0, here.lon + eastDeg)

    private fun line(lengthM: Double, stepM: Double = 10.0, elev: ((Double) -> Double?)? = null): Route {
        val n = (lengthM / stepM).toInt()
        val pts = (0..n).map { north(it * stepM) }
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Geo.distance(pts[i - 1], pts[i])
        return Route(pts, cum.map { m -> elev?.invoke(m) }, cum, emptyList(), lengthM / 20.0)
    }

    @Test
    fun projectsOntoRoute() {
        val r = line(1000.0)
        val p = OnRoute.project(r, north(400.0, 0.0005))!! // ~44 m ανατολικά
        assertEquals(400.0, p.alongM, 3.0)
        assertEquals(44.0, p.offM, 4.0)
    }

    @Test
    fun fuelAlongRouteSortedAndFiltered() {
        val r = line(20_000.0, stepM = 50.0)
        val stations = listOf(
            FuelStation("b", "BP", north(12_000.0, 0.002).lat, north(12_000.0, 0.002).lon),     // ~175 m
            FuelStation("a", "Shell", north(3_000.0, 0.001).lat, north(3_000.0, 0.001).lon),   // ~88 m
            FuelStation("a2", "Shell", north(3_010.0, 0.0012).lat, north(3_010.0, 0.0012).lon), // ίδιο
            FuelStation("far", "EKO", north(8_000.0, 0.02).lat, north(8_000.0, 0.02).lon),     // ~1,7 km
        )
        val on = FuelOnRoute.along(r, stations)
        assertEquals(listOf("a", "b"), on.map { it.station.id })
        assertEquals(3_000.0, on[0].alongM, 20.0)
        // μπροστά σου από τα 5 km: μόνο το BP
        assertEquals(listOf("b"), FuelOnRoute.ahead(on, 5_000.0).map { it.station.id })
        // μεγαλύτερο κενό: από 12 km ως το τέλος (8 km) ή 3→12 (9 km)
        assertEquals(9_000.0, FuelOnRoute.longestGap(on, r.lengthM), 50.0)
    }

    @Test
    fun parsesOverpassFuel() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":38.0,"lon":23.7,"tags":{"amenity":"fuel","brand":"Shell","opening_hours":"24/7"}},
            {"type":"way","id":2,"center":{"lat":38.1,"lon":23.8},"tags":{"amenity":"fuel","name":"Πρατήριο Νίκος"}},
            {"type":"node","id":3,"lat":38.2,"lon":23.9,"tags":{"amenity":"fuel"}}
        ]}"""
        val s = FuelOnRoute.parse(json)
        assertEquals(listOf("Shell", "Πρατήριο Νίκος", "Βενζινάδικο"), s.map { it.name })
        assertEquals("24/7", s[0].hours)
    }

    @Test
    fun parsesTomTomAlongRoute() {
        val json = """{"results":[
            {"id":"g1","position":{"lat":37.95,"lon":23.70},"poi":{"name":"Aegean Καλλιθέα","brands":[{"name":"Aegean"}]}},
            {"id":"g2","position":{"lat":37.96,"lon":23.71},"poi":{"name":"Πρατήριο Νίκος"}}
        ]}"""
        val s = FuelOnRoute.tomtomParse(json)
        assertEquals(listOf("Aegean", "Πρατήριο Νίκος"), s.map { it.name })
        assertEquals("tt-g1", s[0].id)
    }

    @Test
    fun queryContainsLine() {
        val q = FuelOnRoute.query(line(5_000.0))
        assertTrue(q.contains("\"amenity\"=\"fuel\""))
        // 5 km → ένα κουτάκι γύρω από τη γραμμή
        assertEquals(1, FuelOnRoute.boxes(line(5_000.0)).size)
        assertTrue(q.contains("(37.9910,23.6886,38.0540,23.7114)"))
        // 60 km → 3 κουτάκια
        assertEquals(3, FuelOnRoute.boxes(line(60_000.0, stepM = 100.0)).size)
    }

    @Test
    fun elevationAscentWithHysteresis() {
        // ανεβαίνει 0→100 m στα πρώτα 1000 m, κατεβαίνει 100→40 ως τα 2000 m, με θόρυβο ±1 m
        val r = line(2_000.0, elev = { m ->
            val base = if (m <= 1000) m / 10.0 else 100 - (m - 1000) * 0.06
            base + if ((m / 10).toInt() % 2 == 0) 1.0 else -1.0
        })
        val p = ElevationProfile.of(r)!!
        assertEquals(100.0, p.ascentM, 6.0)
        assertEquals(60.0, p.descentM, 6.0)
        assertEquals(0.0, p.minM, 2.0)
        assertEquals(100.0, p.maxM, 2.0)
        assertEquals(0.0, p.ascentFrom(1100.0), 3.0)
    }

    @Test
    fun noElevationNoProfile() {
        assertNull(ElevationProfile.of(line(2_000.0)))
    }

    @Test
    fun hazardAheadOnRouteAndExpiry() {
        val r = line(2_000.0)
        val now = 1_000_000_000L
        val h = Hazard("h1", HazardKind.SLIPPERY, north(700.0, 0.0002).lat, north(700.0, 0.0002).lon, now)
        val a = Hazards.ahead(north(300.0), 0.0, listOf(h), route = r, progressM = 300.0)
        assertNotNull(a)
        assertEquals(400.0, a!!.second, 5.0)
        // πέρα από τα 500 m: όχι ακόμα
        assertNull(Hazards.ahead(north(100.0), 0.0, listOf(h), route = r, progressM = 100.0))
        // το πέρασες
        assertNull(Hazards.ahead(north(800.0), 0.0, listOf(h), route = r, progressM = 800.0))
        // χωρίς διαδρομή, προς τα πίσω δεν ειδοποιεί
        assertNull(Hazards.ahead(north(900.0), 0.0, listOf(h)))
        assertNotNull(Hazards.ahead(north(400.0), 0.0, listOf(h)))
        // λήξη: λάδια 24 ώρες
        assertTrue(h.alive(now + 23 * 3_600_000L))
        assertTrue(!h.alive(now + 25 * 3_600_000L))
    }

    @Test
    fun hazardJsonRoundTrip() {
        val list = listOf(Hazard("x", HazardKind.ANIMAL, 38.1, 23.2, 5L, 90.0), Hazard("y", HazardKind.WORKS, 38.2, 23.3, 6L))
        val back = Hazards.fromJson(Hazards.toJson(list))
        assertEquals(list, back)
    }
}
