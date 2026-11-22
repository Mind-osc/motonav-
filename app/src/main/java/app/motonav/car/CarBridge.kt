package app.motonav.car

import android.content.Context
import app.motonav.nav.NavSession
import kotlinx.coroutines.CoroutineScope

/**
 * Μία πλοήγηση για κινητό και Android Auto: αν η εφαρμογή είναι ανοιχτή στο κινητό, το αυτοκίνητο
 * δείχνει την ίδια· αλλιώς το Android Auto φτιάχνει τη δική του (με δικό του GPS).
 */
object CarBridge {
    @Volatile var phoneSession: NavSession? = null
    private var carSession: NavSession? = null

    fun session(context: Context, scope: CoroutineScope): NavSession =
        phoneSession ?: carSession ?: NavSession(context.applicationContext, scope).also { carSession = it }

    /** Το αυτοκίνητο έχει δική του πλοήγηση (άρα του στέλνει πάντα στίγματα). */
    fun ownsSession(s: NavSession) = s === carSession

    fun release() {
        carSession?.let { if (!it.navigating) it.dispose() }
        carSession = null
    }
}
