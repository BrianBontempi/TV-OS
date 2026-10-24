package it.brian.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
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

/** Sfondo "Aurora" del mockup: quattro macchie di colore sfumate su un viola scuro (o sui colori dati). */
class SfondoAurora(
    context: Context,
    private val base: Int = Color.rgb(26, 18, 64),
    private val colori: IntArray = intArrayOf(
        Color.rgb(123, 63, 228),
        Color.rgb(31, 162, 255),
        Color.rgb(255, 79, 163),
        Color.rgb(18, 216, 192)
    )
) : View(context) {

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

/**
 * Dietro un'app selezionata: un'ombra scura spostata in basso, che la fa sembrare sollevata,
 * e sopra un alone "neon" del colore principale dell'app.
 * La vista è più grande del riquadro di [margine] per lato (e [margineSotto] in basso), così le sfumature hanno spazio.
 */
class AloneNeon(
    context: Context,
    colore: Int,
    private val margine: Float,
    private val margineSotto: Float,
    private val raggio: Float
) : View(context) {

    private val neon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, Color.red(colore), Color.green(colore), Color.blue(colore))
        maskFilter = BlurMaskFilter(margine * 0.55f, BlurMaskFilter.Blur.NORMAL)
    }
    private val ombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 0, 0)
        maskFilter = BlurMaskFilter(margine * 0.6f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        // Le sfumature richiedono il disegno software.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sinistra = margine
        val sopra = margine
        val destra = width - margine
        val sotto = height - margineSotto
        val spostamento = margineSotto * 0.75f
        val rientro = margine * 0.15f
        canvas.drawRoundRect(sinistra + rientro, sopra + spostamento, destra - rientro, sotto + spostamento, raggio, raggio, ombra)
        val fuori = margine * 0.2f
        canvas.drawRoundRect(sinistra - fuori, sopra - fuori, destra + fuori, sotto + fuori, raggio + fuori, raggio + fuori, neon)
    }
}

/**
 * Il banner dell'app ritagliato con angoli arrotondati e antialiasing, senza fondo dietro:
 * niente bordini scuri né angoli seghettati.
 */
class RiquadroArrotondato(
    context: Context,
    private val banner: Drawable?,
    private val icona: Drawable,
    private val raggio: Float
) : View(context) {

    private val pennello = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var immagine: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, vecchiaW: Int, vecchiaH: Int) {
        super.onSizeChanged(w, h, vecchiaW, vecchiaH)
        if (w <= 0 || h <= 0) return
        immagine?.recycle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tela = Canvas(bitmap)
        if (banner != null) {
            val copia = banner.constantState?.newDrawable(resources)?.mutate() ?: banner
            copia.setBounds(0, 0, w, h)
            copia.draw(tela)
        } else {
            // App senza banner: vetro chiaro con l'icona al centro.
            tela.drawColor(Color.argb(46, 255, 255, 255))
            val lato = (h * 0.55f).toInt()
            val copia = icona.constantState?.newDrawable(resources)?.mutate() ?: icona
            copia.setBounds((w - lato) / 2, (h - lato) / 2, (w + lato) / 2, (h + lato) / 2)
            copia.draw(tela)
        }
        immagine = bitmap
        pennello.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (immagine == null) return
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), raggio, raggio, pennello)
    }
}

/** Riflesso di luce dall'alto sull'app selezionata, come una superficie lucida. */
class Lucentezza(context: Context, private val raggio: Float) : View(context) {

    private val pennello = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, vecchiaW: Int, vecchiaH: Int) {
        super.onSizeChanged(w, h, vecchiaW, vecchiaH)
        pennello.shader = LinearGradient(
            0f, 0f, 0f, h * 0.6f,
            Color.argb(56, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), raggio, raggio, pennello)
    }
}

/** Trova il colore che rappresenta un'app guardando il suo banner. */
object ColoreApp {

    fun principale(disegno: Drawable): Int {
        val copia = disegno.constantState?.newDrawable()?.mutate() ?: return Color.BLACK
        val bitmap = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888)
        copia.setBounds(0, 0, bitmap.width, bitmap.height)
        copia.draw(Canvas(bitmap))
        val pixel = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixel, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        // Colori vivi raggruppati per tinta; se non ce ne sono abbastanza, vale il colore medio (es. nero per Apple TV).
        val pesi = FloatArray(12)
        val somme = Array(12) { LongArray(4) }
        val media = LongArray(4)
        val hsv = FloatArray(3)
        for (p in pixel) {
            if (Color.alpha(p) < 128) continue
            media[0] += Color.red(p).toLong(); media[1] += Color.green(p).toLong(); media[2] += Color.blue(p).toLong(); media[3]++
            Color.colorToHSV(p, hsv)
            if (hsv[1] > 0.45f && hsv[2] > 0.35f) {
                val gruppo = (hsv[0] / 30f).toInt().coerceIn(0, 11)
                pesi[gruppo] += hsv[1] * hsv[2]
                somme[gruppo][0] += Color.red(p).toLong(); somme[gruppo][1] += Color.green(p).toLong()
                somme[gruppo][2] += Color.blue(p).toLong(); somme[gruppo][3]++
            }
        }
        if (media[3] == 0L) return Color.BLACK
        val migliore = pesi.indices.maxByOrNull { pesi[it] } ?: 0
        val s = somme[migliore]
        return if (s[3] >= media[3] / 20 && s[3] > 0) {
            Color.rgb((s[0] / s[3]).toInt(), (s[1] / s[3]).toInt(), (s[2] / s[3]).toInt())
        } else {
            Color.rgb((media[0] / media[3]).toInt(), (media[1] / media[3]).toInt(), (media[2] / media[3]).toInt())
        }
    }
}
