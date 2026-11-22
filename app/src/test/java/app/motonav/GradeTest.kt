package app.motonav

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.routing.Route
import app.motonav.sensors.LeanMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeTest {

    /** ~1 km ευθεία, ανεβαίνει 50 m στα πρώτα 500 m (10%), μετά επίπεδη. Κάποια σημεία χωρίς υψόμετρο. */
    private fun hill(): Route {
        val pts = (0..100).map { LatLon(39.0, 22.0 + it * 0.000115) } // ~10 m ανά σημείο
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Geo.distance(pts[i - 1], pts[i])
        val ele = pts.indices.map { i ->
            if (i % 7 == 3) null else if (cum[i] <= 500) 100.0 + cum[i] * 0.10 else 150.0
        }
        return Route(pts, ele, cum, emptyList(), 100.0)
    }

    @Test
    fun routeGradeUphillAndFlat() {
        val r = hill()
        assertEquals(10.0, r.gradeAt(200.0)!!, 0.6)
        assertEquals(0.0, r.gradeAt(800.0)!!, 0.6)
    }

    @Test
    fun elevationInterpolatesOverMissingPoints() {
        val r = hill()
        val i = 3 // χωρίς υψόμετρο
        assertEquals(100.0 + r.cumulative[i] * 0.10, r.elevationAt(r.cumulative[i])!!, 0.2)
    }

    @Test
    fun noElevationNoGrade() {
        val r = hill().let { Route(it.points, it.points.map { null }, it.cumulative, emptyList(), 1.0) }
        assertNull(r.gradeAt(300.0))
    }

    @Test
    fun sensorGradeNeedsDistance() {
        assertNull(LeanMath.gradePct(listOf(0.0 to 100.0, 30.0 to 103.0)))
        assertEquals(-6.0, LeanMath.gradePct(listOf(0.0 to 100.0, 50.0 to 97.0, 100.0 to 94.0))!!, 1e-9)
    }
}
