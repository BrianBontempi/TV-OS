package it.brian.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.CaptioningManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Centro di controllo, come nel mockup v3: un pannello di vetro a destra, sopra quello che si sta guardando.
 * Si apre tenendo premuto HOME. Volume, uscita audio, modalità immagine e spegnimento passano dal Controllo IP Sony;
 * sottotitoli e Bluetooth aprono le impostazioni della TV.
 */
class CentroControlloActivity : SchermataTv() {

    private val lavoro = Executors.newSingleThreadExecutor()

    private lateinit var corpo: FrameLayout
    private lateinit var griglia: View
    private lateinit var volumeValore: TextView
    private lateinit var volumeBarra: BarraVolume
    private val riquadri = mutableMapOf<String, TextView>()

    private var volume: ControlloIp.Volume? = null
    private var immagine: List<ControlloIp.Voce>? = null
    private var audio: List<ControlloIp.Voce>? = null
    private var sorgenteAttuale: String? = null
    private var inElenco = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val radice = FrameLayout(this)
        // Velo scuro solo verso destra, così il pannello si legge anche sopra un gioco luminoso.
        radice.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, Color.argb(90, 0, 0, 0), Color.argb(170, 0, 0, 0)))
        }, FrameLayout.LayoutParams(MATCH, MATCH))

        val pannello = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // I pulsanti in basso si ingrandiscono un po' quando sono selezionati: non vanno tagliati.
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.argb(222, 30, 28, 48))
                setStroke(dp(1), Color.argb(82, 255, 255, 255))
            }
            setPadding(dp(22), dp(20), dp(22), dp(20))
        }
        val larghezza = resources.displayMetrics.widthPixels / 3
        radice.addView(pannello, FrameLayout.LayoutParams(larghezza, MATCH, Gravity.END).apply {
            setMargins(0, dp(24), dp(24), dp(24))
        })

        pannello.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(testo("Centro di controllo", 19f, Typeface.BOLD))
            addView(View(this@CentroControlloActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextClock(this@CentroControlloActivity).apply {
                format24Hour = "HH:mm"
                format12Hour = "HH:mm"
                setTextColor(Stile.TESTO_SECONDARIO)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
        })

        corpo = FrameLayout(this)
        pannello.addView(corpo, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(14) })
        griglia = creaGriglia()
        corpo.addView(griglia, FrameLayout.LayoutParams(MATCH, MATCH))

        val spegni = PulsanteSpegni(this).apply { setOnClickListener { spegniTv() } }
        pannello.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(Stile.pillola(this@CentroControlloActivity, "Tutte le impostazioni") {
                apri(Intent(Settings.ACTION_SETTINGS))
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            addView(spegni, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(12) })
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })

        setContentView(radice)
        griglia.findViewWithTag<View>(VOLUME)?.requestFocus()
        aggiorna()
    }

    override fun onDestroy() {
        // I passi di volume già premuti arrivano comunque alla TV.
        lavoro.shutdown()
        super.onDestroy()
    }

    private fun esegui(blocco: () -> Unit) {
        try {
            lavoro.execute(blocco)
        } catch (e: RejectedExecutionException) {
            // Pannello già chiuso.
        }
    }

    @Deprecated("Il tasto Indietro chiude prima l'elenco aperto, poi il pannello.")
    override fun onBackPressed() {
        if (inElenco) mostraGriglia(null) else super.onBackPressed()
    }

    // ---- Griglia principale ----

    private fun creaGriglia(): View {
        val contenuto = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        volumeValore = testo("…", 14f, Typeface.BOLD).apply { setTextColor(Stile.TESTO_SECONDARIO) }
        volumeBarra = BarraVolume(this)
        val etichettaVolume = testo("Volume", 14f, Typeface.BOLD)
        val schedaVolume = LinearLayout(this).apply {
            tag = VOLUME
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            background = Stile.vetro(this@CentroControlloActivity, 20)
            setPadding(dp(16), dp(12), dp(16), dp(14))
            addView(LinearLayout(this@CentroControlloActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(etichettaVolume, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(volumeValore)
            })
            addView(volumeBarra, LinearLayout.LayoutParams(MATCH, dp(10)).apply { topMargin = dp(10) })
            setOnFocusChangeListener { vista, focus ->
                vista.background = if (focus) Stile.pieno(this@CentroControlloActivity, 20, Color.WHITE) else Stile.vetro(this@CentroControlloActivity, 20)
                val colore = if (focus) Stile.SCURO else Stile.TESTO
                etichettaVolume.setTextColor(colore)
                volumeValore.setTextColor(if (focus) Stile.SCURO else Stile.TESTO_SECONDARIO)
                volumeBarra.scuro = focus
            }
            setOnKeyListener { _, codice, evento ->
                if (evento.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (codice) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { cambiaVolume(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cambiaVolume(+1); true }
                    else -> false
                }
            }
            setOnClickListener { cambiaMuto() }
        }
        contenuto.addView(schedaVolume)
        contenuto.addView(testo("← → per il volume, OK per il muto", 11f, Typeface.NORMAL).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            setPadding(dp(4), dp(6), 0, dp(10))
        })

        val voci = listOf(
            Triple(SORGENTE, "Sorgente") { scegliSorgente() },
            Triple(IMMAGINE, "Modalità immagine") { scegliModalita() },
            Triple(USCITA, "Uscita audio") { scegliUscita() },
            Triple(TIMER, "Timer spegnimento") { scegliTimer() },
            Triple(SOTTOTITOLI, "Sottotitoli") { apri(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) },
            Triple(BLUETOOTH, "Bluetooth") { apri(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        )
        voci.chunked(2).forEachIndexed { indice, coppia ->
            contenuto.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                coppia.forEachIndexed { i, (chiave, etichetta, azione) ->
                    addView(riquadro(chiave, etichetta, azione), LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                        marginStart = if (i == 0) 0 else dp(10)
                    })
                }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = if (indice == 0) 0 else dp(10) })
        }
        riquadri[TIMER]?.text = TimerSpegnimento.minutiRimasti()?.let { "Fra $it min" } ?: "Disattivato"
        riquadri[SOTTOTITOLI]?.text = if (sottotitoliAttivi()) "Attivi" else "Disattivati"
        riquadri[BLUETOOTH]?.text = "Dispositivi"

        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(contenuto)
        }
    }

    /** Un riquadro della griglia: etichetta piccola sopra, valore sotto; selezionato diventa bianco. */
    private fun riquadro(chiave: String, etichetta: String, azione: () -> Unit): View {
        val titolo = testo(etichetta, 11f, Typeface.BOLD).apply { setTextColor(Stile.TESTO_SECONDARIO) }
        val valore = testo("…", 15f, Typeface.BOLD).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        riquadri[chiave] = valore
        return LinearLayout(this).apply {
            tag = chiave
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = Stile.vetro(this@CentroControlloActivity, 18)
            setPadding(dp(14), 0, dp(10), 0)
            addView(titolo)
            addView(valore, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
            setOnFocusChangeListener { vista, focus ->
                vista.background = if (focus) Stile.pieno(this@CentroControlloActivity, 18, Color.WHITE) else Stile.vetro(this@CentroControlloActivity, 18)
                titolo.setTextColor(if (focus) Color.argb(170, 17, 19, 24) else Stile.TESTO_SECONDARIO)
                valore.setTextColor(if (focus) Stile.SCURO else Stile.TESTO)
            }
            setOnClickListener { azione() }
        }
    }

    /** Legge dalla TV volume, immagine, audio e cosa si sta guardando, poi aggiorna i riquadri. */
    private fun aggiorna() {
        esegui {
            val v = ControlloIp.leggiVolume()
            val i = ControlloIp.leggiImmagine()
            val a = ControlloIp.leggiAudio()
            val s = nomeSorgente(ControlloIp.inVisione())
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                volume = v
                immagine = i
                audio = a
                sorgenteAttuale = s
                mostraVolume()
                riquadri[SORGENTE]?.text = s ?: "Scegli"
                riquadri[IMMAGINE]?.text = voce(i, "pictureMode")?.let { nomeModalita(it.valore) } ?: "Impostazioni Sony"
                riquadri[USCITA]?.text = voce(a, "outputTerminal")?.let { nomeUscita(it.valore) } ?: "Impostazioni Sony"
            }
        }
    }

    // ---- Volume ----

    private fun mostraVolume() {
        val v = volume
        if (v != null) {
            volumeValore.text = if (v.muto) "Muto" else v.livello.toString()
            volumeBarra.frazione = if (v.muto) 0f else v.livello.toFloat() / v.massimo.coerceAtLeast(1)
        } else {
            // La TV non ha risposto: si usa il volume di Android.
            val gestore = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val livello = gestore.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeValore.text = livello.toString()
            volumeBarra.frazione = livello.toFloat() / gestore.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        }
    }

    private fun cambiaVolume(passo: Int) {
        Suoni.sposta()
        val v = volume
        if (v == null) {
            val gestore = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            gestore.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (passo > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            mostraVolume()
            return
        }
        // Il numero cambia subito; la TV riceve i passi in ordine, uno alla volta.
        volume = v.copy(livello = (v.livello + passo).coerceIn(0, v.massimo), muto = false)
        mostraVolume()
        esegui {
            if (v.muto) ControlloIp.impostaMuto(false)
            ControlloIp.cambiaVolume(v.target, if (passo > 0) "+$passo" else "$passo")
        }
    }

    private fun cambiaMuto() {
        val v = volume ?: return
        volume = v.copy(muto = !v.muto)
        mostraVolume()
        esegui { ControlloIp.impostaMuto(!v.muto) }
    }

    // ---- Scelte ----

    private fun scegliSorgente() {
        val sorgenti = Sorgenti.elenco(this)
        if (sorgenti.isEmpty()) return avviso("Nessuna sorgente trovata")
        mostraElenco("Sorgente", sorgenti.map { it.nome to it.nome }, sorgenteAttuale) { nome ->
            sorgenti.firstOrNull { it.nome == nome }?.let { Sorgenti.apri(this, it) }
            finish()
        }
    }

    private fun scegliModalita() {
        val modo = voce(immagine, "pictureMode")
        if (modo == null || modo.scelte.isEmpty()) return apri(Intent().setComponent(ComponentName(Sonda.IMPOSTAZIONI_SONY, Sonda.IMMAGINE_SONY)))
        mostraElenco("Modalità immagine", modo.scelte.map { it to nomeModalita(it) }, modo.valore) { scelta ->
            riquadri[IMMAGINE]?.text = nomeModalita(scelta)
            esegui {
                val problema = ControlloIp.impostaImmagine(mapOf("pictureMode" to scelta))
                runOnUiThread { if (problema != null) avviso("La TV non ha cambiato la modalità: $problema") }
                aggiorna()
            }
        }
    }

    private fun scegliUscita() {
        val uscita = voce(audio, "outputTerminal")
        if (uscita == null || uscita.scelte.isEmpty()) return apri(Intent(Settings.ACTION_SOUND_SETTINGS))
        mostraElenco("Uscita audio", uscita.scelte.map { it to nomeUscita(it) }, uscita.valore) { scelta ->
            riquadri[USCITA]?.text = nomeUscita(scelta)
            esegui {
                val problema = ControlloIp.impostaAudio(mapOf("outputTerminal" to scelta))
                runOnUiThread { if (problema != null) avviso("La TV non ha cambiato l'uscita audio: $problema") }
                aggiorna()
            }
        }
    }

    private fun scegliTimer() {
        val attuale = TimerSpegnimento.minutiRimasti()?.let { rimasti ->
            TimerSpegnimento.SCELTE.filter { it > 0 }.minByOrNull { kotlin.math.abs(it - rimasti) }
        } ?: 0
        mostraElenco("Timer spegnimento", TimerSpegnimento.SCELTE.map { it.toString() to TimerSpegnimento.descrivi(it) }, attuale.toString()) { scelta ->
            val minuti = scelta.toInt()
            TimerSpegnimento.imposta(this, minuti)
            riquadri[TIMER]?.text = if (minuti > 0) "Fra $minuti min" else "Disattivato"
        }
    }

    /** Sostituisce la griglia con un elenco di scelte; Indietro torna alla griglia. */
    private fun mostraElenco(titolo: String, scelte: List<Pair<String, String>>, attuale: String?, scegli: (String) -> Unit) {
        val chiave = (corpo.findFocus()?.tag as? String)
        val elenco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        elenco.addView(testo("‹  $titolo", 15f, Typeface.BOLD).apply { setPadding(dp(4), 0, 0, dp(10)) })
        var daSelezionare: View? = null
        for ((valore, nome) in scelte) {
            val scelto = valore == attuale
            val segno = testo(if (scelto) "✓" else "", 16f, Typeface.BOLD)
            val etichetta = testo(nome, 15f, Typeface.BOLD)
            val riga = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                background = Stile.vetro(this@CentroControlloActivity, 16)
                setPadding(dp(16), 0, dp(16), 0)
                addView(etichetta, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(segno)
                setOnFocusChangeListener { vista, focus ->
                    vista.background = if (focus) Stile.pieno(this@CentroControlloActivity, 16, Color.WHITE) else Stile.vetro(this@CentroControlloActivity, 16)
                    etichetta.setTextColor(if (focus) Stile.SCURO else Stile.TESTO)
                    segno.setTextColor(if (focus) Stile.SCURO else Stile.TESTO)
                }
                setOnClickListener {
                    scegli(valore)
                    if (!isFinishing) mostraGriglia(chiave)
                }
            }
            elenco.addView(riga, LinearLayout.LayoutParams(MATCH, dp(48)).apply { bottomMargin = dp(8) })
            if (scelto || daSelezionare == null) daSelezionare = riga.takeIf { scelto } ?: daSelezionare ?: riga
        }
        corpo.removeAllViews()
        corpo.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(elenco)
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        inElenco = true
        daSelezionare?.requestFocus()
    }

    private fun mostraGriglia(chiave: String?) {
        corpo.removeAllViews()
        corpo.addView(griglia, FrameLayout.LayoutParams(MATCH, MATCH))
        inElenco = false
        griglia.findViewWithTag<View>(chiave ?: VOLUME)?.requestFocus()
    }

    // ---- Azioni ----

    private fun spegniTv() {
        esegui {
            val problema = ControlloIp.spegniTv()
            runOnUiThread {
                if (problema != null) avviso("La TV non si è spenta: $problema") else finish()
            }
        }
    }

    private fun apri(intent: Intent) {
        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            avviso("Questa pagina non si apre su questa TV")
        }
    }

    // ---- Nomi ----

    private fun voce(voci: List<ControlloIp.Voce>?, target: String) = voci?.firstOrNull { it.target == target && it.disponibile }

    /** "extInput:hdmi?port=3" diventa il nome della console su HDMI 3. */
    private fun nomeSorgente(uri: String?): String? {
        if (uri == null) return null
        if (uri.startsWith("tv:")) return "TV"
        val porta = Regex("port=(\\d)").find(uri)?.groupValues?.get(1) ?: return null
        return Sorgenti.elenco(this).firstOrNull { it.porta == "HDMI $porta" }?.nome ?: "HDMI $porta"
    }

    private fun nomeModalita(valore: String) = when (valore) {
        "vivid" -> "Vivido"
        "standard" -> "Standard"
        "cinema" -> "Cinema"
        "game" -> "Gioco"
        "graphics" -> "Grafica"
        "photo" -> "Foto"
        "custom" -> "Personalizzato"
        else -> valore
    }

    private fun nomeUscita(valore: String) = when (valore) {
        "speaker" -> "Altoparlanti TV"
        "speaker_hdmi" -> "TV e impianto audio"
        "hdmi" -> "Impianto audio (HDMI)"
        "audioSystem" -> "Impianto audio"
        else -> valore
    }

    private fun sottotitoliAttivi() =
        (getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager)?.isEnabled == true

    private fun avviso(messaggio: String) = Toast.makeText(this, messaggio, Toast.LENGTH_LONG).show()

    private fun testo(contenuto: String, dimensione: Float, stile: Int) = TextView(this).apply {
        text = contenuto
        setTextColor(Stile.TESTO)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, dimensione)
        setTypeface(Typeface.create("sans-serif", stile))
    }

    private fun dp(valore: Int) = Stile.dp(this, valore)

    /** La barra del volume: binario chiaro con la parte piena bianca (scura quando la scheda è selezionata). */
    private class BarraVolume(context: Context) : View(context) {
        private val pennello = Paint(Paint.ANTI_ALIAS_FLAG)
        private val forma = RectF()
        var frazione = 0f
            set(valore) { field = valore.coerceIn(0f, 1f); invalidate() }
        var scuro = false
            set(valore) { field = valore; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val raggio = height / 2f
            pennello.color = if (scuro) Color.argb(46, 17, 19, 24) else Color.argb(56, 255, 255, 255)
            forma.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(forma, raggio, raggio, pennello)
            if (frazione <= 0f) return
            pennello.color = if (scuro) Stile.SCURO else Color.WHITE
            forma.set(0f, 0f, (width * frazione).coerceAtLeast(height.toFloat()), height.toFloat())
            canvas.drawRoundRect(forma, raggio, raggio, pennello)
        }
    }

    /** Pulsante rotondo col simbolo di accensione; selezionato diventa bianco. */
    private class PulsanteSpegni(context: Context) : View(context) {
        private val tratto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = Stile.dp(context, 2).toFloat()
        }
        private val fondo = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arco = RectF()

        init {
            isFocusable = true
            isClickable = true
            contentDescription = "Spegni la TV"
            setOnFocusChangeListener { _, focus ->
                val scala = if (focus) 1.06f else 1f
                animate().scaleX(scala).scaleY(scala).setDuration(120).start()
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f
            fondo.color = if (isFocused) Color.WHITE else Color.argb(33, 255, 255, 255)
            canvas.drawCircle(cx, cy, r - 1f, fondo)
            tratto.color = if (isFocused) Stile.SCURO else Color.WHITE
            val s = r * 0.42f
            arco.set(cx - s, cy - s, cx + s, cy + s)
            canvas.drawArc(arco, -55f, 290f, false, tratto)
            canvas.drawLine(cx, cy - s * 1.15f, cx, cy - s * 0.15f, tratto)
        }
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val VOLUME = "volume"
        private const val SORGENTE = "sorgente"
        private const val IMMAGINE = "immagine"
        private const val USCITA = "uscita"
        private const val TIMER = "timer"
        private const val SOTTOTITOLI = "sottotitoli"
        private const val BLUETOOTH = "bluetooth"

        fun apri(context: Context) {
            context.startActivity(Intent(context, CentroControlloActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }
}
