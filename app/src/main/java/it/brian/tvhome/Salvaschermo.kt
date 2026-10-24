package it.brian.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Lo screensaver della TV (Modalità Ambient) non parte da solo quando in primo piano c'è la nostra Home:
 * la Home conta il tempo senza tasti premuti e lo avvia lei, con il comando di sistema "Avvia screensaver".
 */
object Salvaschermo {

    private const val PREDEFINITO_MS = 15 * 60_000L
    private val AVVIO = ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")

    /** Dopo quanto partire, come impostato sulla TV; null se lo screensaver è spento o impostato su "Mai". */
    fun attesa(context: Context): Long? {
        val impostazioni = context.contentResolver
        if (Settings.Secure.getInt(impostazioni, "screensaver_enabled", 1) == 0) return null
        val ms = Settings.System.getLong(impostazioni, Settings.System.SCREEN_OFF_TIMEOUT, PREDEFINITO_MS)
        return ms.takeIf { it in 1 until Int.MAX_VALUE.toLong() }
    }

    fun avvia(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_MAIN).setComponent(AVVIO).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Registro.aggiungi(context, Registro.EVENTI, "Screensaver avviato dalla Home")
        } catch (e: Exception) {
            Registro.aggiungi(context, Registro.EVENTI, "Screensaver non avviabile: ${e.javaClass.simpleName}")
        }
    }
}
