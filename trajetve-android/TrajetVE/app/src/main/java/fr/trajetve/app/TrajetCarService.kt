package fr.trajetve.app

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate

/** Point d'entrée Android Auto. */
class TrajetCarService : CarAppService() {
    // Appli personnelle : tous les hôtes sont acceptés (Android Auto, émulateur DHU)
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = TripScreen(carContext)
    }
}

/** Écran voiture : batterie, prochains arrêts de recharge (carte + liste), arrivée. */
class TripScreen(carContext: CarContext) : Screen(carContext) {

    private var trip: Trip? = null
    private var car: CarState? = null
    private var loaded = false
    private val regs = mutableListOf<ListenerRegistration>()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { listen() }
            override fun onStop(owner: LifecycleOwner) {
                regs.forEach { it.remove() }
                regs.clear()
            }
        })
    }

    private fun merge(c: CarState?) {
        if (c != null && (car == null || c.at > car!!.at)) car = c
    }

    private fun listen() {
        val u = FirebaseAuth.getInstance().currentUser
        if (u == null) { loaded = true; invalidate(); return }
        val ref = FirebaseFirestore.getInstance().collection("trajetve_car").document(u.uid)
        regs += ref.addSnapshotListener { s, _ ->
            trip = Trip.parse(s?.getString("actif"))
            merge(CarState.fromRes(s?.get("res")))
            loaded = true
            invalidate()
        }
        regs += ref.collection("jours").document(LocalDate.now().toString()).addSnapshotListener { s, _ ->
            merge(CarState.fromDay(s?.data))
            invalidate()
        }
        // rien aujourd'hui (voiture garée) : dernier relevé de la veille
        ref.collection("jours").document(LocalDate.now().minusDays(1).toString()).get()
            .addOnSuccessListener { s -> merge(CarState.fromDay(s.data)); invalidate() }
    }

    private fun maxRows(): Int = try {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
    } catch (e: Exception) {
        6
    }

    private fun navigate(name: String, lat: Double, lon: Double) {
        try {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})")
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
        } catch (e: Exception) {
            CarToast.makeText(carContext, "Navigation impossible", CarToast.LENGTH_LONG).show()
        }
    }

    private fun place(lat: Double?, lon: Double?, label: String): Metadata? {
        if (lat == null || lon == null) return null
        val p = Place.Builder(CarLocation.create(lat, lon))
            .setMarker(PlaceMarker.Builder().setLabel(label.take(3)).build())
            .build()
        return Metadata.Builder().setPlace(p).build()
    }

    override fun onGetTemplate(): Template {
        if (FirebaseAuth.getInstance().currentUser == null) {
            return MessageTemplate.Builder("Ouvre Trajet VE sur le téléphone et connecte-toi avec Google.")
                .setTitle("Trajet VE")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        val refresh = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Actualiser").setOnClickListener { invalidate() }.build())
            .build()
        if (!loaded) {
            return PlaceListMapTemplate.Builder()
                .setTitle("Trajet VE")
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        val list = ItemList.Builder()
        var rows = 0
        val limit = maxRows()

        car?.let { c ->
            list.addItem(
                Row.Builder()
                    .setTitle("Batterie ${c.soc.toInt()} %${c.range?.let { " · ${it.toInt()} km" } ?: ""}")
                    .addText("Donnée de la voiture ${ago(c.at)}")
                    .build()
            )
            rows++
        }

        val t = trip
        if (t != null) {
            val upcoming = t.upcoming()
            upcoming.forEachIndexed { i, s ->
                if (rows >= limit - 1) return@forEachIndexed
                val idx = t.stops.indexOf(s) + 1
                val b = Row.Builder()
                    .setTitle("$idx. ${s.name}")
                    .addText("${hm(s.eta)} · ${s.socIn ?: "?"} % → ${s.socOut ?: "?"} %${if (s.pause) " · pause" else ""}")
                    .addText(listOf("${s.dur ?: 0} min", kwTxt(s.kw), s.op).filter { it.isNotBlank() }.joinToString(" · "))
                place(s.lat, s.lon, "$idx")?.let { b.setMetadata(it) }
                if (s.lat != null && s.lon != null) b.setOnClickListener { navigate(s.name, s.lat, s.lon) }
                list.addItem(b.build())
                rows++
            }
            t.end?.let { e ->
                if (rows < limit) {
                    val b = Row.Builder()
                        .setTitle("Arrivée : ${e.name}")
                        .addText("${hm(e.eta)} avec ${e.soc ?: "?"} %${if (upcoming.isEmpty()) " · aucun arrêt restant" else ""}")
                    place(e.lat, e.lon, "A")?.let { b.setMetadata(it) }
                    if (e.lat != null && e.lon != null) b.setOnClickListener { navigate(e.name, e.lat, e.lon) }
                    list.addItem(b.build())
                    rows++
                }
            }
        }
        if (rows == 0) list.setNoItemsMessage("Aucun trajet : calcule-le sur le site ou dans l'appli, avec le même compte.")
        else if (t == null && rows < limit) list.addItem(Row.Builder().setTitle("Aucun trajet en cours").addText("Calcule-le sur le site avec le même compte").build())

        return PlaceListMapTemplate.Builder()
            .setTitle(if (t != null) "Vers ${t.to}" else "Trajet VE")
            .setHeaderAction(Action.APP_ICON)
            .setItemList(list.build())
            .setActionStrip(refresh)
            .build()
    }
}
