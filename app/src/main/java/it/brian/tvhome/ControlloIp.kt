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

    /** Esito di una chiamata: il risultato JSON oppure il motivo per cui non è arrivato. */
    private class Risposta(val risultato: JSONArray?, val problema: String?)

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
                val tv = chiama(indirizzo, "system", "getInterfaceInformation", "1.0", JSONArray())
                val modello = tv.risultato?.optJSONObject(0)?.optString("modelName").orEmpty()
                appendLine("Risponde su $indirizzo · modello $modello")

                appendLine("-- Comandi disponibili (set = può cambiare) --")
                appendLine(comandi(indirizzo))

                appendLine("-- Ingresso in uso --")
                appendLine(ingressoInUso(indirizzo))

                appendLine("-- Immagine (valore attuale [scelte]) --")
                appendLine(impostazioni(chiama(indirizzo, "video", "getPictureQualitySettings", "1.0", obiettivoTutti())))

                appendLine("-- Audio --")
                appendLine(impostazioni(chiama(indirizzo, "audio", "getSoundSettings", "1.1", obiettivoTutti())))

                appendLine("-- Ingressi --")
                appendLine(ingressi(chiama(indirizzo, "avContent", "getCurrentExternalInputsStatus", "1.0", JSONArray())))
            }.trimEnd()
            app.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ESITO, testo).apply()
            val riga = testo.lines().getOrNull(1).orEmpty()
            Registro.aggiungi(app, Registro.EVENTI, "Prova controllo IP: $riga")
            finito(riga)
        }
    }

    fun ultimoEsito(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ESITO, null) ?: "Mai provato (Diagnostica > Prova controllo IP)"

    private fun obiettivoTutti() = JSONArray().put(JSONObject().put("target", ""))

    /** Per ogni servizio, i nomi dei comandi; quelli che cambiano qualcosa iniziano con "set". */
    private fun comandi(indirizzo: String): String {
        val servizi = JSONArray().put("video").put("audio").put("avContent").put("system")
        val risposta = chiama(indirizzo, "guide", "getSupportedApiInfo", "1.0", JSONArray().put(JSONObject().put("services", servizi)))
        val elenco = risposta.risultato?.optJSONArray(0) ?: return risposta.problema ?: "nessun dato"
        return (0 until elenco.length()).mapNotNull { elenco.optJSONObject(it) }.joinToString("\n") { servizio ->
            val api = servizio.optJSONArray("apis") ?: JSONArray()
            val nomi = (0 until api.length()).mapNotNull { api.optJSONObject(it)?.optString("name") }
                .filter { it.startsWith("set") || it.startsWith("get") }
                .sortedBy { if (it.startsWith("set")) 0 else 1 }
            "${servizio.optString("service")}: ${nomi.joinToString(", ")}"
        }
    }

    private fun ingressoInUso(indirizzo: String): String {
        val risposta = chiama(indirizzo, "avContent", "getPlayingContentInfo", "1.0", JSONArray())
        val info = risposta.risultato?.optJSONObject(0) ?: return risposta.problema ?: "nessun dato"
        return listOf(info.optString("title"), info.optString("uri")).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Una riga per impostazione: "nome = valore [scelta|scelta]" oppure "nome = valore (min-max)". */
    private fun impostazioni(risposta: Risposta): String {
        val elenco = risposta.risultato?.optJSONArray(0) ?: return risposta.problema ?: "nessun dato"
        return (0 until elenco.length()).mapNotNull { elenco.optJSONObject(it) }.joinToString("\n") { voce ->
            val candidati = voce.optJSONArray("candidate") ?: JSONArray()
            val scelte = (0 until candidati.length()).mapNotNull { candidati.optJSONObject(it) }
            val dettaglio = when {
                scelte.any { it.has("value") } -> scelte.joinToString("|", " [", "]") { it.optString("value") }
                scelte.any { it.has("max") } -> scelte.first().let { " (${it.optInt("min")}-${it.optInt("max")})" }
                else -> ""
            }
            val disponibile = if (voce.optBoolean("isAvailable", true)) "" else " (non disponibile ora)"
            "${voce.optString("target")} = ${voce.optString("currentValue")}$dettaglio$disponibile"
        }
    }

    private fun ingressi(risposta: Risposta): String {
        val elenco = risposta.risultato?.optJSONArray(0) ?: return risposta.problema ?: "nessun dato"
        return (0 until elenco.length()).mapNotNull { elenco.optJSONObject(it) }.joinToString("\n") { voce ->
            val stato = if (voce.optBoolean("connection")) "collegato" else "scollegato"
            "${voce.optString("title")} · $stato · ${voce.optString("uri")}"
        }
    }

    private fun risponde(indirizzo: String) = try {
        chiamaGrezzo(indirizzo, "system", "getInterfaceInformation", "1.0", JSONArray())
        true
    } catch (e: Exception) {
        false
    }

    private fun chiama(indirizzo: String, servizio: String, metodo: String, versione: String, parametri: JSONArray): Risposta = try {
        val (codice, testo) = chiamaGrezzo(indirizzo, servizio, metodo, versione, parametri)
        val json = try { JSONObject(testo) } catch (e: Exception) { null }
        when {
            json == null -> Risposta(null, "HTTP $codice ${testo.take(200)}")
            json.has("error") -> Risposta(null, spiegaErrore(json.optJSONArray("error")))
            else -> Risposta(json.optJSONArray("result"), null)
        }
    } catch (e: Exception) {
        Risposta(null, "errore ${e.javaClass.simpleName}")
    }

    private fun chiamaGrezzo(indirizzo: String, servizio: String, metodo: String, versione: String, parametri: JSONArray): Pair<Int, String> {
        val connessione = URL("http://$indirizzo/sony/$servizio").openConnection() as HttpURLConnection
        try {
            connessione.connectTimeout = 3000
            connessione.readTimeout = 5000
            connessione.requestMethod = "POST"
            connessione.doOutput = true
            connessione.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connessione.setRequestProperty("X-Auth-PSK", CHIAVE)
            val corpo = JSONObject()
                .put("method", metodo)
                .put("id", 1)
                .put("params", parametri)
                .put("version", versione)
            connessione.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
            val codice = connessione.responseCode
            val flusso = if (codice in 200..299) connessione.inputStream else connessione.errorStream
            return codice to flusso?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connessione.disconnect()
        }
    }

    /** L'errore Sony con il suo significato. */
    private fun spiegaErrore(errore: JSONArray?): String {
        val codice = errore?.optInt(0) ?: 0
        val spiegazione = when (codice) {
            401, 403 -> "chiave sbagliata o mancante"
            3 -> "parametri non accettati"
            7 -> "non implementato"
            12 -> "metodo non disponibile"
            14 -> "versione non supportata"
            15 -> "servizio non disponibile"
            40005 -> "TV spenta"
            else -> errore?.optString(1).orEmpty()
        }
        return "errore $codice ($spiegazione)"
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
