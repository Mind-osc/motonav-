package app.motonav.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Προσομοίωση πλοήγησης (όπως στο Kurviger): ένα «ψεύτικο» GPS κινείται πάνω στη διαδρομή,
 * για να δεις τη διαδρομή και τις οδηγίες πριν φύγεις. Όσο τρέχει, το πραγματικό GPS αγνοείται.
 */
object Simulator {
    var active by mutableStateOf(false)
    /** Πόσες φορές πιο γρήγορα από το όριο του δρόμου (1×, 2×, 4×). */
    var speedUp by mutableStateOf(1)
}
