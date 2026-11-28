package app.motonav

import android.app.Application
import app.motonav.i18n.Lang

/** Πρώτο πράγμα πριν από οτιδήποτε άλλο (οθόνες, πλοήγηση, Android Auto): η γλώσσα της εφαρμογής. */
class StreetwolfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
    }
}
