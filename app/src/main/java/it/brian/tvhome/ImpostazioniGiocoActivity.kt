package it.brian.tvhome

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Impostazioni immagine di una console, ridisegnate: ogni voce è un'impostazione Sony vera.
 * Le scelte si mandano alla TV quando si apre la console con Gioca, perché Sony le salva per ingresso.
 * Le voci non toccate restano come sono sulla TV.
 */
class ImpostazioniGiocoActivity : SchermataTv() {

    private lateinit var console: String
    private lateinit var elenco: LinearLayout
    private lateinit var stato: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        console = intent.getStringExtra(CONSOLE).orEmpty()

        val radice = FrameLayout(this)
        radice.addView(SfondoAurora(this, Color.rgb(10, 12, 32), intArrayOf(
            Color.rgb(0, 82, 200),
            Color.rgb(16, 140, 60),
            Color.rgb(200, 20, 40),
            Color.rgb(110, 50, 220)
        )), FrameLayout.LayoutParams(MATCH, MATCH))

        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(48), dp(28), dp(48), dp(24))
        }
        radice.addView(contenuto, FrameLayout.LayoutParams(MATCH, MATCH))

        // A sinistra le voci, a destra spiegazione e pulsanti.
        elenco = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val colonnaVoci = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(testo("$console · Immagine", 30f, Typeface.BOLD))
            addView(testo("← → cambia il valore · OK lo lascia com'è sulla TV", 13f, Typeface.NORMAL).apply {
                setTextColor(Stile.TESTO_SECONDARIO)
                setPadding(0, dp(4), 0, dp(12))
            })
            addView(ScrollView(this@ImpostazioniGiocoActivity).apply {
                isVerticalScrollBarEnabled = false
                isFillViewport = true
                addView(elenco)
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        contenuto.addView(colonnaVoci, LinearLayout.LayoutParams(0, MATCH, 3f))

        stato = testo("", 14f, Typeface.NORMAL).apply { setTextColor(Stile.TESTO_SECONDARIO) }
        val colonnaAzioni = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Stile.vetro(this@ImpostazioniGiocoActivity, 20)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(testo("Come funziona", 18f, Typeface.BOLD))
            addView(testo("Quello che scegli qui cambia le impostazioni Sony vere dell'ingresso di $console, " +
                "nel momento in cui premi Gioca. Le voci che non tocchi restano come sono.", 14f, Typeface.NORMAL).apply {
                setTextColor(Stile.TESTO_SECONDARIO)
                setPadding(0, dp(8), 0, dp(16))
            })
            addView(stato)
            addView(View(this@ImpostazioniGiocoActivity), LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(pulsante("Gioca con queste impostazioni") { gioca() }, LinearLayout.LayoutParams(MATCH, dp(48)))
            addView(pulsante("Lascia tutto come sulla TV") { azzera() }, LinearLayout.LayoutParams(MATCH, dp(48)).apply { topMargin = dp(12) })
            addView(testo("VRR, formato HDMI, ALLM e Motionflow Sony non li rende modificabili dalle app: si cambiano dalle impostazioni della TV.", 12f, Typeface.NORMAL).apply {
                setTextColor(Stile.TESTO_SECONDARIO)
                setPadding(0, dp(20), 0, dp(8))
            })
            addView(pulsante("Impostazioni TV") { avvia(Intent(Settings.ACTION_SETTINGS), "Impostazioni") }, LinearLayout.LayoutParams(MATCH, dp(44)))
        }
        contenuto.addView(colonnaAzioni, LinearLayout.LayoutParams(0, MATCH, 2f).apply { marginStart = dp(32) })

        setContentView(radice)
        carica()
    }

    /** Valori e scelte possibili: quelli letti dall'ingresso della console, altrimenti quelli dell'ingresso in uso. */
    private fun carica() {
        val lette = ProfiliGioco.lette(this, console)
        if (lette.isNotEmpty()) {
            stato.text = "Valori letti dalla TV l'ultima volta che hai giocato con $console."
            mostra(lette, daConsole = true)
            return
        }
        stato.text = "Lettura delle impostazioni dalla TV…"
        thread {
            val voci = ControlloIp.leggiImmagine()
            runOnUiThread {
                if (voci == null) {
                    stato.text = "La TV non risponde: controlla che Controllo IP sia attivo con la chiave ${ControlloIp.CHIAVE}."
                } else {
                    stato.text = "Non hai ancora giocato con $console da qui: i valori veri di quell'ingresso compariranno dopo il primo Gioca."
                    mostra(voci, daConsole = false)
                }
            }
        }
    }

    private fun mostra(voci: List<ControlloIp.Voce>, daConsole: Boolean) {
        elenco.removeAllViews()
        val perNome = voci.filter { it.disponibile }.associateBy { it.target }
        val ordine = VOCI.keys.filter { it in perNome } + perNome.keys.filter { it !in VOCI }
        for (target in ordine) {
            elenco.addView(riga(perNome.getValue(target), daConsole), LinearLayout.LayoutParams(MATCH, dp(52)).apply { bottomMargin = dp(6) })
        }
        elenco.getChildAt(0)?.requestFocus()
    }

    private fun riga(voce: ControlloIp.Voce, daConsole: Boolean): View {
        val nome = testo(VOCI[voce.target]?.first ?: voce.target, 16f, Typeface.BOLD)
        val valore = testo("", 16f, Typeface.BOLD).apply { gravity = Gravity.END }
        val nota = testo("", 11f, Typeface.NORMAL).apply {
            gravity = Gravity.END
            setTextColor(Stile.TESTO_SECONDARIO)
        }

        fun aggiorna() {
            val scelto = ProfiliGioco.scelte(this, console)[voce.target]
            val mostrato = scelto ?: if (daConsole) voce.valore else null
            valore.text = if (mostrato == null) "‹  —  ›" else "‹  ${etichettaValore(voce.target, mostrato)}  ›"
            valore.setTextColor(if (scelto != null) Stile.TESTO else Stile.TESTO_SECONDARIO)
            nota.text = if (scelto != null) "scelto da te" else "come sulla TV"
        }

        fun cambia(verso: Int) {
            val attuale = ProfiliGioco.scelte(this, console)[voce.target] ?: voce.valore
            val nuovo = if (voce.scelte.isNotEmpty()) {
                val i = voce.scelte.indexOf(attuale).let { if (it < 0) 0 else it }
                voce.scelte[(i + verso + voce.scelte.size) % voce.scelte.size]
            } else {
                val minimo = voce.minimo ?: 0
                val massimo = voce.massimo ?: 100
                ((attuale.toIntOrNull() ?: minimo) + verso).coerceIn(minimo, massimo).toString()
            }
            ProfiliGioco.salvaScelta(this, console, voce.target, nuovo)
            aggiorna()
        }

        val normale = Stile.pieno(this, 14, Color.argb(20, 255, 255, 255))
        val selezionata = Stile.vetroSelezionato(this, 14)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = normale
            isFocusable = true
            setPadding(dp(18), 0, dp(18), 0)
            addView(nome, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(LinearLayout(this@ImpostazioniGiocoActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(valore)
                addView(nota)
            })
            setOnFocusChangeListener { vista, focus -> vista.background = if (focus) selezionata else normale }
            setOnKeyListener { _, codice, evento ->
                if (evento.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (codice) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { cambia(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cambia(1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        ProfiliGioco.salvaScelta(this@ImpostazioniGiocoActivity, console, voce.target, null)
                        aggiorna()
                        true
                    }
                    else -> false
                }
            }
            aggiorna()
        }
    }

    private fun gioca() {
        val sorgente = Sorgenti.elenco(this).firstOrNull { !it.tv && it.nome == console }
        if (sorgente == null) {
            stato.text = "Non trovo $console tra gli ingressi: accendila e riprova."
            return
        }
        ProfiliGioco.gioca(this, sorgente)
    }

    private fun azzera() {
        for (target in ProfiliGioco.scelte(this, console).keys) ProfiliGioco.salvaScelta(this, console, target, null)
        carica()
    }

    private fun etichettaValore(target: String, valore: String) = VOCI[target]?.second?.get(valore) ?: valore

    private fun pulsante(etichetta: String, azione: () -> Unit) = testo(etichetta, 15f, Typeface.BOLD).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = Stile.vetro(this@ImpostazioniGiocoActivity, 24, forte = true)
        setOnFocusChangeListener { _, focus ->
            background = if (focus) Stile.pieno(this@ImpostazioniGiocoActivity, 24, Color.WHITE) else Stile.vetro(this@ImpostazioniGiocoActivity, 24, forte = true)
            setTextColor(if (focus) Stile.SCURO else Stile.TESTO)
        }
        setOnClickListener { azione() }
    }

    private fun avvia(intent: Intent, descrizione: String) {
        try {
            startActivity(intent)
            Registro.aggiungi(this, Registro.EVENTI, "Aperto: $descrizione")
        } catch (e: Exception) {
            Registro.aggiungi(this, Registro.EVENTI, "Non apribile: $descrizione (${e.javaClass.simpleName})")
        }
    }

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
        private const val CONSOLE = "console"

        private val SI_NO = mapOf("on" to "Sì", "off" to "No")
        private val LIVELLI = mapOf("high" to "Alto", "medium" to "Medio", "low" to "Basso", "off" to "No")

        /** Nome italiano di ogni voce Sony e dei suoi valori, nell'ordine in cui si mostrano. */
        private val VOCI: Map<String, Pair<String, Map<String, String>>> = linkedMapOf(
            "pictureMode" to ("Modalità immagine" to mapOf(
                "vivid" to "Vivido", "standard" to "Standard", "cinema" to "Cinema", "game" to "Gioco",
                "graphics" to "Grafica", "photo" to "Foto", "custom" to "Personalizzato")),
            "brightness" to ("Luminosità" to emptyMap<String, String>()),
            "contrast" to ("Contrasto" to emptyMap<String, String>()),
            "color" to ("Colore" to emptyMap<String, String>()),
            "hue" to ("Tinta" to emptyMap<String, String>()),
            "sharpness" to ("Nitidezza" to emptyMap<String, String>()),
            "colorTemperature" to ("Temperatura colore" to mapOf(
                "cool" to "Fredda", "neutral" to "Neutra", "warm" to "Calda", "expert1" to "Esperto 1", "expert2" to "Esperto 2")),
            "hdrMode" to ("Modalità HDR" to mapOf("auto" to "Automatica", "hdr10" to "HDR10", "hlg" to "HLG", "off" to "No")),
            "xtendedDynamicRange" to ("Gamma dinamica estesa" to LIVELLI),
            "autoLocalDimming" to ("Oscuramento locale" to LIVELLI),
            "colorSpace" to ("Spazio colore" to mapOf(
                "auto" to "Automatico", "sRgb/bt709" to "sRGB / BT.709", "dci" to "DCI", "adobeRgb" to "Adobe RGB", "bt2020" to "BT.2020")),
            "lightSensor" to ("Sensore di luce" to SI_NO),
            "autoPictureMode" to ("Modalità immagine automatica" to mapOf("auto" to "Automatica", "auto24p" to "Automatica (24p)", "off" to "No"))
        )

        fun apri(context: Context, console: String) {
            context.startActivity(Intent(context, ImpostazioniGiocoActivity::class.java).putExtra(CONSOLE, console))
        }
    }
}
