package app.motonav

import app.motonav.sensors.LeanMath
import org.junit.Assert.assertEquals
import org.junit.Test

class GMeterTest {
    @Test fun lateralFromLean() {
        assertEquals(0.0, LeanMath.lateralG(0.0), 1e-9)
        assertEquals(1.0, LeanMath.lateralG(45.0), 1e-9)
        assertEquals(-1.0, LeanMath.lateralG(-45.0), 1e-9)
    }

    @Test fun crossTimeInterpolates() {
        // 90 → 110 km/h σε 1″: τα 100 στη μέση.
        assertEquals(500L, LeanMath.crossTime(0, 90 / 3.6, 1000, 110 / 3.6, 100 / 3.6))
        assertEquals(1000L, LeanMath.crossTime(0, 30.0, 1000, 30.0, 27.0))
    }
}
