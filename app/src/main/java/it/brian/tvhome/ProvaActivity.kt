package it.brian.tvhome

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/** Schermata di diagnostica (ex versione di prova): test, aggiornamento dell'app e rapporto da fotografare. */
class ProvaActivity : Activity() {

    private lateinit var rigaIngressi: LinearLayout
    private lateinit var pulsanteHome: Button
    private lateinit var testoRapporto: TextView
    private var ultimoRapporto = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val margine = dp(32)
        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margine, margine, margine, margine)
            setBackgroundColor(Color.rgb(16, 18, 24))
        }

        val versione = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        radice.addView(testo("TV Home · diagnostica $versione", 32f, true))
        radice.addView(testo("Premi i pulsanti per i test, poi fotografa il rapporto o salvalo su chiavetta USB.", 18f, false))

        pulsanteHome = pulsante("") { cambiaIntercettaHome() }
        radice.addView(riga(
            pulsante("Aggiorna") { aggiorna() },
            pulsante("Aggiorna app") { Aggiornamento.avvia(this) { avviso(it) } },
            pulsanteHome,
            pulsante("Accessibilità") { apri(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Impostazioni accessibilità") },
            pulsante("Salva su USB") { salvaSuUsb() },
            pulsante("Svuota registro") { Registro.svuota(this); aggiorna() }
        ))
        radice.addView(riga(
            pulsante("Impostazioni") { apri(Intent(Settings.ACTION_SETTINGS), "Impostazioni") },
            pulsante("Schermo") { apri(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Impostazioni schermo") },
            pulsante("Audio") { apri(Intent(Settings.ACTION_SOUND_SETTINGS), "Impostazioni audio") }
        ))

        rigaIngressi = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        radice.addView(HorizontalScrollView(this).apply { addView(rigaIngressi) })

        val rigaSony = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (classe in Sonda.schermateSony(this)) {
            val etichetta = if (classe == Sonda.IMMAGINE_SONY) "Immagine Sony" else "Sony ${Sonda.nomeBreve(classe)}"
            rigaSony.addView(pulsante(etichetta) {
                apri(Intent().setComponent(ComponentName(Sonda.IMPOSTAZIONI_SONY, classe)), etichetta)
            })
        }
        if (rigaSony.childCount == 0) rigaSony.addView(testo("Nessuna pagina Sony apribile trovata", 18f, false))
        radice.addView(HorizontalScrollView(this).apply { setPadding(0, dp(12), 0, 0); addView(rigaSony) })

        testoRapporto = testo("", 16f, false).apply { typeface = Typeface.MONOSPACE }
        val scorrimento = ScrollView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            addView(testoRapporto)
        }
        radice.addView(scorrimento, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(radice)
        Aggiornamento.gestisciEsito(this, intent) { avviso(it) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Aggiornamento.gestisciEsito(this, intent) { avviso(it) }
    }

    override fun onResume() {
        super.onResume()
        aggiorna()
    }

    @Deprecated("Usato solo per il salvataggio su USB")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RICHIESTA_SALVA || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(ultimoRapporto.toByteArray(Charsets.UTF_8)) }
            avviso("Rapporto salvato")
            Registro.aggiungi(this, Registro.EVENTI, "Rapporto salvato su $uri")
        } catch (e: Exception) {
            avviso("Salvataggio non riuscito: ${e.message}")
        }
    }

    private fun aggiorna() {
        pulsanteHome.text = if (Registro.intercettaHome(this)) "Intercetta HOME: SÌ" else "Intercetta HOME: NO"

        rigaIngressi.removeAllViews()
        for (info in Sonda.ingressi(this)) {
            if (info.type == TvInputInfo.TYPE_HDMI || info.isPassthroughInput) {
                rigaIngressi.addView(pulsante("Apri ${Sonda.nomeIngresso(this, info)}") { apriIngresso(info) })
            }
        }

        testoRapporto.text = "Raccolta dati in corso…"
        val app = applicationContext
        thread {
            val rapporto = Sonda.rapporto(app)
            runOnUiThread {
                ultimoRapporto = rapporto
                testoRapporto.text = rapporto
            }
        }
    }

    private fun cambiaIntercettaHome() {
        Registro.impostaIntercettaHome(this, !Registro.intercettaHome(this))
        aggiorna()
    }

    private fun apriIngresso(info: TvInputInfo) {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
        apri(intent, "Ingresso ${Sonda.nomeIngresso(this, info)}")
    }

    private fun apri(intent: Intent, descrizione: String) {
        try {
            startActivity(intent)
            Registro.aggiungi(this, Registro.EVENTI, "Aperto: $descrizione")
        } catch (e: Exception) {
            Registro.aggiungi(this, Registro.EVENTI, "Non apribile: $descrizione (${e.javaClass.simpleName})")
            avviso("Non apribile: $descrizione")
        }
    }

    private fun salvaSuUsb() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, "rapporto-tv.txt")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, RICHIESTA_SALVA)
        } catch (e: Exception) {
            Registro.aggiungi(this, Registro.EVENTI, "Salvataggio non disponibile (${e.javaClass.simpleName})")
            avviso("Su questa TV non c'è la finestra per salvare file: fotografa lo schermo")
        }
    }

    private fun riga(vararg viste: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(12), 0, 0)
        viste.forEach { addView(it) }
    }

    private fun pulsante(etichetta: String, azione: () -> Unit) = Button(this).apply {
        text = etichetta
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setOnClickListener { azione() }
    }

    private fun testo(contenuto: String, dimensione: Float, grassetto: Boolean) = TextView(this).apply {
        text = contenuto
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensione)
        if (grassetto) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, 0)
    }

    private fun avviso(messaggio: String) = Toast.makeText(this, messaggio, Toast.LENGTH_LONG).show()

    private fun dp(valore: Int) = (valore * resources.displayMetrics.density).toInt()

    companion object {
        private const val RICHIESTA_SALVA = 1
    }
}
