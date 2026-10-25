package it.brian.tvhome

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.concurrent.thread

/**
 * Timer di spegnimento del Centro di controllo: allo scadere mette la TV in standby col Controllo IP Sony.
 * Vive nel processo dell'app, che resta attivo finché è acceso il servizio di accessibilità.
 */
object TimerSpegnimento {

    /** Le durate proposte, in minuti; 0 = disattivato. */
    val SCELTE = listOf(0, 15, 30, 60, 90, 120)

    private val gestore = Handler(Looper.getMainLooper())
    private var fine = 0L
    private var app: Context? = null
    private val spegni = Runnable {
        fine = 0L
        val contesto = app
        thread {
            val problema = ControlloIp.spegniTv()
            contesto?.let { Registro.aggiungi(it, Registro.EVENTI, "Timer di spegnimento: " + (problema ?: "TV spenta")) }
        }
    }

    fun imposta(context: Context, minuti: Int) {
        app = context.applicationContext
        gestore.removeCallbacks(spegni)
        fine = if (minuti > 0) SystemClock.uptimeMillis() + minuti * 60_000L else 0L
        if (minuti > 0) gestore.postAtTime(spegni, fine)
        Registro.aggiungi(context, Registro.EVENTI, "Timer di spegnimento: " + if (minuti > 0) "$minuti min" else "disattivato")
    }

    /** I minuti che mancano allo spegnimento, o null se il timer è spento. */
    fun minutiRimasti(): Int? {
        if (fine == 0L) return null
        return ((fine - SystemClock.uptimeMillis() + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun descrivi(minuti: Int): String = when {
        minuti <= 0 -> "Disattivato"
        minuti < 60 -> "$minuti min"
        minuti % 60 == 0 -> if (minuti == 60) "1 ora" else "${minuti / 60} ore"
        else -> "${minuti / 60} h ${minuti % 60} min"
    }
}
