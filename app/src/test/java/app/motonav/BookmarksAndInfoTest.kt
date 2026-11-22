package app.motonav

import app.motonav.data.BookmarkFiles
import app.motonav.geo.LatLon
import app.motonav.search.Place
import app.motonav.search.PlaceInfoLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksAndInfoTest {
    @Test
    fun gpxRoundTrip() {
        val list = listOf(
            Place("Καφέ <Μηλιές>", "Πήλιο", 39.33, 23.15),
            Place("Βενζίνη & φαγητό", "", 38.5, 22.1),
        )
        val gpx = BookmarkFiles.toGpx("Βόλτες Πήλιο", list)
        val back = BookmarkFiles.parse(gpx)
        assertEquals(2, back.size)
        assertEquals("Καφέ <Μηλιές>", back[0].name)
        assertEquals("Βενζίνη & φαγητό", back[1].name)
        assertEquals(39.33, back[0].lat, 1e-6)
        assertEquals("Βόλτες Πήλιο", BookmarkFiles.title(gpx))
    }

    @Test
    fun kmlPlacemarks() {
        val kml = """<?xml version="1.0"?><kml><Document><name>Ταξίδι</name>
            <Placemark><name>Κορυφή</name><Point><coordinates>22.35,40.08,0</coordinates></Point></Placemark>
            <Placemark><name>Γραμμή</name><LineString><coordinates>1,2 3,4</coordinates></LineString></Placemark>
            </Document></kml>"""
        val p = BookmarkFiles.parse(kml)
        assertEquals(1, p.size)
        assertEquals("Κορυφή", p[0].name)
        assertEquals(40.08, p[0].lat, 1e-6)
        assertEquals(22.35, p[0].lon, 1e-6)
        assertEquals("Ταξίδι", BookmarkFiles.title(kml))
    }

    @Test
    fun openingHoursInGreek() {
        assertEquals("Δε-Πα 08:00-16:00 · Σα 09:00-13:00", PlaceInfoLookup.hoursGreek("Mo-Fr 08:00-16:00; Sa 09:00-13:00"))
        assertEquals("Όλο το 24ωρο", PlaceInfoLookup.hoursGreek("24/7"))
    }

    @Test
    fun placeInfoPicksMatchingName() {
        val json = """{"elements":[
            {"type":"node","lat":38.0001,"lon":23.0001,"tags":{"name":"Άλλο μαγαζί","phone":"210 0000000"}},
            {"type":"way","center":{"lat":38.0002,"lon":23.0002},"tags":{"name":"Shell","opening_hours":"24/7","phone":"+30 210 1234567;+30 210 7654321","website":"shell.gr"}}
        ]}"""
        val info = PlaceInfoLookup.parse(json, LatLon(38.0, 23.0), "Shell")!!
        assertEquals("Όλο το 24ωρο", info.hours)
        assertEquals("+30 210 1234567", info.phone)
        assertEquals("shell.gr", info.website)
        assertNull(PlaceInfoLookup.parse(json, LatLon(38.0, 23.0), "Ταβέρνα Ο Νίκος"))
        assertTrue(Place("x", "", 0.0, 0.0).listName == Place.DEFAULT_LIST)
    }
}
