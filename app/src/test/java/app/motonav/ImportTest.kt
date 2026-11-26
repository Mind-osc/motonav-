package app.motonav

import app.motonav.data.ImportedPois
import app.motonav.search.OfflineCross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImportTest {
    @Test fun garminCsvIsLonLat() {
        val pts = ImportedPois.parseCsv("23.72000,37.98000,\"Shell Πατησίων\",\"24ωρο\"\n23.80,38.01,Aegean,\n")
        assertEquals(2, pts.size)
        assertEquals(37.98, pts[0].lat, 1e-6)
        assertEquals(23.72, pts[0].lon, 1e-6)
        assertEquals("Shell Πατησίων", pts[0].name)
    }

    @Test fun csvWithHeaderLatLon() {
        val pts = ImportedPois.parseCsv("name;lat;lon\nΚαφέ;37,95;23,70\n")
        assertEquals(1, pts.size)
        assertEquals(37.95, pts[0].lat, 1e-6)
        assertEquals("Καφέ", pts[0].name)
    }

    @Test fun csvLatFirstWithoutHeader() {
        val pts = ImportedPois.parseCsv("37.98,23.72,Σημείο")
        assertEquals(37.98, pts[0].lat, 1e-6)
    }

    @Test fun ov2Record() {
        val name = "Κάμερα>Λιοσίων".toByteArray(Charsets.UTF_8)
        val len = 13 + name.size + 1
        val bb = ByteBuffer.allocate(21 + len).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(1); bb.putInt(0); bb.putInt(0); bb.putInt(0); bb.putInt(0); bb.putInt(0) // skipper
        bb.put(2); bb.putInt(len); bb.putInt(2372000); bb.putInt(3798000); bb.put(name); bb.put(0)
        val pts = ImportedPois.parseOv2(bb.array())
        assertEquals(1, pts.size)
        assertEquals(37.98, pts[0].lat, 1e-6)
        assertEquals("Κάμερα", pts[0].name)
        assertEquals("Λιοσίων", pts[0].detail)
    }

    @Test fun crossingSplit() {
        assertEquals("Λιοσίων" to "Πραΐδου", OfflineCross.split("Λιοσίων & Πραΐδου"))
        assertEquals("Λιοσίων" to "Πραΐδου", OfflineCross.split("Λιοσίων και Πραΐδου"))
        assertEquals("Λιοσίων" to "Πραΐδου", OfflineCross.split("Λιοσίων/Πραΐδου"))
        assertNull(OfflineCross.split("Λιοσίων 12/14"))
        assertNull(OfflineCross.split("Λιοσίων 120"))
    }
}

class TripMeterTest {
    @Test fun totalsAndStops() {
        val m = app.motonav.trips.TripMeterData(startMs = 1_000L, distanceM = 30_000.0, movingS = 1800.0)
        assertEquals(3600.0, m.totalS(3_601_000L), 1e-6)
        assertEquals(1800.0, m.stoppedS(3_601_000L), 1e-6)
        assertEquals(60.0, m.avgMovingKmh, 1e-6)
        assertEquals(30.0, m.avgTotalKmh(3_601_000L), 1e-6)
        val back = app.motonav.trips.TripMeterData.fromJson(m.toJson())
        assertEquals(m, back)
    }
}

class FourGpsTest {
    @Test fun garminGpxWithAddressAndPhone() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="POIs 4 GPS http://www.4gps.gr"><wpt lat="38.89420" lon="22.44553"><name>BP - ΓΚΙΚΑΣ ΓΕΩΡΓΙΟΣ - Φθιώτιδος-Λαμία</name><extensions><gpxx:WaypointExtension xmlns:gpxx="http://www.garmin.com/xmlschemas/GpxExtensions/v3"><gpxx:Address><gpxx:StreetAddress>Θερμοπυλών 87</gpxx:StreetAddress><gpxx:City>Λαμία</gpxx:City></gpxx:Address><gpxx:PhoneNumber>2231012345</gpxx:PhoneNumber></gpxx:WaypointExtension></extensions></wpt><wpt lat="37.97877" lon="23.91553"><name>BP - Σπάτα</name><cmt>Κιν: 6973996019 - </cmt></wpt></gpx>"""
        val pts = ImportedPois.parseGpx(gpx)
        assertEquals(2, pts.size)
        assertEquals("BP - ΓΚΙΚΑΣ ΓΕΩΡΓΙΟΣ - Φθιώτιδος-Λαμία", pts[0].name)
        assertEquals("Θερμοπυλών 87, Λαμία · ☎ 2231012345", pts[0].detail)
        assertEquals("☎ 6973996019", pts[1].detail)
    }
}

class FourGpsCamerasTest {
    @Test fun cameraComment() {
        val gpx = """<gpx><wpt lat="38.71560" lon="21.20750"><name>Κάμερα Ταχύτητας - G0118</name><cmt>50,1,345</cmt><extensions><gpxx:StreetAddress>Ε.Ο. Αγρινίου-Άρτας</gpxx:StreetAddress></extensions></wpt><wpt lat="38.6" lon="21.4"><name>Κάμερα</name><cmt>0,0,0</cmt></wpt></gpx>"""
        val pts = ImportedPois.parseGpx(gpx)
        assertEquals(50, pts[0].speed)
        assertEquals(345.0, pts[0].dir!!, 1e-6)
        assertEquals("όριο 50 · Ε.Ο. Αγρινίου-Άρτας", pts[0].detail)
        assertNull(pts[1].speed)
        assertNull(pts[1].dir)
    }
}

class FourGpsExtrasTest {
    @Test fun tollPricesAndHours() {
        val gpx = """<gpx><wpt lat="38.5" lon="21.3"><name>Σταθμός Διοδίων Αγγελόκαστρου</name><cmt>Site: www.naodos.gr - 1.25-2.55-6.30-10.10,1,96</cmt></wpt><wpt lat="37.4" lon="23.2"><name>Συνεργείο</name><cmt>Ωράριο: Δευτέρα - Παρασκευή: 08:00-18:00.</cmt></wpt></gpx>"""
        val pts = ImportedPois.parseGpx(gpx)
        assertEquals("Διόδια: μηχανή 1,25 € · ΙΧ 2,55 €", pts[0].detail)
        assertNull(pts[0].dir)
        assertEquals("Ωράριο: Δευτέρα - Παρασκευή: 08:00-18:00.", pts[1].detail)
    }
}
