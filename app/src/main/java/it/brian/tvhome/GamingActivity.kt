package it.brian.tvhome

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/** Sezione Gaming: una scheda a tutto schermo per ogni console riconosciuta sulle HDMI. */
class GamingActivity : SchermataTv() {

    private lateinit var rigaConsole: LinearLayout

    /** Come appare una console: colore, caratteristiche e funzioni da gioco che supporta. */
    private data class Profilo(val colore: Int, val caratteristiche: String, val funzioni: List<String>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val radice = FrameLayout(this)
        radice.addView(SfondoAurora(this, Color.rgb(10, 12, 32), intArrayOf(
            Color.rgb(0, 82, 200),
            Color.rgb(16, 140, 60),
            Color.rgb(200, 20, 40),
            Color.rgb(110, 50, 220)
        )), FrameLayout.LayoutParams(MATCH, MATCH))

        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(24))
            clipChildren = false
            clipToPadding = false
        }
        radice.addView(contenuto, FrameLayout.LayoutParams(MATCH, MATCH))

        contenuto.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(testo("Gaming", 34f, Typeface.BOLD))
            addView(View(this@GamingActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextClock(this@GamingActivity).apply {
                format24Hour = "HH:mm"
                format12Hour = "HH:mm"
                setTextColor(Stile.TESTO)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(typeface, Typeface.BOLD)
            })
        })
        contenuto.addView(testo("Gioca apre la console con le tue impostazioni immagine. Immagine serve a sceglierle.", 15f, Typeface.NORMAL).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            setPadding(0, dp(6), 0, 0)
        })

        // Le schede occupano tutto lo spazio rimasto; il margine lascia posto all'alone quando una è selezionata.
        rigaConsole = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(ALONE), dp(24), dp(ALONE), dp(ALONE))
        }
        contenuto.addView(rigaConsole, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(radice)
    }

    override fun onResume() {
        super.onResume()
        caricaConsole()
    }

    private fun caricaConsole() {
        val avevaFocus = rigaConsole.hasFocus() || rigaConsole.childCount == 0
        rigaConsole.removeAllViews()
        val console = Sorgenti.elenco(this).filter { !it.tv }
        if (console.isEmpty()) {
            rigaConsole.addView(testo("Nessuna console trovata. Accendine una e torna qui.", 18f, Typeface.NORMAL))
            return
        }
        console.forEachIndexed { indice, sorgente ->
            rigaConsole.addView(schedaConsole(sorgente), LinearLayout.LayoutParams(0, MATCH, 1f).apply {
                marginStart = if (indice == 0) 0 else dp(32)
            })
        }
        if (avevaFocus) rigaConsole.getChildAt(0)?.requestFocus()
    }

    private fun schedaConsole(sorgente: Sorgente): View {
        val profilo = profilo(sorgente.nome)
        val raggio = dp(RAGGIO).toFloat()

        // Parte alta colorata: porta, stato e nome della console.
        val copertina = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(profilo.colore, scurisci(profilo.colore))
            ).apply { cornerRadii = floatArrayOf(raggio, raggio, raggio, raggio, 0f, 0f, 0f, 0f) }
            addView(etichetta(sorgente.porta ?: "HDMI"), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(16), dp(16), 0, 0)
            })
            addView(etichetta(if (sorgente.accesa) "● Accesa" else "In riposo"), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(16), dp(16), 0)
            })
            addView(testo(sorgente.nome, 38f, Typeface.BOLD).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }

        val funzioni = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            profilo.funzioni.forEachIndexed { i, funzione ->
                addView(etichetta(funzione), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = if (i == 0) 0 else dp(8) })
            }
        }
        val gioca = pulsante("Gioca") { ProfiliGioco.gioca(this, sorgente) }
        val immagine = pulsante("Immagine") { ImpostazioniGiocoActivity.apri(this, sorgente.nome) }
        val pulsanti = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            addView(gioca, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(immagine, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(12) })
        }
        val dettagli = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(dp(20), dp(16), dp(20), dp(20))
            addView(testo(profilo.caratteristiche, 15f, Typeface.NORMAL).apply { setTextColor(Stile.TESTO_SECONDARIO) })
            if (profilo.funzioni.isNotEmpty()) addView(funzioni)
            addView(pulsanti, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) })
        }

        val scheda = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Stile.vetro(this@GamingActivity, RAGGIO)
            clipToOutline = true
            addView(copertina, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(dettagli, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // Selezionata: viene un po' in avanti, con un alone del colore della console stretto attorno alla scheda.
        val margine = dp(ALONE)
        val alone = AloneNeon(this, profilo.colore, margine.toFloat(), margine.toFloat(), raggio).apply { alpha = 0f }
        val contenitore = FrameLayout(this).apply {
            clipChildren = false
            addView(alone, FrameLayout.LayoutParams(MATCH, MATCH).apply { setMargins(-margine, -margine, -margine, -margine) })
            addView(scheda, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val aggiorna = View.OnFocusChangeListener { _, _ ->
            contenitore.post {
                val attiva = contenitore.hasFocus()
                val scala = if (attiva) 1.03f else 1f
                contenitore.translationZ = if (attiva) dp(10).toFloat() else 0f
                contenitore.animate().scaleX(scala).scaleY(scala).setDuration(180).start()
                alone.animate().alpha(if (attiva) 1f else 0f).setDuration(220).start()
            }
        }
        gioca.onFocusChangeListener = concatena(gioca.onFocusChangeListener, aggiorna)
        immagine.onFocusChangeListener = concatena(immagine.onFocusChangeListener, aggiorna)
        return contenitore
    }

    /** Pulsante di vetro che diventa bianco quando è selezionato. */
    private fun pulsante(etichetta: String, azione: () -> Unit) = testo(etichetta, 16f, Typeface.BOLD).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = Stile.vetro(this@GamingActivity, 24, forte = true)
        onFocusChangeListener = View.OnFocusChangeListener { _, focus ->
            background = if (focus) Stile.pieno(this@GamingActivity, 24, Color.WHITE) else Stile.vetro(this@GamingActivity, 24, forte = true)
            setTextColor(if (focus) Stile.SCURO else Stile.TESTO)
        }
        setOnClickListener { azione() }
    }

    private fun concatena(primo: View.OnFocusChangeListener?, secondo: View.OnFocusChangeListener) =
        View.OnFocusChangeListener { vista, focus ->
            primo?.onFocusChange(vista, focus)
            secondo.onFocusChange(vista, focus)
        }

    private fun profilo(nome: String): Profilo {
        val n = nome.lowercase()
        return when {
            "playstation" in n || n.startsWith("ps") ->
                Profilo(Color.rgb(0, 112, 209), "4K · 120 Hz · HDR", listOf("VRR", "ALLM", "4K 120"))
            "xbox" in n ->
                Profilo(Color.rgb(16, 124, 16), "4K · 120 Hz · HDR", listOf("VRR", "ALLM", "4K 120"))
            "switch" in n || "nintendo" in n ->
                Profilo(Color.rgb(230, 0, 18), "Fino a 4K 60 Hz · HDR", listOf("4K 60", "HDR"))
            else ->
                Profilo(Color.rgb(110, 50, 220), "Ingresso HDMI", emptyList())
        }
    }

    /** Piccola etichetta di vetro, per porta, stato e funzioni. */
    private fun etichetta(contenuto: String) = testo(contenuto, 12f, Typeface.BOLD).apply {
        background = Stile.vetro(this@GamingActivity, 10, forte = true)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun scurisci(colore: Int) = Color.rgb(Color.red(colore) * 35 / 100, Color.green(colore) * 35 / 100, Color.blue(colore) * 35 / 100)

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
        private const val RAGGIO = 24
        private const val ALONE = 12
    }
}
