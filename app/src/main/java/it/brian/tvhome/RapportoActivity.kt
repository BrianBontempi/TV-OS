package it.brian.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.max

/**
 * Rapporto a tutto schermo, da fotografare una pagina alla volta:
 * freccia giù o destra = pagina dopo, freccia su o sinistra = pagina prima.
 */
class RapportoActivity : Activity() {

    private lateinit var scorrimento: ScrollView
    private lateinit var testo: TextView
    private lateinit var pagina: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titolo = intent.getStringExtra(TITOLO).orEmpty()
        val contenuto = intent.getStringExtra(TESTO).orEmpty()

        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }
        val intestazione = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        intestazione.addView(etichetta("$titolo  ·  frecce su/giù per cambiare pagina", Color.rgb(255, 214, 10)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pagina = etichetta("", Color.rgb(255, 214, 10))
        intestazione.addView(pagina)
        radice.addView(intestazione)

        testo = TextView(this).apply {
            text = contenuto
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.05f)
        }
        scorrimento = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFocusable = false
            addView(testo)
            setOnScrollChangeListener { _, _, _, _, _ -> aggiornaPagina() }
        }
        radice.addView(scorrimento, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })

        setContentView(radice, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scorrimento.post { aggiornaPagina() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
            vaiAPagina(paginaAttuale() + 1); true
        }
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
            vaiAPagina(paginaAttuale() - 1); true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    /** Ogni pagina ripete l'ultima riga della precedente, così nelle foto non si perde niente. */
    private fun passo() = max(1, scorrimento.height - testo.lineHeight)

    private fun totalePagine(): Int {
        val oltre = testo.height - scorrimento.height
        return if (oltre <= 0) 1 else 1 + ceil(oltre / passo().toDouble()).toInt()
    }

    private fun paginaAttuale(): Int {
        val fine = testo.height - scorrimento.height
        if (fine > 0 && scorrimento.scrollY >= fine) return totalePagine()
        return 1 + scorrimento.scrollY / passo()
    }

    private fun vaiAPagina(numero: Int) {
        val n = numero.coerceIn(1, totalePagine())
        scorrimento.scrollTo(0, (n - 1) * passo())
        aggiornaPagina()
    }

    private fun aggiornaPagina() {
        pagina.text = "Pagina ${paginaAttuale()} di ${totalePagine()}"
    }

    private fun etichetta(contenuto: String, colore: Int) = TextView(this).apply {
        text = contenuto
        setTextColor(colore)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private fun dp(valore: Int) = Stile.dp(this, valore)

    companion object {
        private const val TITOLO = "titolo"
        private const val TESTO = "testo"

        fun apri(context: Context, titolo: String, testo: String) {
            context.startActivity(Intent(context, RapportoActivity::class.java)
                .putExtra(TITOLO, titolo)
                .putExtra(TESTO, testo))
        }
    }
}
