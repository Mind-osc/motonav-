package app.motonav.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import app.motonav.data.SettingsRepo
import app.motonav.search.Place
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Λίστα προορισμών στο Android Auto: σπίτι, δουλειά, αγαπημένα, πρόσφατα. */
class CarDestinationsScreen(carContext: CarContext, private val onPick: (Place) -> Unit) : Screen(carContext) {
    private var items: List<Pair<String, Place>>? = null

    init {
        lifecycleScope.launch {
            val repo = SettingsRepo(carContext.applicationContext)
            val list = ArrayList<Pair<String, Place>>()
            repo.home.first()?.let { list += "Σπίτι" to it }
            repo.work.first()?.let { list += "Δουλειά" to it }
            repo.favorites.first().forEach { list += "★ ${it.name}" to it }
            repo.recents.first().forEach { p -> if (list.none { it.second.latLon == p.latLon }) list += p.name to p }
            items = list.take(MAX)
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val t = ListTemplate.Builder().setTitle("Πού πάμε;").setHeaderAction(Action.BACK)
        val list = items ?: return t.setLoading(true).build()
        val b = ItemList.Builder()
        if (list.isEmpty()) b.setNoItemsMessage("Αποθήκευσε σπίτι, δουλειά ή αγαπημένα από το κινητό.")
        list.forEach { (title, place) ->
            b.addItem(
                Row.Builder()
                    .setTitle(title)
                    .apply { if (place.detail.isNotBlank()) addText(place.detail) }
                    .setOnClickListener {
                        onPick(place)
                        screenManager.pop()
                    }
                    .build(),
            )
        }
        return t.setSingleList(b.build()).build()
    }

    private companion object {
        const val MAX = 6
    }
}
