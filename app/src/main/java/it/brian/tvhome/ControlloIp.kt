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
    private const val LOCALE = "127.0.0.1"

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

    /** Un'impostazione immagine come la descrive la TV: valore attuale e valori possibili. */
    data class Voce(val target: String, val valore: String, val scelte: List<String>, val minimo: Int?, val massimo: Int?, val disponibile: Boolean)

    /** Le impostazioni immagine dell'ingresso in uso, o null se la TV non risponde. Da chiamare fuori dal thread principale. */
    fun leggiImmagine(): List<Voce>? =
        voci(chiama(LOCALE, "video", "getPictureQualitySettings", "1.0", obiettivoTutti()))

    /** Le impostazioni audio (es. outputTerminal, l'uscita audio), o null se la TV non risponde. Fuori dal thread principale. */
    fun leggiAudio(): List<Voce>? =
        voci(chiama(LOCALE, "audio", "getSoundSettings", "1.1", obiettivoTutti()))

    /** Cambia le impostazioni audio. Restituisce null se la TV le ha accettate, altrimenti il motivo. */
    fun impostaAudio(valori: Map<String, String>): String? {
        if (valori.isEmpty()) return null
        val impostazioni = JSONArray()
        valori.forEach { (target, valore) -> impostazioni.put(JSONObject().put("target", target).put("value", valore)) }
        return chiama(LOCALE, "audio", "setSoundSettings", "1.1",
            JSONArray().put(JSONObject().put("settings", impostazioni))).problema
    }

    /** Il volume di un'uscita audio, come lo descrive la TV. */
    data class Volume(val target: String, val livello: Int, val massimo: Int, val muto: Boolean)

    /** Il volume dell'uscita in uso (la prima che la TV elenca), o null se la TV non risponde. */
    fun leggiVolume(): Volume? {
        val elenco = chiama(LOCALE, "audio", "getVolumeInformation", "1.0", JSONArray()).risultato?.optJSONArray(0) ?: return null
        val voce = elenco.optJSONObject(0) ?: return null
        return Volume(voce.optString("target"), voce.optInt("volume"), voce.optInt("maxVolume", 100), voce.optBoolean("mute"))
    }

    /** Cambia il volume: un numero ("25") o un passo ("+1", "-1"). Restituisce null se riuscito, altrimenti il motivo. */
    fun cambiaVolume(target: String, valore: String): String? =
        chiama(LOCALE, "audio", "setAudioVolume", "1.0",
            JSONArray().put(JSONObject().put("target", target).put("volume", valore))).problema

    fun impostaMuto(muto: Boolean): String? =
        chiama(LOCALE, "audio", "setAudioMute", "1.0", JSONArray().put(JSONObject().put("status", muto))).problema

    /** Mette la TV in standby. */
    fun spegniTv(): String? =
        chiama(LOCALE, "system", "setPowerStatus", "1.0", JSONArray().put(JSONObject().put("status", false))).problema

    /** L'indirizzo Sony di quello che si sta guardando (es. "extInput:hdmi?port=3"), o null se è un'app. */
    fun inVisione(): String? =
        chiama(LOCALE, "avContent", "getPlayingContentInfo", "1.0", JSONArray()).risultato?.optJSONObject(0)?.optString("uri")?.ifBlank { null }

    /** Le voci di una risposta get...Settings: valore attuale, scelte possibili e intervallo. */
    private fun voci(risposta: Risposta): List<Voce>? {
        val elenco = risposta.risultato?.optJSONArray(0) ?: return null
        return (0 until elenco.length()).mapNotNull { elenco.optJSONObject(it) }.map { voce ->
            val candidati = voce.optJSONArray("candidate") ?: JSONArray()
            val scelte = (0 until candidati.length()).mapNotNull { candidati.optJSONObject(it) }
            val intervallo = scelte.firstOrNull { it.has("max") }
            Voce(
                voce.optString("target"),
                voce.optString("currentValue"),
                scelte.filter { it.has("value") }.map { it.optString("value") },
                intervallo?.optInt("min"),
                intervallo?.optInt("max"),
                voce.optBoolean("isAvailable", true)
            )
        }
    }

    /**
     * Cambia le impostazioni immagine dell'ingresso in uso con il comando ufficiale Sony.
     * Restituisce null se la TV le ha accettate, altrimenti il motivo. Da chiamare fuori dal thread principale.
     */
    fun impostaImmagine(valori: Map<String, String>): String? {
        if (valori.isEmpty()) return null
        val impostazioni = JSONArray()
        valori.forEach { (target, valore) -> impostazioni.put(JSONObject().put("target", target).put("value", valore)) }
        val risposta = chiama(LOCALE, "video", "setPictureQualitySettings", "1.0",
            JSONArray().put(JSONObject().put("settings", impostazioni)))
        return risposta.problema
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
