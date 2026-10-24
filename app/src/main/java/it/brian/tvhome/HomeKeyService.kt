package it.brian.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Registra i tasti del telecomando e le schermate che si aprono.
 * Se "Intercetta HOME" è attivo, prova due strade per aprire TV Home al posto della Home Google:
 * consumare il tasto HOME, oppure riaprirsi quando compare la Home Google.
 */
class HomeKeyService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pacchetto = event.packageName?.toString() ?: return
        val classe = event.className?.toString().orEmpty()
        Registro.aggiungi(this, Registro.SCHERMATE, "$pacchetto / $classe")
        if (Registro.intercettaHome(this) && pacchetto in HOME_GOOGLE) {
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
            if (event.action == KeyEvent.ACTION_UP) {
                Registro.aggiungi(this, Registro.EVENTI, "Tasto HOME intercettato")
                apriTvHome()
            }
            return true
        }
        return false
    }

    override fun onInterrupt() = Unit

    private fun apriTvHome() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (e: Exception) {
            Registro.aggiungi(this, Registro.EVENTI, "Apertura TV Home fallita: ${e.message}")
        }
    }

    companion object {
        private val HOME_GOOGLE = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher"
        )
    }
}
