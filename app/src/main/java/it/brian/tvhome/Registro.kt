package it.brian.tvhome

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro persistente di eventi, tasti e schermate, più le preferenze dell'app. */
object Registro {
    const val TASTI = "tasti"
    const val SCHERMATE = "schermate"
    const val EVENTI = "eventi"

    private const val FILE = "registro"
    private const val MAX_RIGHE = 60
    private const val INTERCETTA_HOME = "intercetta_home"

    private val ora = SimpleDateFormat("HH:mm:ss", Locale.ITALY)

    @Synchronized
    fun aggiungi(context: Context, tipo: String, testo: String) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val righe = prefs.getString(tipo, "").orEmpty().lines().filter { it.isNotBlank() }
        val nuove = (righe + "${ora.format(Date())}  $testo").takeLast(MAX_RIGHE)
        prefs.edit().putString(tipo, nuove.joinToString("\n")).apply()
    }

    fun leggi(context: Context, tipo: String): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(tipo, "").orEmpty()

    fun svuota(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(TASTI).remove(SCHERMATE).remove(EVENTI).apply()
    }

    fun intercettaHome(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(INTERCETTA_HOME, true)

    fun impostaIntercettaHome(context: Context, attivo: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(INTERCETTA_HOME, attivo).apply()
    }
}
