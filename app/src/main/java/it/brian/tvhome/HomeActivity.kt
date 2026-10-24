package it.brian.tvhome

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** La Home vera: saluto, app con i loro banner rettangolari, console riconosciute e scorciatoie Sony. */
class HomeActivity : Activity() {

    private lateinit var saluto: TextView
    private lateinit var data: TextView
    private lateinit var rigaApp: LinearLayout
    private lateinit var rigaSorgenti: LinearLayout
    private lateinit var avvisoHome: View
    private lateinit var scorrimentoApp: HorizontalScrollView
    private var larghezzaApp = 0
    private var primaVisibile = 0
    private var pacchettiMostrati = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val radice = FrameLayout(this)
        radice.addView(SfondoAurora(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(24))
            clipChildren = false
            clipToPadding = false
        }
        radice.addView(contenuto, FrameLayout.LayoutParams(MATCH, MATCH))

        contenuto.addView(barraSuperiore())

        saluto = testo("", 40f, Typeface.BOLD).apply { setPadding(0, dp(56), 0, 0) }
        data = testo("", 15f, Typeface.NORMAL).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            setPadding(0, dp(8), 0, 0)
        }
        contenuto.addView(saluto)
        contenuto.addView(data)
        avvisoHome = pillola("Il tasto HOME porta ancora a Google", "Sistema", Color.rgb(255, 159, 10)) { sistemaTastoHome() }.apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, dp(44)).apply { topMargin = dp(20) }
            visibility = View.GONE
        }
        contenuto.addView(avvisoHome)
        contenuto.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Sempre APP_VISIBILI app intere e centrate, con margini laterali uguali.
        // Le app fuori vista vengono nascoste, così non se ne vede mai un pezzo ai lati.
        val larghezzaPannello = resources.displayMetrics.widthPixels - 2 * dp(48)
        val spazioUtile = larghezzaPannello - 2 * dp(MARGINE_PANNELLO) - (APP_VISIBILI - 1) * dp(SPAZIO_APP)
        larghezzaApp = spazioUtile / APP_VISIBILI
        val avanzo = spazioUtile - larghezzaApp * APP_VISIBILI
        rigaApp = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        scorrimentoApp = object : HorizontalScrollView(this) {
            // Lo scorrimento lo decidiamo noi, un'app intera alla volta.
            override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
        }.apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            setPadding(dp(MARGINE_PANNELLO) + avanzo / 2, dp(34), dp(MARGINE_PANNELLO) + avanzo - avanzo / 2, dp(22))
            addView(rigaApp)
        }
        val pannelloApp = FrameLayout(this).apply {
            background = Stile.vetro(this@HomeActivity, 20)
            clipToOutline = true
            addView(scorrimentoApp)
        }
        contenuto.addView(pannelloApp, LinearLayout.LayoutParams(MATCH, WRAP))

        rigaSorgenti = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(16), 0, 0)
        }
        contenuto.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(rigaSorgenti)
        })

        setContentView(radice)
        caricaApp()
        rigaApp.getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        aggiornaSaluto()
        caricaApp()
        caricaSorgenti()
        controllaTastoHome()
    }

    // La Home non si chiude con Indietro.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK) true else super.onKeyDown(keyCode, event)

    private fun barraSuperiore() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        addView(TextClock(this@HomeActivity).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            setTextColor(Stile.TESTO)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(View(this@HomeActivity), LinearLayout.LayoutParams(0, 1, 1f))
        addView(pillola("Immagine") { apriSony(Sonda.IMMAGINE_SONY, "Immagine Sony") })
        Sonda.schermateSony(this@HomeActivity).firstOrNull { Sonda.nomeBreve(it) == "SoundSetting" }?.let { audio ->
            addView(pillola("Audio") { apriSony(audio, "Audio Sony") })
        }
        addView(pillola("Impostazioni") { avvia(Intent(Settings.ACTION_SETTINGS), "Impostazioni") })
        addView(pillola("Diagnostica") { avvia(Intent(this@HomeActivity, ProvaActivity::class.java), "Diagnostica") })
    }

    private fun servizioAttivo(): Boolean {
        val attivi = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val servizio = ComponentName(this, HomeKeyService::class.java)
        return attivi.split(':').any {
            it.equals(servizio.flattenToString(), true) || it.equals(servizio.flattenToShortString(), true)
        }
    }

    /** Mostra l'avviso se il tasto HOME non può arrivare a noi: servizio spento o intercettazione disattivata. */
    private fun controllaTastoHome() {
        avvisoHome.visibility = if (servizioAttivo() && Registro.intercettaHome(this)) View.GONE else View.VISIBLE
    }

    private fun sistemaTastoHome() {
        Registro.impostaIntercettaHome(this, true)
        if (servizioAttivo()) controllaTastoHome()
        else avvia(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Impostazioni accessibilità")
    }

    private fun aggiornaSaluto() {
        val ora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        saluto.text = when (ora) {
            in 5..12 -> "Buongiorno"
            in 13..17 -> "Buon pomeriggio"
            else -> "Buonasera"
        }
        val oggi = SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(Date())
        data.text = "${oggi.replaceFirstChar { it.uppercase() }} · niente pubblicità, solo le tue cose"
    }

    /** Tutte le app per TV, con le preferite di Brian davanti. Ricostruisce la riga solo se sono cambiate. */
    private fun caricaApp() {
        val pm = packageManager
        val trovate = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedWith(compareBy<ResolveInfo>({ posizione(it.activityInfo.packageName) }, { it.loadLabel(pm).toString().lowercase() }))
        val pacchetti = trovate.map { it.activityInfo.packageName }
        if (pacchetti == pacchettiMostrati) return
        pacchettiMostrati = pacchetti

        val avevaFocus = rigaApp.hasFocus()
        rigaApp.removeAllViews()
        trovate.forEachIndexed { indice, app ->
            rigaApp.addView(riquadroApp(app), LinearLayout.LayoutParams(larghezzaApp, WRAP).apply {
                marginStart = if (indice == 0) 0 else dp(SPAZIO_APP)
            })
        }
        primaVisibile = 0
        scorrimentoApp.scrollTo(0, 0)
        aggiornaVisibili(animare = false)
        if (avevaFocus) rigaApp.getChildAt(0)?.requestFocus()
    }

    private fun caricaSorgenti() {
        val avevaFocus = rigaSorgenti.hasFocus()
        rigaSorgenti.removeAllViews()
        for (sorgente in Sorgenti.elenco(this)) {
            rigaSorgenti.addView(pillola(sorgente.nome, sorgente.porta, if (sorgente.accesa) Color.WHITE else Color.argb(115, 255, 255, 255)) {
                Sorgenti.apri(this, sorgente)
            })
        }
        if (avevaFocus) rigaSorgenti.getChildAt(0)?.requestFocus()
    }

    private fun riquadroApp(app: ResolveInfo): View {
        val pm = packageManager
        val nome = app.loadLabel(pm).toString()
        val banner: Drawable? = app.activityInfo.loadBanner(pm) ?: app.activityInfo.applicationInfo.loadBanner(pm)

        val icona = app.loadIcon(pm)
        val raggio = dp(RAGGIO_APP).toFloat()
        val immagine = RiquadroArrotondato(this, banner, icona, raggio)
        val riflesso = Lucentezza(this, raggio).apply { alpha = 0f }
        // Quando l'app è selezionata compaiono dietro l'ombra e l'alone del suo colore.
        val margineAlone = dp(22)
        val margineSotto = dp(18)
        val alone = AloneNeon(this, ColoreApp.principale(banner ?: icona), margineAlone.toFloat(), (margineAlone + margineSotto).toFloat(), raggio).apply {
            alpha = 0f
        }
        val riquadro = FrameLayout(this).apply {
            clipChildren = false
            addView(alone, FrameLayout.LayoutParams(MATCH, MATCH).apply {
                setMargins(-margineAlone, -margineAlone, -margineAlone, -(margineAlone + margineSotto))
            })
            addView(immagine, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(riflesso, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val etichetta = testo(nome, 11f, Typeface.NORMAL).apply {
            setTextColor(Stile.TESTO_SECONDARIO)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            clipChildren = false
            addView(riquadro, LinearLayout.LayoutParams(larghezzaApp, larghezzaApp * 9 / 16))
            addView(etichetta, LinearLayout.LayoutParams(larghezzaApp, WRAP))
            setOnFocusChangeListener { vista, focus ->
                if (focus) mostraApp(rigaApp.indexOfChild(vista))
                // L'app selezionata viene in avanti: più grande, un po' più in alto e disegnata sopra le vicine.
                val scala = if (focus) 1.14f else 1f
                vista.translationZ = if (focus) dp(10).toFloat() else 0f
                vista.animate().scaleX(scala).scaleY(scala).translationY(if (focus) -dp(4).toFloat() else 0f)
                    .setDuration(180).start()
                alone.animate().alpha(if (focus) 1f else 0f).setDuration(220).start()
                riflesso.animate().alpha(if (focus) 1f else 0f).setDuration(220).start()
                etichetta.setTextColor(if (focus) Stile.TESTO else Stile.TESTO_SECONDARIO)
                etichetta.setTypeface(null, if (focus) Typeface.BOLD else Typeface.NORMAL)
            }
            setOnClickListener { apriApp(app.activityInfo.packageName, nome) }
        }
    }

    /** Fa scorrere la riga di app intere solo quando quella selezionata uscirebbe dal pannello. */
    private fun mostraApp(indice: Int) {
        if (indice < 0) return
        primaVisibile = when {
            indice < primaVisibile -> indice
            indice >= primaVisibile + APP_VISIBILI -> indice - APP_VISIBILI + 1
            else -> primaVisibile
        }
        scorrimentoApp.smoothScrollTo(primaVisibile * (larghezzaApp + dp(SPAZIO_APP)), 0)
        aggiornaVisibili(animare = true)
    }

    /** Mostra solo le app dentro il pannello; quelle che entrano o escono sfumano. */
    private fun aggiornaVisibili(animare: Boolean) {
        for (i in 0 until rigaApp.childCount) {
            val visibile = i >= primaVisibile && i < primaVisibile + APP_VISIBILI
            val vista = rigaApp.getChildAt(i)
            if (animare) vista.animate().alpha(if (visibile) 1f else 0f).setDuration(160).start()
            else vista.alpha = if (visibile) 1f else 0f
        }
    }

    private fun pillola(etichetta: String, secondario: String? = null, puntino: Int? = null, azione: () -> Unit): View {
        val normale = Stile.vetro(this, 22)
        val selezionata = Stile.vetroSelezionato(this, 22)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = normale
            isFocusable = true
            isClickable = true
            setPadding(dp(18), 0, dp(18), 0)
            if (puntino != null) {
                addView(View(this@HomeActivity).apply { background = Stile.pieno(this@HomeActivity, 4, puntino) },
                    LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(10) })
            }
            addView(testo(etichetta, 14f, Typeface.BOLD))
            if (secondario != null) {
                addView(testo(secondario, 12f, Typeface.NORMAL).apply {
                    setTextColor(Stile.TESTO_SECONDARIO)
                    setPadding(dp(8), 0, 0, 0)
                })
            }
            layoutParams = LinearLayout.LayoutParams(WRAP, dp(44)).apply { marginStart = dp(10) }
            setOnFocusChangeListener { vista, focus ->
                vista.background = if (focus) selezionata else normale
                val scala = if (focus) 1.06f else 1f
                vista.animate().scaleX(scala).scaleY(scala).setDuration(120).start()
            }
            setOnClickListener { azione() }
        }
    }

    private fun apriApp(pacchetto: String, nome: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pacchetto)
            ?: packageManager.getLaunchIntentForPackage(pacchetto)
        if (intent != null) avvia(intent, nome)
    }

    private fun apriSony(classe: String, descrizione: String) =
        avvia(Intent().setComponent(ComponentName(Sonda.IMPOSTAZIONI_SONY, classe)), descrizione)

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
        private const val APP_VISIBILI = 6
        private const val SPAZIO_APP = 20
        private const val MARGINE_PANNELLO = 34
        private const val RAGGIO_APP = 14

        /** Ordine delle app preferite; le altre seguono in ordine alfabetico. */
        private val PREFERITE = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "it.rainet.androidtv",
            "com.spotify.tv.android"
        )

        private fun posizione(pacchetto: String) = PREFERITE.indexOf(pacchetto).let { if (it < 0) PREFERITE.size else it }
    }
}
