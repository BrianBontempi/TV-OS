package it.brian.pokebonk

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * PokeBonk sulla TV: il sito ufficiale del gioco dentro una WebView a schermo intero.
 * L'app non contiene nessun file del gioco: carica solo la pagina, come farebbe il browser.
 */
class GiocoActivity : Activity() {

    private lateinit var radice: FrameLayout
    private lateinit var pagina: WebView
    private lateinit var messaggio: TextView
    private lateinit var controller: Controller

    /** La vista che il gioco chiede quando va a schermo intero da solo (requestFullscreen). */
    private var vistaSchermoIntero: View? = null
    private var chiudiSchermoIntero: WebChromeClient.CustomViewCallback? = null

    private var ultimoIndietro = 0L
    private var errore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Schermo sempre acceso mentre si gioca: niente screensaver a metà partita.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        radice = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        messaggio = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            text = "Carico PokeBonk…"
        }
        radice.addView(messaggio, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(radice)

        controller = Controller(this, { pagina }) { window.superDispatchKeyEvent(it) }
        pagina = creaPagina()
        if (savedInstanceState == null || pagina.restoreState(savedInstanceState) == null) pagina.loadUrl(INDIRIZZO)
        nascondiBarre()
    }

    private fun creaPagina(): WebView {
        val vista = WebView(this)
        vista.setBackgroundColor(Color.BLACK)
        vista.isFocusable = true
        vista.isFocusableInTouchMode = true
        vista.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            // Senza "Mobile" il sito tratta la TV come un computer e non mostra i pulsanti touch.
            userAgentString = userAgentString.replace(" Mobile", "")
        }
        // Login e progressi restano salvati tra un avvio e l'altro.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(vista, true)
        }
        vista.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val schema = request.url.scheme
                if (schema == "http" || schema == "https") return false
                // Link non web (mail, app esterne): li apre Android, se c'è un'app che li gestisce.
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) { }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (errore) return
                messaggio.visibility = View.GONE
                controller.paginaPronta = true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                errore = true
                messaggio.text = "PokeBonk non risponde (${error.description}).\nControlla la rete e premi OK per riprovare."
                messaggio.visibility = View.VISIBLE
                messaggio.bringToFront()
            }

            // Se il gioco esaurisce la memoria della TV, Android chiude solo la pagina: la riapro invece di chiudere l'app.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                ricreaPagina()
                return true
            }
        }
        vista.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                vistaSchermoIntero?.let { radice.removeView(it) }
                vistaSchermoIntero = view
                chiudiSchermoIntero = callback
                radice.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                view.requestFocus()
                nascondiBarre()
            }

            override fun onHideCustomView() {
                vistaSchermoIntero?.let { radice.removeView(it) }
                vistaSchermoIntero = null
                chiudiSchermoIntero = null
                pagina.requestFocus()
                nascondiBarre()
            }
        }
        radice.addView(vista, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        vista.requestFocus()
        return vista
    }

    private fun ricreaPagina() {
        controller.rilasciaTutto()
        controller.paginaPronta = false
        vistaSchermoIntero?.let { radice.removeView(it) }
        vistaSchermoIntero = null
        chiudiSchermoIntero = null
        radice.removeView(pagina)
        pagina.destroy()
        errore = false
        messaggio.text = "Il gioco si è chiuso per mancanza di memoria: lo riapro…"
        messaggio.visibility = View.VISIBLE
        pagina = creaPagina()
        pagina.loadUrl(INDIRIZZO)
    }

    private fun riprova() {
        errore = false
        messaggio.text = "Carico PokeBonk…"
        pagina.reload()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controller.gestisci(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) indietro()
            return true
        }
        if (errore && event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
            riprova()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        controller.gestisci(event) || super.dispatchGenericMotionEvent(event)

    /** Indietro del telecomando: una volta torna indietro nella pagina, due volte di fila esce dall'app. */
    private fun indietro() {
        val adesso = SystemClock.uptimeMillis()
        if (adesso - ultimoIndietro < DOPPIO_INDIETRO_MS) {
            finish()
            return
        }
        ultimoIndietro = adesso
        when {
            vistaSchermoIntero != null -> chiudiSchermoIntero?.onCustomViewHidden()
            pagina.canGoBack() -> pagina.goBack()
            // Nel gioco non c'è una pagina precedente: Esc fa quello che fa sul computer (pausa o menu indietro).
            else -> controller.premi(KeyEvent.KEYCODE_ESCAPE)
        }
        Toast.makeText(this, "Premi di nuovo Indietro per uscire", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun nascondiBarre() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) nascondiBarre()
    }

    override fun onResume() {
        super.onResume()
        pagina.onResume()
        pagina.resumeTimers()
        nascondiBarre()
    }

    override fun onPause() {
        controller.rilasciaTutto()
        pagina.onPause()
        pagina.pauseTimers()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pagina.saveState(outState)
    }

    override fun onDestroy() {
        controller.chiudi()
        radice.removeView(pagina)
        pagina.destroy()
        super.onDestroy()
    }

    companion object {
        const val INDIRIZZO = "https://pokebonk.com/"
        private const val DOPPIO_INDIETRO_MS = 2000L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
