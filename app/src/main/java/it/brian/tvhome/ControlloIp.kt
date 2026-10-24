package it.brian.tvhome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Prova del "Controllo IP" Sony (REST API BRAVIA) dalla TV stessa.
 * Usa solo metodi di lettura (get...): non cambia nessuna impostazione.
 * Serve a capire se, in futuro, la sezione Gaming potrà leggere o cambiare le impostazioni immagine.
 */
object ControlloIp {

    /** La chiave da impostare sulla TV in Controllo IP > Chiave pre-condivisa. */
    const val CHIAVE = "1234"

    private const val FILE = "controllo_ip"
    private const val ESITO = "esito"

    /** Solo letture. */
    private val PROVE = listOf(
        Prova("system", "getInterfaceInformation", "1.0", JSONArray()),
        Prova("system", "getPowerStatus", "1.0", JSONArray()),
        Prova("guide", "getSupportedApiInfo", "1.0",
            JSONArray().put(JSONObject().put("services", JSONArray().put("video").put("avContent").put("audio")))),
        Prova("video", "getPictureQualitySettings", "1.0", JSONArray().put(JSONObject().put("target", ""))),
        Prova("avContent", "getCurrentExternalInputsStatus", "1.0", JSONArray())
    )

    private data class Prova(val servizio: String, val metodo: String, val versione: String, val parametri: JSONArray)

    fun avvia(context: Context, finito: (String) -> Unit) {
        val app = context.applicationContext
        thread {
            val testo = buildString {
                appendLine("Prova del ${SimpleDateFormat("dd/MM HH:mm", Locale.ITALY).format(Date())} · chiave \"$CHIAVE\"")
                val indirizzi = listOf("127.0.0.1") + indirizziLocali()
                val indirizzo = indirizzi.firstOrNull { risponde(it) }
                if (indirizzo == null) {
                    appendLine("Nessuna risposta da ${indirizzi.joinToString(", ")}: Controllo IP spento o non raggiungibile.")
                    return@buildString
                }
                appendLine("Risponde su $indirizzo")
                for (prova in PROVE) {
                    appendLine("${prova.servizio}/${prova.metodo}: ${chiama(indirizzo, prova)}")
                }
            }.trimEnd()
            app.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ESITO, testo).apply()
            Registro.aggiungi(app, Registro.EVENTI, "Prova controllo IP: ${testo.lines().getOrNull(1).orEmpty()}")
            finito(testo.lines().getOrNull(1).orEmpty())
        }
    }

    fun ultimoEsito(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ESITO, null) ?: "Mai provato (Diagnostica > Prova controllo IP)"

    private fun risponde(indirizzo: String) = try {
        chiama(indirizzo, PROVE.first(), tentativo = true)
        true
    } catch (e: Exception) {
        false
    }

    private fun chiama(indirizzo: String, prova: Prova, tentativo: Boolean = false): String {
        val connessione = URL("http://$indirizzo/sony/${prova.servizio}").openConnection() as HttpURLConnection
        try {
            connessione.connectTimeout = 3000
            connessione.readTimeout = 5000
            connessione.requestMethod = "POST"
            connessione.doOutput = true
            connessione.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connessione.setRequestProperty("X-Auth-PSK", CHIAVE)
            val corpo = JSONObject()
                .put("method", prova.metodo)
                .put("id", 1)
                .put("params", prova.parametri)
                .put("version", prova.versione)
            connessione.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
            val codice = connessione.responseCode
            val flusso = if (codice in 200..299) connessione.inputStream else connessione.errorStream
            val risposta = flusso?.bufferedReader()?.use { it.readText() }.orEmpty()
            return "HTTP $codice ${riassumi(risposta)}"
        } catch (e: Exception) {
            if (tentativo) throw e
            return "errore ${e.javaClass.simpleName}"
        } finally {
            connessione.disconnect()
        }
    }

    /** Risposta in breve: l'errore Sony con il suo significato, oppure il risultato tagliato. */
    private fun riassumi(risposta: String): String {
        val json = try { JSONObject(risposta) } catch (e: Exception) { return risposta.take(200) }
        json.optJSONArray("error")?.let { errore ->
            val codice = errore.optInt(0)
            val spiegazione = when (codice) {
                401, 403 -> "chiave sbagliata o mancante"
                3 -> "parametri non accettati"
                7 -> "non implementato"
                12 -> "metodo non disponibile"
                14 -> "versione non supportata"
                15 -> "servizio non disponibile"
                40005 -> "TV spenta"
                else -> errore.optString(1)
            }
            return "errore $codice ($spiegazione)"
        }
        return json.optJSONArray("result")?.toString()?.take(600) ?: risposta.take(200)
    }

    private fun indirizziLocali(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress.orEmpty() }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}
