package it.brian.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * "Le mie app": gli APK pubblicati nelle release del repository GitHub, da installare con un clic.
 * Per aggiungere un'app basta caricare il suo APK in una release su GitHub.
 */
class StoreActivity : Activity() {

    private lateinit var elenco: LinearLayout
    private lateinit var stato: TextView

    /** Un APK trovato in una release. */
    private data class Pacchetto(val nome: String, val release: String, val byte: Long, val data: String, val indirizzo: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val radice = FrameLayout(this)
        radice.addView(SfondoAurora(this), FrameLayout.LayoutParams(MATCH, MATCH))
        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(24))
        }
        radice.addView(contenuto, FrameLayout.LayoutParams(MATCH, MATCH))

        contenuto.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(testo("Le mie app", 30f, Typeface.BOLD))
            addView(View(this@StoreActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(Stile.pillola(this@StoreActivity, "Aggiorna elenco") { carica() }, LinearLayout.LayoutParams(WRAP, dp(40)))
        })
        stato = testo("", 14f, Typeface.NORMAL).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            setPadding(0, dp(6), 0, dp(16))
        }
        contenuto.addView(stato)

        elenco = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        contenuto.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(elenco)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(radice)
        Aggiornamento.gestisciEsito(this, intent) { avviso(it) }
        carica()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Aggiornamento.gestisciEsito(this, intent) { avviso(it) }
    }

    private fun carica() {
        stato.text = "Cerco le app su GitHub…"
        elenco.removeAllViews()
        thread {
            val pacchetti = try { leggiRelease() } catch (e: Exception) { null }
            runOnUiThread { mostra(pacchetti) }
        }
    }

    private fun mostra(pacchetti: List<Pacchetto>?) {
        elenco.removeAllViews()
        when {
            pacchetti == null -> stato.text = "GitHub non risponde: controlla la rete e premi Aggiorna elenco."
            pacchetti.isEmpty() -> stato.text = "Nessun APK trovato. Caricane uno in una release del repository $REPOSITORY e premi Aggiorna elenco."
            else -> {
                stato.text = "${pacchetti.size} app nel repository $REPOSITORY. Premi su un'app per installarla o aggiornarla."
                pacchetti.forEach { elenco.addView(riga(it), LinearLayout.LayoutParams(MATCH, dp(64)).apply { bottomMargin = dp(8) }) }
                elenco.getChildAt(0)?.requestFocus()
            }
        }
    }

    private fun riga(pacchetto: Pacchetto): View {
        val normale = Stile.vetro(this, 16)
        val selezionata = Stile.vetroSelezionato(this, 16)
        val installa = testo("Installa", 14f, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = Stile.vetro(this@StoreActivity, 18, forte = true)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = normale
            isFocusable = true
            isClickable = true
            setPadding(dp(20), 0, dp(16), 0)
            addView(LinearLayout(this@StoreActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(testo(pacchetto.nome, 17f, Typeface.BOLD))
                addView(testo("${pacchetto.release} · ${megabyte(pacchetto.byte)} · ${pacchetto.data}", 12f, Typeface.NORMAL).apply {
                    setTextColor(Stile.TESTO_SECONDARIO)
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(installa)
            setOnFocusChangeListener { vista, focus ->
                vista.background = if (focus) selezionata else normale
                installa.background = if (focus) Stile.pieno(this@StoreActivity, 18, Color.WHITE) else Stile.vetro(this@StoreActivity, 18, forte = true)
                installa.setTextColor(if (focus) Stile.SCURO else Stile.TESTO)
            }
            setOnClickListener { Aggiornamento.installa(this@StoreActivity, pacchetto.indirizzo, pacchetto.nome) { avviso(it) } }
        }
    }

    /** Tutti gli APK delle release pubbliche del repository, i più recenti prima. */
    private fun leggiRelease(): List<Pacchetto> {
        val connessione = URL("https://api.github.com/repos/$REPOSITORY/releases?per_page=50").openConnection() as HttpURLConnection
        val testo = try {
            connessione.connectTimeout = 10000
            connessione.readTimeout = 15000
            connessione.setRequestProperty("Accept", "application/vnd.github+json")
            connessione.setRequestProperty("User-Agent", "TVHome/1.0")
            connessione.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connessione.disconnect()
        }
        val lettura = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val scrittura = SimpleDateFormat("d MMM yyyy", Locale.ITALY)
        val release = JSONArray(testo)
        val trovati = mutableListOf<Pair<String, Pacchetto>>()
        for (i in 0 until release.length()) {
            val r = release.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val nomeRelease = r.optString("name").ifBlank { r.optString("tag_name") }
            val allegati = r.optJSONArray("assets") ?: continue
            for (j in 0 until allegati.length()) {
                val a = allegati.getJSONObject(j)
                val file = a.optString("name")
                if (!file.endsWith(".apk", ignoreCase = true)) continue
                val aggiornato = a.optString("updated_at")
                val data = try { scrittura.format(lettura.parse(aggiornato)!!) } catch (e: Exception) { aggiornato }
                val nome = if (file == "tv-home.apk") "TV Home (questa app)" else file.dropLast(4).replace('-', ' ').replace('_', ' ')
                trovati += aggiornato to Pacchetto(nome, nomeRelease, a.optLong("size"), data, a.optString("browser_download_url"))
            }
        }
        return trovati.sortedByDescending { it.first }.map { it.second }
    }

    private fun megabyte(byte: Long) = String.format(Locale.ITALY, "%.1f MB", byte / 1_048_576.0)

    private fun avviso(messaggio: String) = Toast.makeText(this, messaggio, Toast.LENGTH_LONG).show()

    private fun testo(contenuto: String, dimensione: Float, stile: Int) = TextView(this).apply {
        text = contenuto
        setTextColor(Stile.TESTO)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensione)
        setTypeface(Typeface.create("sans-serif", stile))
    }

    private fun dp(valore: Int) = Stile.dp(this, valore)

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val REPOSITORY = "BrianBontempi/TV-OS"
    }
}
