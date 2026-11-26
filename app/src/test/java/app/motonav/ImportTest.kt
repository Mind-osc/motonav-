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
