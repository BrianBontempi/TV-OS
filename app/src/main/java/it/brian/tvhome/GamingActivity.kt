package it.brian.tvhome

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/** Sezione Gaming: una scheda per ogni console riconosciuta sulle HDMI e le impostazioni Sony consigliate. */
class GamingActivity : Activity() {

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
        contenuto.addView(testo("Scegli una console: si apre il suo ingresso HDMI.", 15f, Typeface.NORMAL).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            setPadding(0, dp(6), 0, 0)
        })

        rigaConsole = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(28), 0, 0)
        }
        contenuto.addView(rigaConsole, LinearLayout.LayoutParams(MATCH, WRAP))

        contenuto.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        contenuto.addView(pannelloConsigli(), LinearLayout.LayoutParams(MATCH, WRAP))

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
            rigaConsole.addView(schedaConsole(sorgente), LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                marginStart = if (indice == 0) 0 else dp(28)
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
                setMargins(dp(14), dp(14), 0, 0)
            })
            addView(etichetta(if (sorgente.accesa) "● Accesa" else "In riposo"), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(14), dp(14), 0)
            })
            addView(testo(sorgente.nome, 28f, Typeface.BOLD).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }

        val funzioni = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            profilo.funzioni.forEachIndexed { i, funzione ->
                addView(etichetta(funzione), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = if (i == 0) 0 else dp(8) })
            }
        }
        val gioca = testo("Gioca", 16f, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = Stile.vetro(this@GamingActivity, 22, forte = true)
        }
        val dettagli = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            addView(testo(profilo.caratteristiche, 14f, Typeface.NORMAL).apply { setTextColor(Stile.TESTO_SECONDARIO) })
            if (profilo.funzioni.isNotEmpty()) addView(funzioni)
            addView(gioca, LinearLayout.LayoutParams(MATCH, dp(44)).apply { topMargin = dp(18) })
        }

        val scheda = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Stile.vetro(this@GamingActivity, RAGGIO)
            clipToOutline = true
            addView(copertina, LinearLayout.LayoutParams(MATCH, dp(150)))
            addView(dettagli, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // Selezionata: viene in avanti con ombra e alone del colore della console.
        val margineAlone = dp(22)
        val margineSotto = dp(18)
        val alone = AloneNeon(this, profilo.colore, margineAlone.toFloat(), (margineAlone + margineSotto).toFloat(), raggio).apply { alpha = 0f }
        return FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            clipChildren = false
            addView(alone, FrameLayout.LayoutParams(MATCH, MATCH).apply {
                setMargins(-margineAlone, -margineAlone, -margineAlone, -(margineAlone + margineSotto))
            })
            addView(scheda, FrameLayout.LayoutParams(MATCH, WRAP))
            setOnFocusChangeListener { vista, focus ->
                val scala = if (focus) 1.05f else 1f
                vista.translationZ = if (focus) dp(10).toFloat() else 0f
                vista.animate().scaleX(scala).scaleY(scala).translationY(if (focus) -dp(4).toFloat() else 0f).setDuration(180).start()
                alone.animate().alpha(if (focus) 1f else 0f).setDuration(220).start()
                gioca.background = if (focus) Stile.pieno(this@GamingActivity, 22, Color.WHITE) else Stile.vetro(this@GamingActivity, 22, forte = true)
                gioca.setTextColor(if (focus) Stile.SCURO else Stile.TESTO)
            }
            setOnClickListener { Sorgenti.apri(this@GamingActivity, sorgente) }
        }
    }

    /**
     * Le impostazioni Sony restano quelle di Sony: qui ci sono solo i valori consigliati e la scorciatoia per arrivarci.
     */
    private fun pannelloConsigli(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Stile.vetro(this@GamingActivity, 20)
        setPadding(dp(24), dp(18), dp(24), dp(18))
        addView(testo("Impostazioni consigliate", 18f, Typeface.BOLD))
        addView(testo("Le impostazioni immagine valgono per l'ingresso in uso: regolale con la console accesa.", 12f, Typeface.NORMAL).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            setPadding(0, dp(4), 0, dp(10))
        })
        addView(rigaConsiglio(
            "Formato segnale HDMI",
            "Formato avanzato con VRR su HDMI 3 e HDMI 4 (PlayStation, Xbox)",
            "Impostazioni › Canali e ingressi › Ingressi esterni › Formato segnale HDMI/VRR"
        ) { avvia(Intent(Settings.ACTION_SETTINGS), "Impostazioni") })
        addView(rigaConsiglio(
            "Modalità immagine",
            "Gioco",
            "Immagine Sony › Modalità immagine"
        ) { avvia(Intent().setComponent(ComponentName(Sonda.IMPOSTAZIONI_SONY, Sonda.IMMAGINE_SONY)), "Immagine Sony") })
    }

    private fun rigaConsiglio(titolo: String, valore: String, percorso: String, azione: () -> Unit): View {
        val normale = Stile.pieno(this, 14, Color.TRANSPARENT)
        val selezionata = Stile.vetroSelezionato(this, 14)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = normale
            isFocusable = true
            isClickable = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(LinearLayout(this@GamingActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(this@GamingActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(testo(titolo, 15f, Typeface.BOLD))
                    addView(testo(valore, 15f, Typeface.NORMAL).apply { setPadding(dp(12), 0, 0, 0) })
                })
                addView(testo(percorso, 12f, Typeface.NORMAL).apply {
                    setTextColor(Stile.TESTO_SECONDARIO)
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(testo("Apri ›", 14f, Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) }
            setOnFocusChangeListener { vista, focus -> vista.background = if (focus) selezionata else normale }
            setOnClickListener { azione() }
        }
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
        private const val RAGGIO = 24
    }
}
