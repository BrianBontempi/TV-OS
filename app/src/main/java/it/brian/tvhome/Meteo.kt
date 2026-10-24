package it.brian.tvhome

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Il meteo di oggi in una riga: icona, temperatura, cielo, massima e minima. */
data class DatiMeteo(val icona: String, val temperatura: Int, val cielo: String, val massima: Int, val minima: Int, val citta: String)

/**
 * Meteo da Open-Meteo (gratuito, senza account), aggiornato al massimo ogni 30 minuti.
 * La zona si ricava dalla connessione internet (scelta di Brian), una volta per avvio dell'app.
 */
object Meteo {

    private const val VALIDITA_MS = 30 * 60 * 1000L
    private var ultimo: DatiMeteo? = null
    private var quando = 0L
    private var coordinate: Triple<Double, Double, String>? = null

    fun aggiorna(risultato: (DatiMeteo) -> Unit) {
        val inMemoria = ultimo
        if (inMemoria != null && System.currentTimeMillis() - quando < VALIDITA_MS) {
            risultato(inMemoria)
            return
        }
        thread {
            try {
                val luogo = coordinate ?: posizioneDaRete().also { coordinate = it }
                val dati = leggiMeteo(luogo.first, luogo.second, luogo.third)
                ultimo = dati
                quando = System.currentTimeMillis()
                risultato(dati)
            } catch (e: Exception) {
                // Senza rete il meteo resta quello di prima, o nascosto.
            }
        }
    }

    /** Zona approssimativa dall'indirizzo della connessione; se il primo servizio non risponde prova il secondo. */
    private fun posizioneDaRete(): Triple<Double, Double, String> = try {
        val json = scarica("https://ipapi.co/json/")
        if (json.optBoolean("error", false)) throw IllegalStateException("ipapi.co")
        Triple(json.getDouble("latitude"), json.getDouble("longitude"), json.optString("city"))
    } catch (e: Exception) {
        val json = scarica("https://ipwho.is/")
        if (!json.optBoolean("success", false)) throw IllegalStateException("ipwho.is")
        Triple(json.getDouble("latitude"), json.getDouble("longitude"), json.optString("city"))
    }

    private fun leggiMeteo(lat: Double, lon: Double, citta: String): DatiMeteo {
        val json = scarica("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1")
        val adesso = json.getJSONObject("current")
        val giorno = json.getJSONObject("daily")
        val codice = adesso.getInt("weather_code")
        val diGiorno = adesso.optInt("is_day", 1) == 1
        val (icona, cielo) = descrivi(codice, diGiorno)
        return DatiMeteo(
            icona,
            adesso.getDouble("temperature_2m").roundToInt(),
            cielo,
            giorno.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            giorno.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            citta
        )
    }

    private fun scarica(indirizzo: String): JSONObject {
        val connessione = URL(indirizzo).openConnection() as HttpURLConnection
        connessione.connectTimeout = 10000
        connessione.readTimeout = 10000
        connessione.setRequestProperty("User-Agent", "TVHome/1.0")
        return try {
            JSONObject(connessione.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connessione.disconnect()
        }
    }

    /** Codici meteo WMO usati da Open-Meteo. */
    private fun descrivi(codice: Int, diGiorno: Boolean): Pair<String, String> = when (codice) {
        0 -> (if (diGiorno) "☀️" else "🌙") to "Sereno"
        1 -> (if (diGiorno) "🌤️" else "🌙") to "Quasi sereno"
        2 -> "⛅" to "Poco nuvoloso"
        3 -> "☁️" to "Nuvoloso"
        45, 48 -> "🌫️" to "Nebbia"
        51, 53, 55, 56, 57 -> "🌦️" to "Pioviggine"
        61, 63, 65, 66, 67 -> "🌧️" to "Pioggia"
        71, 73, 75, 77 -> "❄️" to "Neve"
        80, 81, 82 -> "🌦️" to "Rovesci"
        85, 86 -> "🌨️" to "Rovesci di neve"
        95, 96, 97, 99 -> "⛈️" to "Temporale"
        else -> "☁️" to "Variabile"
    }
}
