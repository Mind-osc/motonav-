package app.motonav

import app.motonav.cameras.CameraAlerts
import app.motonav.cameras.CameraParser
import app.motonav.cameras.SpeedCamera
import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.routing.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamerasTest {

    private val here = LatLon(38.0, 23.7)
    // ~0,00009° γεωγρ. πλάτους ≈ 10 m
    private fun north(m: Double) = LatLon(here.lat + m / 111_195.0, here.lon)

    @Test
    fun parsesOverpass() {
        val json = """
            {"elements":[
              {"type":"node","id":1,"lat":38.001,"lon":23.7,"tags":{"highway":"speed_camera","maxspeed":"50","direction":"NE"}},
              {"type":"node","id":2,"lat":38.002,"lon":23.7,"tags":{"enforcement":"maxspeed","maxspeed":"30 mph","direction":"270"}},
              {"type":"relation","id":3,"center":{"lat":38.003,"lon":23.7},"tags":{"type":"enforcement","enforcement":"average_speed"}}
            ]}
        """.trimIndent()
        val c = CameraParser.overpass(json)
        assertEquals(3, c.size)
        assertEquals(50, c[0].maxspeed)
        assertEquals(45.0, c[0].directionDeg!!, 1e-9)
        assertEquals(48, c[1].maxspeed)
        assertEquals(270.0, c[1].directionDeg!!, 1e-9)
        assertNull(c[2].maxspeed)
        assertEquals(38.003, c[2].lat, 1e-9)
    }

    @Test
    fun jsonRoundTrip() {
        val list = listOf(SpeedCamera("a", 1.0, 2.0, 50, 90.0, "mine"), SpeedCamera("b", 3.0, 4.0))
        assertEquals(list, CameraParser.fromJson(CameraParser.toJson(list)))
    }

    @Test
    fun freeModeOnlyAheadInYourDirection() {
        val ahead = SpeedCamera("ahead", north(250.0).lat, here.lon)
        val behind = SpeedCamera("behind", north(-150.0).lat, here.lon)
        val far = SpeedCamera("far", north(600.0).lat, here.lon)
        val opposite = SpeedCamera("opp", north(120.0).lat, here.lon, directionDeg = 180.0)
        val a = CameraAlerts.ahead(here, headingDeg = 0.0, cams = listOf(behind, far, opposite, ahead))
        assertNotNull(a)
        assertEquals("ahead", a!!.first.id)
        assertEquals(250.0, a.second, 3.0)
        // πας νότια: μπροστά σου είναι το «behind»
        assertEquals("behind", CameraAlerts.ahead(here, 180.0, listOf(behind, ahead))!!.first.id)
        // χωρίς κατεύθυνση: τίποτα
        assertNull(CameraAlerts.ahead(here, null, listOf(ahead)))
    }

    @Test
    fun routeModeUsesDistanceAlongRoute() {
        val pts = (0..60).map { north(it * 10.0) } // 600 m βόρεια
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Geo.distance(pts[i - 1], pts[i])
        val r = Route(pts, pts.map { null }, cum, emptyList(), 60.0)
        val onRoute = SpeedCamera("on", north(400.0).lat, here.lon + 0.0002) // ~17 m δίπλα στη γραμμή
        val offRoute = SpeedCamera("off", north(300.0).lat, here.lon + 0.002) // ~175 m μακριά
        val a = CameraAlerts.ahead(here, 0.0, listOf(offRoute, onRoute), route = r, progressM = 150.0)
        assertEquals("on", a!!.first.id)
        assertEquals(250.0, a.second, 3.0)
        // περάσαμε την κάμερα
        assertNull(CameraAlerts.ahead(north(420.0), 0.0, listOf(onRoute), route = r, progressM = 420.0))
    }

    @Test
    fun parsers() {
        assertEquals(30.0, CameraParser.direction("30")!!, 1e-9)
        assertEquals(225.0, CameraParser.direction("sw")!!, 1e-9)
        assertNull(CameraParser.direction("forward"))
        assertEquals(80, CameraParser.maxspeed("80"))
        assertNull(CameraParser.maxspeed("none"))
        assertTrue(CameraAlerts.angleDiff(350.0, 10.0) in 19.9..20.1)
    }
}
