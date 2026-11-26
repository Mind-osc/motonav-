package app.motonav.routing

/**
 * Επιπλέον αποφυγές (Ρυθμίσεις → Χάρτης → Αποφυγές), όπως στο Kurviger: πλοία, κύριοι δρόμοι,
 * πολύ στενοί δρόμοι, χωματόδρομοι. Τις διαβάζουν το BRouter (δικό μας προφίλ) και το TomTom (όσες υποστηρίζει).
 */
/** Όχημα: μηχανή (προεπιλογή) ή αυτοκίνητο. Ρυθμίσεις → Χάρτης → «Αυτοκίνητο». */
object Vehicle {
    @Volatile var car: Boolean = false
}

object Avoid {
    @Volatile var ferries = false
    @Volatile var mainRoads = false
    @Volatile var narrow = false
    @Volatile var unpaved = false

    val any get() = ferries || mainRoads || narrow || unpaved

    /** Για το προφίλ moto-dirt.brf σε «δρόμο» (χωρίς προτίμηση χώματος). */
    fun brouterParams(): List<String> = buildList {
        if (ferries) add("avoid_ferries=1")
        if (mainRoads) add("avoid_main=1")
        if (narrow) add("avoid_narrow=1")
        if (unpaved) add("avoid_unpaved=1")
    }
}
