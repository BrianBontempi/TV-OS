package it.brian.tvhome

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Le impostazioni immagine scelte per ogni console nella sezione Gaming.
 * Sony le salva per ingresso, quindi si mandano alla TV subito dopo aver aperto l'ingresso della console:
 * cambiano davvero le impostazioni Sony di quell'ingresso, e solo le voci scelte qui.
 */
object ProfiliGioco {

    private const val FILE = "profili_gioco"
    private const val ATTESA_INGRESSO_MS = 3000L

    /** I valori scelti per la console; le voci assenti restano come sono sulla TV. */
    fun scelte(context: Context, console: String): Map<String, String> {
        val json = prefs(context).getString("scelte_$console", null) ?: return emptyMap()
        val oggetto = try { JSONObject(json) } catch (e: Exception) { return emptyMap() }
        return oggetto.keys().asSequence().associateWith { oggetto.optString(it) }
    }

    fun salvaScelta(context: Context, console: String, target: String, valore: String?) {
        val nuove = scelte(context, console).toMutableMap()
        if (valore == null) nuove.remove(target) else nuove[target] = valore
        prefs(context).edit().putString("scelte_$console", JSONObject(nuove.toMap()).toString()).apply()
    }

    /** Le impostazioni lette l'ultima volta dall'ingresso della console (valori e scelte possibili). */
    fun lette(context: Context, console: String): List<ControlloIp.Voce> {
        val json = prefs(context).getString("lette_$console", null) ?: return emptyList()
        return try {
            val elenco = JSONArray(json)
            (0 until elenco.length()).map { i ->
                val o = elenco.getJSONObject(i)
                val scelte = o.optJSONArray("scelte") ?: JSONArray()
                ControlloIp.Voce(
                    o.getString("target"),
                    o.optString("valore"),
                    (0 until scelte.length()).map { scelte.getString(it) },
                    if (o.has("min")) o.getInt("min") else null,
                    if (o.has("max")) o.getInt("max") else null,
                    o.optBoolean("disponibile", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun salvaLette(context: Context, console: String, voci: List<ControlloIp.Voce>) {
        val elenco = JSONArray()
        for (v in voci) {
            elenco.put(JSONObject().apply {
                put("target", v.target)
                put("valore", v.valore)
                put("scelte", JSONArray(v.scelte))
                v.minimo?.let { put("min", it) }
                v.massimo?.let { put("max", it) }
                put("disponibile", v.disponibile)
            })
        }
        prefs(context).edit().putString("lette_$console", elenco.toString()).apply()
    }

    /** Apre l'ingresso della console, poi manda alla TV le impostazioni scelte e rilegge quelle vere. */
    fun gioca(context: Context, sorgente: Sorgente) {
        val app = context.applicationContext
        Sorgenti.apri(context, sorgente)
        val valori = scelte(app, sorgente.nome)
        thread {
            Thread.sleep(ATTESA_INGRESSO_MS)
            var problema = applica(valori)
            if (problema != null) {
                // L'ingresso a volte ci mette di più ad aprirsi: un secondo tentativo.
                Thread.sleep(ATTESA_INGRESSO_MS)
                problema = applica(valori)
            }
            ControlloIp.leggiImmagine()?.let { salvaLette(app, sorgente.nome, it) }
            if (valori.isEmpty()) return@thread
            val messaggio = if (problema == null) "Impostazioni di gioco applicate" else "La TV non ha accettato le impostazioni: $problema"
            Registro.aggiungi(app, Registro.EVENTI, "${sorgente.nome}: $messaggio")
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, messaggio, Toast.LENGTH_LONG).show() }
        }
    }

    /**
     * La modalità immagine va per prima: cambiandola, la TV ricarica gli altri valori di quella modalità.
     * Le altre voci vanno una alla volta, così un valore rifiutato non blocca gli altri.
     */
    private fun applica(valori: Map<String, String>): String? {
        valori["pictureMode"]?.let { modo ->
            ControlloIp.impostaImmagine(mapOf("pictureMode" to modo))?.let { return "Modalità immagine: $it" }
        }
        val rifiutati = (valori - "pictureMode").mapNotNull { (target, valore) ->
            ControlloIp.impostaImmagine(mapOf(target to valore))?.let { "$target: $it" }
        }
        return rifiutati.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
