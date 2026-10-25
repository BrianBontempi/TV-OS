package it.brian.tvhome

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/** Schermata di diagnostica: aggiornamenti, le mie app, prove e rapporto da fotografare. */
class ProvaActivity : SchermataTv() {

    private lateinit var rigaIngressi: LinearLayout
    private lateinit var pulsanteHome: TextView
    private lateinit var testoRapporto: TextView
    private var ultimoRapporto = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val radice = FrameLayout(this)
        radice.addView(SfondoAurora(this), FrameLayout.LayoutParams(MATCH, MATCH))
        // Un velo scuro in più: qui si legge molto testo.
        radice.addView(View(this).apply { setBackgroundColor(Color.argb(90, 0, 0, 0)) }, FrameLayout.LayoutParams(MATCH, MATCH))

        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(24))
            clipChildren = false
            clipToPadding = false
        }
        radice.addView(contenuto, FrameLayout.LayoutParams(MATCH, MATCH))

        val versione = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        contenuto.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(testo("Diagnostica", 30f, true))
            addView(testo("TV Home $versione", 14f, false).apply {
                setTextColor(Stile.TESTO_SECONDARIO)
                setPadding(dp(16), dp(8), 0, 0)
            })
            addView(View(this@ProvaActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextClock(this@ProvaActivity).apply {
                format24Hour = "HH:mm"
                format12Hour = "HH:mm"
                setTextColor(Stile.TESTO)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(typeface, Typeface.BOLD)
            })
        })

        pulsanteHome = Stile.pillola(this, "") { cambiaIntercettaHome() }
        val pulsanteSuoni = Stile.pillola(this, etichettaSuoni()) {}
        pulsanteSuoni.setOnClickListener {
            Suoni.impostaAttivi(this, !Suoni.attivi())
            pulsanteSuoni.text = etichettaSuoni()
        }
        contenuto.addView(sezione("App",
            Stile.pillola(this, "Aggiorna app") { Aggiornamento.avvia(this) { avviso(it) } },
            Stile.pillola(this, "Le mie app") { startActivity(Intent(this, StoreActivity::class.java)) },
            pulsanteHome,
            pulsanteSuoni,
            Stile.pillola(this, "Accessibilità") { apri(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Impostazioni accessibilità") }
        ))
        contenuto.addView(sezione("Prove",
            Stile.pillola(this, "Prova controllo IP") { provaControlloIp() },
            Stile.pillola(this, "Rapporto a schermo intero") {
                RapportoActivity.apri(this, "Rapporto completo", ultimoRapporto.ifBlank { "Raccolta dati in corso: riprova tra qualche secondo." })
            },
            Stile.pillola(this, "Aggiorna rapporto") { aggiorna() },
            Stile.pillola(this, "Salva su USB") { salvaSuUsb() },
            Stile.pillola(this, "Svuota registro") { Registro.svuota(this); aggiorna() }
        ))

        val scorciatoie = mutableListOf<View>(
            Stile.pillola(this, "Impostazioni") { apri(Intent(Settings.ACTION_SETTINGS), "Impostazioni") },
            Stile.pillola(this, "Schermo") { apri(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Impostazioni schermo") },
            Stile.pillola(this, "Audio") { apri(Intent(Settings.ACTION_SOUND_SETTINGS), "Impostazioni audio") }
        )
        for (classe in Sonda.schermateSony(this)) {
            val etichetta = if (classe == Sonda.IMMAGINE_SONY) "Immagine Sony" else "Sony ${Sonda.nomeBreve(classe)}"
            scorciatoie += Stile.pillola(this, etichetta) {
                apri(Intent().setComponent(ComponentName(Sonda.IMPOSTAZIONI_SONY, classe)), etichetta)
            }
        }
        contenuto.addView(sezione("Scorciatoie", *scorciatoie.toTypedArray()))

        rigaIngressi = rigaPillole()
        contenuto.addView(sezioneConRiga("Ingressi", rigaIngressi))

        testoRapporto = TextView(this).apply {
            setTextColor(Stile.TESTO)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.1f)
        }
        val normale = Stile.vetro(this, 20)
        val selezionato = Stile.vetroSelezionato(this, 20)
        val pannello = ScrollView(this).apply {
            isFocusable = true
            isVerticalScrollBarEnabled = false
            background = normale
            setPadding(dp(20), dp(16), dp(20), dp(16))
            clipToPadding = true
            addView(testoRapporto)
            setOnFocusChangeListener { vista, focus -> vista.background = if (focus) selezionato else normale }
        }
        contenuto.addView(testo("Rapporto", 13f, true).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            setPadding(dp(4), dp(14), 0, dp(6))
        })
        contenuto.addView(pannello, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(radice)
        Aggiornamento.gestisciEsito(this, intent) { avviso(it) }
    }

    /** Una riga di pillole con il suo titolo a sinistra; scorre di lato se non ci sta. */
    private fun sezione(titolo: String, vararg pillole: View): View {
        val riga = rigaPillole()
        pillole.forEach { riga.addView(it, parametriPillola(riga.childCount == 0)) }
        return sezioneConRiga(titolo, riga)
    }

    private fun sezioneConRiga(titolo: String, riga: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, dp(14), 0, 0)
        addView(testo(titolo.uppercase(), 12f, true).apply { setTextColor(Stile.TESTO_SECONDARIO) },
            LinearLayout.LayoutParams(dp(120), WRAP))
        addView(HorizontalScrollView(this@ProvaActivity).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(riga)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    private fun rigaPillole() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private fun parametriPillola(prima: Boolean) = LinearLayout.LayoutParams(WRAP, dp(40)).apply {
        marginStart = if (prima) 0 else dp(10)
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
                rigaIngressi.addView(Stile.pillola(this, Sonda.nomeIngresso(this, info)) { apriIngresso(info) },
                    parametriPillola(rigaIngressi.childCount == 0))
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

    /** Solo letture verso il Controllo IP Sony: non cambia nessuna impostazione. */
    private fun provaControlloIp() {
        avviso("Prova del controllo IP in corso…")
        ControlloIp.avvia(this) { esito ->
            runOnUiThread {
                avviso(esito)
                aggiorna()
                RapportoActivity.apri(this, "Controllo IP Sony", ControlloIp.ultimoEsito(this))
            }
        }
    }

    private fun cambiaIntercettaHome() {
        Registro.impostaIntercettaHome(this, !Registro.intercettaHome(this))
        aggiorna()
    }

    private fun etichettaSuoni() = if (Suoni.attivi()) "Suoni: attivi" else "Suoni: spenti"

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

    private fun testo(contenuto: String, dimensione: Float, grassetto: Boolean) = TextView(this).apply {
        text = contenuto
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensione)
        if (grassetto) setTypeface(typeface, Typeface.BOLD)
    }

    private fun avviso(messaggio: String) = Toast.makeText(this, messaggio, Toast.LENGTH_LONG).show()

    private fun dp(valore: Int) = (valore * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val RICHIESTA_SALVA = 1
    }
}
