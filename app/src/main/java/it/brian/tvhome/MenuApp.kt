package it.brian.tvhome

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Il piccolo menu che compare tenendo premuto OK su un'app: un riquadro di vetro al centro, sopra la Home velata.
 * Indietro lo chiude (lo gestisce chi lo apre, con [chiudi]).
 */
class MenuApp(
    context: Context,
    titolo: String,
    icona: Drawable?,
    nota: String?,
    voci: List<Pair<String, () -> Unit>>,
    private val chiuso: () -> Unit
) : FrameLayout(context) {

    private var primo: View? = null

    init {
        isClickable = true
        setBackgroundColor(Color.argb(150, 0, 0, 0))
        val scheda = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(Color.argb(232, 32, 30, 50))
                setStroke(dp(1), Color.argb(82, 255, 255, 255))
            }
            setPadding(dp(26), dp(22), dp(26), dp(22))
            clipChildren = false
            clipToPadding = false
        }
        if (icona != null) {
            scheda.addView(ImageView(context).apply { setImageDrawable(icona) }, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                bottomMargin = dp(10)
            })
        }
        scheda.addView(testo(titolo, 18f, Typeface.BOLD).apply { gravity = Gravity.CENTER })
        if (nota != null) {
            scheda.addView(testo(nota, 12f, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setTextColor(Stile.TESTO_SECONDARIO)
                setPadding(0, dp(6), 0, 0)
            })
        }
        voci.forEachIndexed { indice, (etichetta, azione) ->
            val pulsante = Stile.pillola(context, etichetta) {
                chiudi()
                azione()
            }
            scheda.addView(pulsante, LinearLayout.LayoutParams(MATCH, dp(46)).apply { topMargin = dp(if (indice == 0) 18 else 10) })
            if (indice == 0) primo = pulsante
        }
        addView(scheda, LayoutParams(dp(300), WRAP, Gravity.CENTER))
    }

    fun mostra(contenitore: ViewGroup) {
        contenitore.addView(this, LayoutParams(MATCH, MATCH))
        alpha = 0f
        animate().alpha(1f).setDuration(140).start()
        primo?.requestFocus()
    }

    fun chiudi() {
        (parent as? ViewGroup)?.removeView(this)
        chiuso()
    }

    private fun testo(contenuto: String, dimensione: Float, stile: Int) = TextView(context).apply {
        text = contenuto
        setTextColor(Stile.TESTO)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensione)
        setTypeface(Typeface.create("sans-serif", stile))
    }

    private fun dp(valore: Int) = Stile.dp(context, valore)

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
