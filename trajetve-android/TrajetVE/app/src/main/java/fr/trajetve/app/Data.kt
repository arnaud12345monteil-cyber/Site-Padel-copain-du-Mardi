package fr.trajetve.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

/** Adresse du site (GitHub Pages). */
const val SITE_URL = "https://arnaud12345monteil-cyber.github.io/Site-Padel-copain-du-Mardi/trajet-ve.html"
const val RECHARGES_URL = "https://arnaud12345monteil-cyber.github.io/Site-Padel-copain-du-Mardi/trajetve-recharges.html"

data class Stop(
    val name: String, val lat: Double?, val lon: Double?, val eta: Long?, val dur: Int?,
    val socIn: Int?, val socOut: Int?, val kw: Double?, val op: String, val pause: Boolean
)

data class Via(val name: String, val lat: Double?, val lon: Double?, val eta: Long?, val pause: Int, val soc: Int?)

data class End(val name: String, val lat: Double?, val lon: Double?, val eta: Long?, val soc: Int?)

/** Trajet en cours, écrit par le site dans trajetve_car/<compte>.actif (texte JSON). */
data class Trip(
    val at: Long, val from: String, val to: String, val dep: Long,
    val stops: List<Stop>, val vias: List<Via>, val end: End?
) {
    /** Arrêts encore à venir pendant le trajet ; tous les arrêts avant le départ ou après l'arrivée. */
    fun upcoming(now: Long = System.currentTimeMillis()): List<Stop> {
        val endAt = end?.eta ?: Long.MAX_VALUE
        if (now < dep || now > endAt) return stops
        return stops.filter { (it.eta ?: 0L) + (it.dur ?: 0) * 60_000L > now }
    }

    companion object {
        private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null
        private fun JSONObject.lng(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
        private fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) optInt(k) else null

        fun parse(json: String?): Trip? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                val st = o.optJSONArray("stops")
                val stops = (0 until (st?.length() ?: 0)).map { i ->
                    val s = st!!.getJSONObject(i)
                    Stop(s.optString("n", "Borne"), s.dbl("lat"), s.dbl("lon"), s.lng("eta"), s.int("d"),
                        s.int("si"), s.int("so"), s.dbl("kw"), s.optString("op", ""), s.optBoolean("p", false))
                }
                val vi = o.optJSONArray("vias")
                val vias = (0 until (vi?.length() ?: 0)).map { i ->
                    val v = vi!!.getJSONObject(i)
                    Via(v.optString("n", "Étape"), v.dbl("lat"), v.dbl("lon"), v.lng("eta"), v.optInt("d", 0), v.int("soc"))
                }
                val e = o.optJSONObject("end")
                val end = e?.let { End(it.optString("n", "Arrivée"), it.dbl("lat"), it.dbl("lon"), it.lng("eta"), it.int("soc")) }
                Trip(o.optLong("at"), o.optString("from"), o.optString("to"), o.optLong("dep"), stops, vias, end)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** État de la voiture : dernière lecture à la demande (champ res) ou dernier relevé de la journée. */
data class CarState(val soc: Double, val range: Double?, val at: Long) {
    companion object {
        private fun num(v: Any?): Double? = (v as? Number)?.toDouble()
        private fun time(v: Any?): Long? = when (v) {
            is Number -> v.toLong()
            is String -> try { OffsetDateTime.parse(v).toInstant().toEpochMilli() } catch (e: Exception) { null }
            else -> null
        }

        fun fromRes(v: Any?): CarState? {
            val m = v as? Map<*, *> ?: return null
            val soc = num(m["soc"]) ?: return null
            return CarState(soc, num(m["range"]), time(m["carAt"]) ?: 0L)
        }

        /** Document jours/<date> : listes « points » et « etats ». */
        fun fromDay(data: Map<String, Any>?): CarState? {
            if (data == null) return null
            var best: CarState? = null
            for (key in listOf("points", "etats")) {
                val list = data[key] as? List<*> ?: continue
                for (x in list) {
                    val m = x as? Map<*, *> ?: continue
                    val soc = num(m["soc"]) ?: continue
                    val t = time(m["t"]) ?: continue
                    if (best == null || t > best.at) best = CarState(soc, num(m["range"]), t)
                }
            }
            return best
        }
    }
}

fun hm(ms: Long?): String = if (ms == null || ms <= 0) "—" else SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))

fun ago(ms: Long): String {
    if (ms <= 0) return "heure inconnue"
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "à l'instant"
        m < 60 -> "il y a $m min"
        m < 1440 -> "il y a ${m / 60} h ${"%02d".format(m % 60)}"
        else -> "il y a ${m / 1440} j"
    }
}

fun kwTxt(kw: Double?): String = if (kw == null) "" else "${kw.toInt()} kW"
