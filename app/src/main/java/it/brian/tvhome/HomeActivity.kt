package it.brian.tvhome

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
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
    private lateinit var meteo: LinearLayout
    private lateinit var meteoIcona: TextView
    private lateinit var meteoTemperatura: TextView
    private lateinit var meteoCielo: TextView
    private lateinit var meteoDettagli: TextView
    private lateinit var scorrimentoApp: HorizontalScrollView
    private var larghezzaApp = 0
    private var primaVisibile = 0
    private var pacchettiMostrati = emptyList<String>()
    private lateinit var radice: FrameLayout
    private lateinit var contenuto: LinearLayout
    private lateinit var suggerimento: TextView
    private var menu: MenuApp? = null
    // L'app che si sta spostando con le frecce, e la sua animazione "tremolante" come su Apple TV.
    private var spostata: View? = null
    private var tremolio: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        radice = FrameLayout(this)
        radice.addView(SfondoAurora(this), FrameLayout.LayoutParams(MATCH, MATCH))

        contenuto = LinearLayout(this).apply {
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
        contenuto.addView(pannelloMeteo(), LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(18) })
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

        suggerimento = testo("Sposta l'app con ← →, poi premi OK", 14f, Typeface.BOLD).apply {
            background = Stile.vetro(this@HomeActivity, 20, forte = true)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            visibility = View.GONE
        }
        radice.addView(suggerimento, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(28)
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
        Meteo.aggiorna { dati -> runOnUiThread { mostraMeteo(dati) } }
    }

    /** Meteo sopra la barra delle app; resta nascosto finché non arrivano i dati. */
    private fun pannelloMeteo(): View {
        meteoIcona = testo("", 30f, Typeface.NORMAL)
        meteoTemperatura = testo("", 34f, Typeface.BOLD).apply { setPadding(dp(12), 0, dp(16), 0) }
        meteoCielo = testo("", 15f, Typeface.BOLD)
        meteoDettagli = testo("", 12f, Typeface.NORMAL).apply { setTextColor(Stile.TESTO_SECONDARIO) }
        meteo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Stile.vetro(this@HomeActivity, 20)
            setPadding(dp(20), dp(12), dp(24), dp(12))
            visibility = View.GONE
            addView(meteoIcona)
            addView(meteoTemperatura)
            addView(LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(meteoCielo)
                addView(meteoDettagli)
            })
        }
        return meteo
    }

    private fun mostraMeteo(dati: DatiMeteo) {
        meteoIcona.text = dati.icona
        meteoTemperatura.text = "${dati.temperatura}°"
        meteoCielo.text = dati.cielo
        val luogo = if (dati.citta.isNotBlank()) "${dati.citta} · " else ""
        meteoDettagli.text = "${luogo}Max ${dati.massima}° · Min ${dati.minima}°"
        meteo.visibility = View.VISIBLE
    }

    // La Home non si chiude con Indietro: chiude solo il menu dell'app, se è aperto.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        menu?.chiudi()
        return true
    }

    /** Mentre un'app si sposta, le frecce la muovono nella riga e OK o Indietro la lasciano lì. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vista = spostata ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> muovi(vista, -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> muovi(vista, +1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> finisciSpostamento()
            }
        }
        // Durante lo spostamento nessun altro tasto fa niente, così l'app non perde la selezione.
        return true
    }

    /** Tenendo premuto OK su un'app: Sposta e Disinstalla. */
    private fun mostraMenu(app: ResolveInfo, vista: View) {
        val pm = packageManager
        val pacchetto = app.activityInfo.packageName
        val nome = app.loadLabel(pm).toString()
        val diSistema = app.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val togli = if (diSistema) {
            "Gestisci app" to { avvia(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pacchetto")), "Info app $nome") }
        } else {
            "Disinstalla" to { avvia(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pacchetto")), "Disinstalla $nome") }
        }
        val nuovo = MenuApp(this, nome, app.loadIcon(pm),
            if (diSistema) "App preinstallata sulla TV: non si disinstalla, ma dalla sua pagina si può disattivare." else null,
            listOf("Sposta" to { iniziaSpostamento(vista) }, togli)
        ) {
            menu = null
            contenuto.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            if (spostata == null) vista.requestFocus()
        }
        menu = nuovo
        nuovo.mostra(radice)
        contenuto.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun iniziaSpostamento(vista: View) {
        spostata = vista
        vista.requestFocus()
        suggerimento.visibility = View.VISIBLE
        tremolio = ObjectAnimator.ofFloat(vista, View.ROTATION, -1.2f, 1.2f).apply {
            duration = 130
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun muovi(vista: View, passo: Int) {
        val da = rigaApp.indexOfChild(vista)
        val a = da + passo
        if (da < 0 || a < 0 || a >= rigaApp.childCount) return
        // Si sposta la vicina, non l'app selezionata: così la selezione non salta su un'altra app.
        val vicina = rigaApp.getChildAt(a)
        rigaApp.removeViewAt(a)
        rigaApp.addView(vicina, da)
        // Il primo riquadro non ha spazio a sinistra, gli altri sì.
        for (i in 0 until rigaApp.childCount) {
            (rigaApp.getChildAt(i).layoutParams as LinearLayout.LayoutParams).marginStart = if (i == 0) 0 else dp(SPAZIO_APP)
        }
        rigaApp.requestLayout()
        mostraApp(a)
    }

    private fun finisciSpostamento() {
        val vista = spostata ?: return
        spostata = null
        tremolio?.cancel()
        tremolio = null
        vista.rotation = 0f
        suggerimento.visibility = View.GONE
        pacchettiMostrati = (0 until rigaApp.childCount).mapNotNull { rigaApp.getChildAt(it).tag as? String }
        prefs().edit().putString(ORDINE, pacchettiMostrati.joinToString(",")).apply()
        Registro.aggiungi(this, Registro.EVENTI, "Nuovo ordine delle app salvato")
    }

    private fun prefs() = getSharedPreferences(FILE_ORDINE, Context.MODE_PRIVATE)

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
        data.text = oggi.replaceFirstChar { it.uppercase() }
    }

    /**
     * Tutte le app per TV, nell'ordine scelto da Brian; quelle nuove seguono, con le preferite davanti.
     * Ricostruisce la riga solo se sono cambiate.
     */
    private fun caricaApp() {
        if (spostata != null) return
        val pm = packageManager
        val ordine = prefs().getString(ORDINE, null)?.split(',').orEmpty()
        val trovate = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            // Il nome si legge una volta per app, non a ogni confronto: la Home riappare più in fretta.
            .map { it to it.loadLabel(pm).toString().lowercase() }
            .sortedWith(compareBy<Pair<ResolveInfo, String>>(
                { ordine.indexOf(it.first.activityInfo.packageName).let { i -> if (i < 0) ordine.size else i } },
                { posizione(it.first.activityInfo.packageName) },
                { it.second }
            ))
            .map { it.first }
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
        rigaSorgenti.addView(pillola("Gaming") { avvia(Intent(this, GamingActivity::class.java), "Gaming") })
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
            tag = app.activityInfo.packageName
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
            setOnLongClickListener { vista ->
                mostraMenu(app, vista)
                true
            }
            // Ai bordi della riga le frecce non fanno niente: niente salto alle sorgenti sotto,
            // e niente scorrimento a vuoto dopo l'ultima app, che lascerebbe le app tagliate.
            setOnKeyListener { vista, codice, evento ->
                val indice = rigaApp.indexOfChild(vista)
                evento.action == KeyEvent.ACTION_DOWN &&
                    ((codice == KeyEvent.KEYCODE_DPAD_LEFT && indice == 0) ||
                        (codice == KeyEvent.KEYCODE_DPAD_RIGHT && indice == rigaApp.childCount - 1))
            }
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
        private const val FILE_ORDINE = "ordine_app"
        private const val ORDINE = "pacchetti"

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
