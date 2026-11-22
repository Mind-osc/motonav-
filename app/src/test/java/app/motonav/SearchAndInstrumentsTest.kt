package app.motonav

import app.motonav.geo.LatLon
import app.motonav.search.PlaceParsers
import app.motonav.sensors.LeanMath
import app.motonav.ui.cardinal
import app.motonav.ui.leanLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndInstrumentsTest {

    // Μορφή απάντησης Photon (GeoJSON).
    private val photon = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[21.6303,39.7056]},
           "properties":{"osm_value":"town","name":"Καλαμπάκα","county":"Περιφερειακή Ενότητα Τρικάλων","state":"Θεσσαλία","country":"Ελλάδα","countrycode":"GR"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[23.7275,37.9838]},
           "properties":{"osm_value":"house","street":"Πατησίων","housenumber":"42","city":"Αθήνα","postcode":"10682","country":"Ελλάδα","countrycode":"GR"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[12.49,41.89]},
           "properties":{"osm_value":"city","name":"Roma","state":"Lazio","country":"Italia","countrycode":"IT"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]},"properties":{"name":null}}
        ]}
    """.trimIndent()

    // Μορφή απάντησης Overpass (out center).
    private val overpass = """
        {"version":0.6,"elements":[
          {"type":"node","id":1,"lat":39.70,"lon":21.64,"tags":{"amenity":"fuel","brand":"Shell","opening_hours":"24/7"}},
          {"type":"way","id":2,"center":{"lat":39.72,"lon":21.63},"tags":{"amenity":"fuel","name":"Πρατήριο Γιώργου","addr:street":"Τρικάλων","addr:housenumber":"5"}},
          {"type":"node","id":3,"lat":39.9,"lon":21.9,"tags":{"amenity":"fuel"}}
        ]}
    """.trimIndent()

    @Test
    fun parsesPhoton() {
        val p = PlaceParsers.photon(photon)
        assertEquals(3, p.size)
        assertEquals("Καλαμπάκα", p[0].name)
        assertEquals(39.7056, p[0].lat, 1e-9)
        assertEquals(21.6303, p[0].lon, 1e-9)
        assertTrue(p[0].detail, p[0].detail.contains("Τρικάλων"))
        assertEquals("Πατησίων 42", p[1].name)
        assertTrue(p[1].detail, p[1].detail.contains("Αθήνα"))
        assertTrue("ξένη χώρα στο detail", p[2].detail.contains("Italia"))
        assertTrue("Ελλάδα δεν γράφεται", !p[0].detail.contains("Ελλάδα"))
    }

    @Test
    fun parsesOverpassAndSortsByDistance() {
        val here = LatLon(39.705, 21.635)
        val p = PlaceParsers.overpass(overpass, here, "Βενζινάδικο")
        assertEquals(3, p.size)
        assertEquals("Shell", p[0].name)
        assertTrue(p[0].detail, p[0].detail.contains("24/7"))
        assertEquals("Πρατήριο Γιώργου", p[1].name)
        assertTrue(p[1].detail, p[1].detail.contains("Τρικάλων 5"))
        assertEquals("Βενζινάδικο", p[2].name)
    }

    @Test
    fun leanFromSpeedAndTurnRate() {
        // 20 m/s (72 km/h), στροφή αριστερά 0,49 rad/s → tan φ = 1 → 45° αριστερά
        assertEquals(-45.0, LeanMath.leanDeg(20.0, 0.49033), 0.1)
        // δεξιά στροφή
        assertTrue(LeanMath.leanDeg(15.0, -0.3) > 20)
        // σταματημένος: 0
        assertEquals(0.0, LeanMath.leanDeg(1.0, 1.0), 0.0)
    }

    @Test
    fun headingAndLabels() {
        assertEquals(0.0, LeanMath.headingDeg(0.0, 1.0), 1e-6)
        assertEquals(90.0, LeanMath.headingDeg(1.0, 0.0), 1e-6)
        assertEquals(270.0, LeanMath.headingDeg(-1.0, 0.0), 1e-6)
        assertEquals("Β", cardinal(359f))
        assertEquals("ΒΑ", cardinal(44f))
        assertEquals("Ν", cardinal(181f))
        assertEquals("ΒΔ", cardinal(-45f))
        assertEquals("24° Α", leanLabel(-24.2f))
        assertEquals("31° Δ", leanLabel(30.6f))
        assertEquals("0°", leanLabel(0.3f))
    }
}
