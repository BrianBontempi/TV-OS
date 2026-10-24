package it.brian.tvhome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View

/** Colori e forme del design v3: vetro chiaro su sfondo aurora. */
object Stile {
    val TESTO = Color.WHITE
    val TESTO_SECONDARIO = Color.argb(217, 255, 255, 255)
    val SCURO = Color.rgb(17, 19, 24)

    fun dp(context: Context, valore: Int) = (valore * context.resources.displayMetrics.density).toInt()

    /** Pannello "liquid glass": bianco semitrasparente con bordo chiaro. */
    fun vetro(context: Context, raggio: Int, forte: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(context, raggio).toFloat()
        setColor(if (forte) Color.argb(51, 255, 255, 255) else Color.argb(33, 255, 255, 255))
        setStroke(dp(context, 1), if (forte) Color.argb(115, 255, 255, 255) else Color.argb(71, 255, 255, 255))
    }

    /** Vetro con il bordo bianco pieno, per l'elemento selezionato dal telecomando. */
    fun vetroSelezionato(context: Context, raggio: Int) = GradientDrawable().apply {
        cornerRadius = dp(context, raggio).toFloat()
        setColor(Color.argb(64, 255, 255, 255))
        setStroke(dp(context, 3), Color.WHITE)
    }

    fun pieno(context: Context, raggio: Int, colore: Int) = GradientDrawable().apply {
        cornerRadius = dp(context, raggio).toFloat()
        setColor(colore)
    }
}

/** Sfondo "Aurora" del mockup: quattro macchie di colore sfumate su un viola scuro. */
class SfondoAurora(context: Context) : View(context) {

    private val base = Color.rgb(26, 18, 64)
    private val colori = intArrayOf(
        Color.rgb(123, 63, 228),
        Color.rgb(31, 162, 255),
        Color.rgb(255, 79, 163),
        Color.rgb(18, 216, 192)
    )
    // Centro (x, y) e raggio in proporzione allo schermo.
    private val macchie = arrayOf(
        floatArrayOf(0.12f, 0.08f, 0.55f),
        floatArrayOf(0.88f, 0.15f, 0.52f),
        floatArrayOf(0.48f, 1.05f, 0.55f),
        floatArrayOf(0.80f, 0.95f, 0.42f)
    )
    private val pennello = Paint(Paint.ANTI_ALIAS_FLAG)
    private val velo = Color.argb(46, 0, 0, 0)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(base)
        for (i in macchie.indices) {
            val (x, y, r) = macchie[i].let { Triple(it[0] * w, it[1] * h, it[2] * w) }
            val colore = colori[i]
            pennello.shader = RadialGradient(
                x, y, r,
                intArrayOf(Color.argb(217, Color.red(colore), Color.green(colore), Color.blue(colore)), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, r, pennello)
        }
        canvas.drawColor(velo)
    }
}
