package it.brian.pokebonk

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.Toast
import kotlin.math.abs

/**
 * Il controller Bluetooth nel gioco, in due modi:
 * - GIOCO: i tasti arrivano alla pagina così come sono e li legge il gioco (Gamepad API della WebView);
 * - TASTIERA: se la pagina non vede il controller, stick, croce e tasti diventano tasti della tastiera.
 * All'inizio si prova GIOCO; se dopo tre pressioni la pagina non vede nessun controller si passa a TASTIERA.
 * Tenendo premuto Select per 3 secondi si cambia modo a mano, e la scelta resta salvata.
 */
class Controller(
    private val activity: Activity,
    private val pagina: () -> WebView,
    private val invia: (KeyEvent) -> Boolean
) {

    enum class Modo { GIOCO, TASTIERA }

    private val preferenze = activity.getSharedPreferences("controller", Context.MODE_PRIVATE)
    private val scelto = preferenze.getString("modo", null)
    var modo = if (scelto == "tastiera") Modo.TASTIERA else Modo.GIOCO
        private set

    /** Vera quando il modo è deciso: scelto a mano oppure già verificato in questa sessione. */
    private var verificato = scelto != null
    private var tentativi = 0
    private var ultimaVerifica = 0L

    /** La pagina ha finito di caricare: prima la verifica darebbe un falso "non lo vede". */
    var paginaPronta = false

    private val attesa = Handler(Looper.getMainLooper())
    private val cambiaModo = Runnable { alterna() }

    /** Tasti della tastiera che stiamo tenendo premuti noi, da rilasciare quando si esce o si cambia modo. */
    private val premuti = mutableSetOf<Int>()
    private var stickX = 0
    private var stickY = 0
    private var croceX = 0
    private var croceY = 0

    fun gestisci(event: KeyEvent): Boolean {
        if (!daController(event)) return false
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) attesa.postDelayed(cambiaModo, TIENI_PREMUTO_MS)
            if (event.action == KeyEvent.ACTION_UP) attesa.removeCallbacks(cambiaModo)
        }
        if (modo == Modo.GIOCO) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) verificaDopo()
            // B non letto dalla pagina: Android lo trasforma in Indietro. Qui vale come Esc e non fa uscire dall'app.
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                traduci(event, KeyEvent.KEYCODE_ESCAPE)
                return true
            }
            return false
        }
        TASTI[event.keyCode]?.let { traduci(event, it) }
        return true
    }

    fun gestisci(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        val x = asse(event, MotionEvent.AXIS_X)
        val y = asse(event, MotionEvent.AXIS_Y)
        if (modo == Modo.GIOCO) {
            if (abs(x) > 0.5f || abs(y) > 0.5f) verificaDopo()
            return false
        }
        stickX = direzione(x, stickX)
        stickY = direzione(y, stickY)
        tieni(KeyEvent.KEYCODE_A, stickX < 0)
        tieni(KeyEvent.KEYCODE_D, stickX > 0)
        tieni(KeyEvent.KEYCODE_W, stickY < 0)
        tieni(KeyEvent.KEYCODE_S, stickY > 0)
        croceX = direzione(event.getAxisValue(MotionEvent.AXIS_HAT_X), croceX)
        croceY = direzione(event.getAxisValue(MotionEvent.AXIS_HAT_Y), croceY)
        tieni(KeyEvent.KEYCODE_DPAD_LEFT, croceX < 0)
        tieni(KeyEvent.KEYCODE_DPAD_RIGHT, croceX > 0)
        tieni(KeyEvent.KEYCODE_DPAD_UP, croceY < 0)
        tieni(KeyEvent.KEYCODE_DPAD_DOWN, croceY > 0)
        return true
    }

    /** Preme e rilascia subito un tasto della tastiera nella pagina (per esempio Esc dal tasto Indietro del telecomando). */
    fun premi(codice: Int) {
        val ora = SystemClock.uptimeMillis()
        invia(tasto(ora, ora, KeyEvent.ACTION_DOWN, codice, 0))
        invia(tasto(ora, ora, KeyEvent.ACTION_UP, codice, 0))
    }

    fun rilasciaTutto() {
        val ora = SystemClock.uptimeMillis()
        premuti.toList().forEach { invia(tasto(ora, ora, KeyEvent.ACTION_UP, it, 0)) }
        premuti.clear()
        stickX = 0; stickY = 0; croceX = 0; croceY = 0
    }

    fun chiudi() {
        attesa.removeCallbacksAndMessages(null)
    }

    private fun traduci(originale: KeyEvent, codice: Int) {
        if (originale.action == KeyEvent.ACTION_DOWN) premuti += codice else premuti -= codice
        invia(tasto(originale.downTime, originale.eventTime, originale.action, codice, originale.repeatCount))
    }

    private fun tieni(codice: Int, giu: Boolean) {
        if (giu == codice in premuti) return
        val ora = SystemClock.uptimeMillis()
        if (giu) premuti += codice else premuti -= codice
        invia(tasto(ora, ora, if (giu) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, codice, 0))
    }

    /**
     * Un tasto come se venisse da una tastiera vera. Il codice di scansione (quello di Linux) serve:
     * da lì la WebView ricava event.code ("KeyW", "Enter"...), che molti giochi usano al posto della lettera.
     */
    private fun tasto(giuDa: Long, ora: Long, azione: Int, codice: Int, ripetizione: Int) = KeyEvent(
        giuDa, ora, azione, codice, ripetizione, 0, KeyCharacterMap.VIRTUAL_KEYBOARD,
        SCANSIONE[codice] ?: 0, 0, InputDevice.SOURCE_KEYBOARD
    )

    private fun verificaDopo() {
        if (verificato || !paginaPronta || modo != Modo.GIOCO) return
        val adesso = SystemClock.uptimeMillis()
        if (adesso - ultimaVerifica < 1000) return
        ultimaVerifica = adesso
        attesa.postDelayed({ verifica() }, 500)
    }

    /** Chiede alla pagina se vede un controller collegato: è quello che legge il gioco con la Gamepad API. */
    private fun verifica() {
        pagina().evaluateJavascript(VEDE_CONTROLLER) { risultato ->
            if (verificato) return@evaluateJavascript
            if (risultato == "true") {
                verificato = true
                return@evaluateJavascript
            }
            tentativi++
            if (tentativi >= 3) {
                verificato = true
                imposta(Modo.TASTIERA)
            }
        }
    }

    private fun alterna() {
        val nuovo = if (modo == Modo.GIOCO) Modo.TASTIERA else Modo.GIOCO
        preferenze.edit().putString("modo", if (nuovo == Modo.TASTIERA) "tastiera" else "gioco").apply()
        verificato = true
        imposta(nuovo)
    }

    private fun imposta(nuovo: Modo) {
        rilasciaTutto()
        modo = nuovo
        val testo = when (nuovo) {
            Modo.GIOCO -> "Controller: lo legge il gioco"
            Modo.TASTIERA -> "Controller come tastiera: stick e croce muovono, A = Invio, B = Esc, X = Salto"
        }
        Toast.makeText(activity, testo, Toast.LENGTH_LONG).show()
    }

    /** -1, 0 o 1, con un margine tra premere e rilasciare così il tasto non sfarfalla vicino alla soglia. */
    private fun direzione(valore: Float, prima: Int): Int = when {
        valore <= -SOGLIA_PREMI -> -1
        valore >= SOGLIA_PREMI -> 1
        abs(valore) < SOGLIA_RILASCIA -> 0
        else -> prima
    }

    /** Valore dello stick con la zona morta del dispositivo. */
    private fun asse(event: MotionEvent, asse: Int): Float {
        val valore = event.getAxisValue(asse)
        val piatto = event.device?.getMotionRange(asse, event.source)?.flat ?: 0f
        return if (abs(valore) > piatto) valore else 0f
    }

    companion object {
        private const val TIENI_PREMUTO_MS = 3000L
        private const val SOGLIA_PREMI = 0.45f
        private const val SOGLIA_RILASCIA = 0.25f

        private const val VEDE_CONTROLLER =
            "(function(){try{var p=navigator.getGamepads?navigator.getGamepads():[];" +
                "for(var i=0;i<p.length;i++){if(p[i]&&p[i].connected)return true;}}catch(e){}return false;})()"

        fun daController(event: InputEvent): Boolean =
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)

        /** Modo TASTIERA: tasto del controller → tasto della tastiera (tasti di PokeBonk sul computer). */
        private val TASTI = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_ENTER,        // conferma, scelta del potenziamento
            KeyEvent.KEYCODE_DPAD_CENTER to KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_B to KeyEvent.KEYCODE_ESCAPE,       // indietro, pausa
            KeyEvent.KEYCODE_BACK to KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_X to KeyEvent.KEYCODE_SPACE,        // salto
            KeyEvent.KEYCODE_BUTTON_Y to KeyEvent.KEYCODE_R,            // Ultimate
            KeyEvent.KEYCODE_BUTTON_L1 to KeyEvent.KEYCODE_SHIFT_LEFT,  // scivolata
            KeyEvent.KEYCODE_BUTTON_R1 to KeyEvent.KEYCODE_E,           // interagisci
            KeyEvent.KEYCODE_BUTTON_START to KeyEvent.KEYCODE_P,        // pausa
            KeyEvent.KEYCODE_BUTTON_SELECT to KeyEvent.KEYCODE_M,       // mappa
            KeyEvent.KEYCODE_DPAD_UP to KeyEvent.KEYCODE_DPAD_UP,       // la WebView li passa come frecce
            KeyEvent.KEYCODE_DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT
        )

        /** Codici di scansione di Linux (input-event-codes.h) dei tasti che mandiamo. */
        private val SCANSIONE = mapOf(
            KeyEvent.KEYCODE_ESCAPE to 1,
            KeyEvent.KEYCODE_E to 18,
            KeyEvent.KEYCODE_R to 19,
            KeyEvent.KEYCODE_P to 25,
            KeyEvent.KEYCODE_W to 17,
            KeyEvent.KEYCODE_ENTER to 28,
            KeyEvent.KEYCODE_A to 30,
            KeyEvent.KEYCODE_S to 31,
            KeyEvent.KEYCODE_D to 32,
            KeyEvent.KEYCODE_SHIFT_LEFT to 42,
            KeyEvent.KEYCODE_M to 50,
            KeyEvent.KEYCODE_SPACE to 57,
            KeyEvent.KEYCODE_DPAD_UP to 103,
            KeyEvent.KEYCODE_DPAD_LEFT to 105,
            KeyEvent.KEYCODE_DPAD_RIGHT to 106,
            KeyEvent.KEYCODE_DPAD_DOWN to 108
        )
    }
}
