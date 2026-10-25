package it.brian.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Registra i tasti del telecomando e le schermate che si aprono.
 * Se "Intercetta HOME" è attivo, prova due strade per aprire TV Home al posto della Home Google:
 * consumare il tasto HOME, oppure riaprirsi quando compare la Home Google.
 * Tenendo premuto HOME si apre il Centro di controllo.
 */
class HomeKeyService : AccessibilityService() {

    private val gestore = Handler(Looper.getMainLooper())
    private var pressioneLunga = false
    private val apriCentro = Runnable {
        pressioneLunga = true
        Registro.aggiungi(this, Registro.EVENTI, "HOME tenuto premuto: apro il Centro di controllo")
        try {
            CentroControlloActivity.apri(this)
        } catch (e: Exception) {
            Registro.aggiungi(this, Registro.EVENTI, "Apertura Centro di controllo fallita: ${e.message}")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Dopo "Riavvia" la Home Google compare prima che il servizio sia pronto, quindi non la vede: si apre TV Home qui.
        if (Registro.intercettaHome(this) && SystemClock.elapsedRealtime() < APPENA_ACCESA_MS) {
            Registro.aggiungi(this, Registro.EVENTI, "TV appena avviata: apro TV Home")
            apriTvHome()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pacchetto = event.packageName?.toString() ?: return
        val classe = event.className?.toString().orEmpty()
        Registro.aggiungi(this, Registro.SCHERMATE, "$pacchetto / $classe")
        // Solo la schermata Home vera: i pannelli di Google (impostazioni rapide, ingressi) restano utilizzabili.
        if (Registro.intercettaHome(this) && pacchetto in HOME_GOOGLE && classe.endsWith("HomeActivity")) {
            Registro.aggiungi(this, Registro.EVENTI, "Home Google comparsa: riapro TV Home")
            apriTvHome()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Registro.aggiungi(
                this,
                Registro.TASTI,
                "${KeyEvent.keyCodeToString(event.keyCode)} (codice ${event.keyCode}, scan ${event.scanCode})"
            )
        }
        if (Registro.intercettaHome(this) && event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                pressioneLunga = false
                gestore.postDelayed(apriCentro, PRESSIONE_LUNGA_MS)
            } else if (event.action == KeyEvent.ACTION_UP) {
                gestore.removeCallbacks(apriCentro)
                if (!pressioneLunga) {
                    Registro.aggiungi(this, Registro.EVENTI, "Tasto HOME intercettato")
                    apriTvHome()
                }
            }
            return true
        }
        return false
    }

    override fun onInterrupt() = Unit

    private fun apriTvHome() {
        try {
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (e: Exception) {
            Registro.aggiungi(this, Registro.EVENTI, "Apertura TV Home fallita: ${e.message}")
        }
    }

    companion object {
        private const val PRESSIONE_LUNGA_MS = 500L
        private const val APPENA_ACCESA_MS = 5 * 60_000L
        private val HOME_GOOGLE = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher"
        )
    }
}
