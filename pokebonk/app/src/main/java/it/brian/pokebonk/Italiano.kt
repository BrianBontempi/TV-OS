package it.brian.pokebonk

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Patch italiano. Il gioco non ha l'italiano, quindi l'app lo mette al posto dell'inglese:
 * nel menu delle lingue "English" diventa "Italiano" e, quando la pagina chiede i testi inglesi,
 * riceve quelli tradotti negli asset dell'app con l'inglese originale sotto.
 * Così una frase nuova, aggiunta al gioco dopo la traduzione, resta in inglese invece di sparire.
 * I file del gioco non sono copiati né salvati: arrivano sempre dal sito, l'app li completa al volo.
 */
class Italiano(private val asset: AssetManager) {

    /** Testi dell'interfaccia: chiave spagnola del gioco → frase italiana. */
    private val testi by lazy { leggi("italiano/testi.json") }

    /** Nomi ufficiali italiani di Pokémon, mosse, abilità, tipi e strumenti, con le descrizioni. */
    private val dati by lazy { leggi("italiano/dati.json") }

    /** Risponde al posto del sito solo per i tre file della lingua inglese; per tutto il resto null (va il sito). */
    fun intercetta(richiesta: WebResourceRequest): WebResourceResponse? {
        val url = richiesta.url
        if (richiesta.method != "GET" || url.host != HOST || url.getQueryParameter(ORIGINALE) != null) return null
        val percorso = url.path ?: return null
        return try {
            when {
                TESTI.matches(percorso) -> risposta("text/javascript",
                    "import o from ${originale(url)};export default Object.assign({},o,$testi);")
                LINGUE.matches(percorso) -> risposta("text/javascript",
                    "import * as m from ${originale(url)};export * from ${originale(url)};" +
                        "for(const v of Object.values(m))if(Array.isArray(v))for(const l of v)" +
                        "if(l&&l.id===\"en\"&&l.locale){l.name=\"Italiano\";l.locale=\"it-IT\";l.short=\"IT\"}")
                percorso.endsWith(DATI) -> risposta("application/json", unisciDati(url))
                else -> null
            }
        } catch (e: Exception) {
            // Se qualcosa va storto il gioco carica l'inglese normale: meglio in inglese che bloccato.
            null
        }
    }

    /** Il file originale, tra virgolette per il codice: stesso indirizzo con ?originale=1, che qui non viene toccato. */
    private fun originale(url: Uri): String =
        JSONObject.quote(url.buildUpon().appendQueryParameter(ORIGINALE, "1").build().toString())

    /** I dati inglesi del sito con sopra quelli italiani; se il sito non risponde, solo quelli italiani. */
    private fun unisciDati(url: Uri): String {
        val italiani = JSONObject(dati)
        val inglesi = try {
            JSONObject(scarica(url.toString()))
        } catch (e: Exception) {
            return dati
        }
        for (sezione in italiani.keys()) {
            val tradotti = italiani.optJSONObject(sezione) ?: continue
            val base = inglesi.optJSONObject(sezione) ?: JSONObject().also { inglesi.put(sezione, it) }
            for (chiave in tradotti.keys()) base.put(chiave, tradotti.get(chiave))
        }
        return inglesi.toString()
    }

    private fun scarica(indirizzo: String): String {
        val connessione = URL(indirizzo).openConnection() as HttpURLConnection
        connessione.connectTimeout = 15_000
        connessione.readTimeout = 15_000
        try {
            if (connessione.responseCode != 200) throw IllegalStateException("HTTP ${connessione.responseCode}")
            return connessione.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connessione.disconnect()
        }
    }

    private fun leggi(nome: String): String =
        asset.open(nome).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun risposta(tipo: String, testo: String): WebResourceResponse =
        WebResourceResponse(tipo, "utf-8", 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "https://$HOST", "Cache-Control" to "no-store"),
            ByteArrayInputStream(testo.toByteArray(Charsets.UTF_8)))

    companion object {
        private const val HOST = "pokebonk.com"
        private const val ORIGINALE = "originale"
        private const val DATI = "/data/i18n/en.json"
        private val TESTI = Regex("/assets/en-[^/]*\\.js")
        private val LINGUE = Regex("/assets/i18n-[^/]*\\.js")
    }
}
