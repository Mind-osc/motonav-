package app.motonav

import app.motonav.geo.LatLon
import app.motonav.search.Place
import app.motonav.search.PlaceParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchTest {
    @Test
    fun abbreviations() {
        assertEquals("Λεωφόρος Συγγρού 150", PlaceParsers.expandAbbreviations("Λ. Συγγρού 150"))
        assertEquals("Λεωφόρος Συγγρού", PlaceParsers.expandAbbreviations("λεωφ Συγγρού"))
        assertEquals("Πλατεία Συντάγματος", PlaceParsers.expandAbbreviations("Πλ. Συντάγματος"))
        assertEquals("Λαμία", PlaceParsers.expandAbbreviations("Λαμία"))
        assertEquals("Οδός Πειραιώς", PlaceParsers.expandAbbreviations("οδ. Πειραιώς"))
    }

    @Test
    fun accentVariantsLastThreeSyllables() {
        assertEquals(listOf("συγγρού", "σύγγρου"), PlaceParsers.accentVariants("συγγρου"))
        assertEquals(listOf("αχαρνές", "αχάρνες", "άχαρνες"), PlaceParsers.accentVariants("αχαρνεσ"))
        assertEquals(listOf("συγγρού 150", "σύγγρου 150"), PlaceParsers.accentVariants("Συγγρου 150"))
        assertTrue(PlaceParsers.accentVariants("Συγγρού").isEmpty())
        assertTrue(PlaceParsers.accentVariants("Syggrou").isEmpty())
        // δίφθογγος «ει»: ο τόνος στο δεύτερο γράμμα
        assertTrue(PlaceParsers.accentVariants("πειραιως").contains("πειραιώς"))
    }

    @Test
    fun surnameMatchesFullStreetName() {
        assertEquals(0, PlaceParsers.textScore("Συγγρού", "Λεωφόρος Συγγρού"))
        assertEquals(1, PlaceParsers.textScore("Συγγρού", "Λεωφόρος Ανδρέα Συγγρού"))
        assertEquals(1, PlaceParsers.textScore("συγγρου", "Λεωφόρος Ανδρέα Συγγρού"))
        assertEquals(0, PlaceParsers.textScore("Λεωφόρος Συγγρού", "Συγγρού"))
    }

    @Test
    fun streetAboveFarPoiWithSameWord() {
        val near = LatLon(37.95, 23.72)
        val street = Place("Λεωφόρος Ανδρέα Συγγρού", "Αθήνα", 37.955, 23.722, "primary")
        val shop = Place("Φαρμακείο Συγγρού Παπαδόπουλος", "Θεσσαλονίκη", 40.6, 22.9, "pharmacy")
        assertEquals(street, PlaceParsers.rank("Συγγρού", listOf(shop, street), emptyList(), near).first())
    }

    @Test
    fun missingHouseNumberIsSaid() {
        val street = Place("Λεωφόρος Ανδρέα Συγγρού", "Αθήνα", 37.955, 23.722, "primary")
        val house = Place("Λεωφόρος Ανδρέα Συγγρού 150", "Καλλιθέα", 37.95, 23.71, "house")
        val out = PlaceParsers.markMissingNumber(listOf(house, street), "150")
        assertEquals(house, out[0])
        assertTrue(out[1].detail, out[1].detail.startsWith("χωρίς τον αριθμό 150"))
        assertEquals(listOf(street), PlaceParsers.markMissingNumber(listOf(street), null))
    }
}
