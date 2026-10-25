package it.brian.tvhome

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

/**
 * Base di tutte le schermate: sposta la selezione con le frecce e suona i suoni di TV Home.
 * I suoni di sistema di Android sono spenti qui dentro, così non si sentono due suoni insieme.
 */
open class SchermataTv : Activity() {

    private val senzaSuoniDiSistema = ViewTreeObserver.OnGlobalLayoutListener { spegniSuoniDiSistema(window.decorView) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Suoni.prepara(this)
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(senzaSuoniDiSistema)
    }

    override fun onDestroy() {
        window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(senzaSuoniDiSistema)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val direzione = direzione(event.keyCode)
        val prima = currentFocus
        if (direzione == null) {
            if (event.repeatCount == 0 && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) &&
                prima?.isClickable == true) Suoni.conferma()
            return super.dispatchKeyEvent(event)
        }
        // Prima decidono le viste (righe che scorrono, pannelli, bordi bloccati).
        if (super.dispatchKeyEvent(event)) {
            if (currentFocus !== prima) Suoni.sposta()
            return true
        }
        // Poi lo spostamento normale di Android, fatto qui per suonare il nostro tic al posto del suo.
        val da = prima ?: return false
        val verso = da.focusSearch(direzione)
        if (verso == null || verso === da) {
            Suoni.bordo()
            return true
        }
        // Come fa Android: la nuova selezione parte dal punto in cui era la vecchia.
        val area = Rect()
        val riuscito = try {
            val radice = window.decorView as ViewGroup
            da.getFocusedRect(area)
            radice.offsetDescendantRectToMyCoords(da, area)
            radice.offsetRectIntoDescendantCoords(verso, area)
            verso.requestFocus(direzione, area)
        } catch (e: IllegalArgumentException) {
            verso.requestFocus(direzione)
        }
        if (!riuscito) {
            Suoni.bordo()
            return true
        }
        Suoni.sposta()
        return true
    }

    private fun direzione(tasto: Int) = when (tasto) {
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }

    private fun spegniSuoniDiSistema(vista: View) {
        vista.isSoundEffectsEnabled = false
        if (vista is ViewGroup) for (i in 0 until vista.childCount) spegniSuoniDiSistema(vista.getChildAt(i))
    }
}
