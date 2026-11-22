package app.motonav.ui

import android.graphics.Color as AColor
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue

/**
 * Περισσότερη αντίθεση στον σκούρο χάρτη (στυλ «dark» του OpenFreeMap, βασισμένο στο Dark Matter),
 * για να φαίνονται δρόμοι και ονόματα πάνω στη μηχανή.
 *
 * Το αρχικό στυλ ζωγραφίζει τους μεγάλους δρόμους «κούφιους» (γκρι περίγραμμα, σχεδόν μαύρο εσωτερικό)
 * και τους μικρούς σχεδόν μαύρους. Εδώ: γεμάτοι γκρι δρόμοι, πιο ανοιχτοί όσο μεγαλύτεροι,
 * με σκούρο περίγραμμα, και ανοιχτά ονόματα με μαύρο περίγραμμα.
 *
 * Αλλάζουμε τα layers με το όνομά τους (από το style.json του OpenFreeMap) και με τη γενική
 * Layer.setProperties, χωρίς να βασιζόμαστε στον τύπο του αντικειμένου.
 */
object MapContrast {
    private fun c(hex: String) = AColor.parseColor(hex)

    private val roads: Map<String, Array<PropertyValue<*>>> = mapOf(
        "background" to arrayOf(PropertyFactory.backgroundColor(c("#07080B"))),
        "highway_path" to arrayOf(PropertyFactory.lineColor(c("#454A53")), PropertyFactory.lineOpacity(1f)),
        "highway_minor" to arrayOf(PropertyFactory.lineColor(c("#5A606B")), PropertyFactory.lineOpacity(1f)),
        "highway_major_subtle" to arrayOf(PropertyFactory.lineColor(c("#555B66"))),
        "highway_major_casing" to arrayOf(PropertyFactory.lineColor(c("#1B1D22")), PropertyFactory.lineOpacity(1f)),
        "highway_major_inner" to arrayOf(PropertyFactory.lineColor(c("#878D98"))),
        "highway_motorway_subtle" to arrayOf(PropertyFactory.lineColor(c("#6A707B"))),
        "highway_motorway_casing" to arrayOf(PropertyFactory.lineColor(c("#1B1D22")), PropertyFactory.lineOpacity(1f)),
        "highway_motorway_inner" to arrayOf(PropertyFactory.lineColor(c("#A6ACB6"))),
        "railway" to arrayOf(PropertyFactory.lineColor(c("#3A3E46"))),
        "railway_minor" to arrayOf(PropertyFactory.lineColor(c("#3A3E46"))),
        "railway_transit" to arrayOf(PropertyFactory.lineColor(c("#3A3E46"))),
    )

    private val labels = listOf(
        "water_name", "highway_name_other", "highway_name_motorway", "place_other", "place_suburb",
        "place_village", "place_town", "place_city", "place_city_large", "place_state",
        "place_country_other", "place_country_minor", "place_country_major",
    )

    fun apply(style: Style) {
        for ((id, props) in roads) {
            runCatching { style.getLayer(id)?.setProperties(*props) }
        }
        for (id in labels) {
            runCatching {
                style.getLayer(id)?.setProperties(
                    PropertyFactory.textColor(c(if (id.startsWith("highway")) "#E3E6EB" else "#C9CDD4")),
                    PropertyFactory.textHaloColor(c("#07080B")),
                    PropertyFactory.textHaloWidth(1.6f),
                )
            }
        }
    }

    /** Το πρώτο layer με ονόματα. Ό,τι μπαίνει «κάτω» από αυτό δεν σκεπάζει τα ονόματα. */
    fun firstLabelLayerId(style: Style): String? =
        labels.firstOrNull { style.getLayer(it) != null }
}
