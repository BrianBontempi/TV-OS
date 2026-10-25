package it.brian.tvhome

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * I suoni dell'interfaccia, fatti apposta per TV Home in stile Apple TV: un tic morbido quando si sposta la selezione,
 * un "pop" su OK e un colpetto sordo quando si arriva al bordo. Seguono il volume della TV.
 */
object Suoni {

    private const val FILE = "suoni"
    private const val ATTIVI = "attivi"

    private var pool: SoundPool? = null
    private var idSposta = 0
    private var idConferma = 0
    private var idBordo = 0
    private var attivi = true

    fun prepara(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        attivi = app.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(ATTIVI, true)
        pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build().also {
                idSposta = it.load(app, R.raw.suono_sposta, 1)
                idConferma = it.load(app, R.raw.suono_conferma, 1)
                idBordo = it.load(app, R.raw.suono_bordo, 1)
            }
    }

    fun sposta() = suona(idSposta)
    fun conferma() = suona(idConferma)
    fun bordo() = suona(idBordo)

    fun attivi() = attivi

    fun impostaAttivi(context: Context, valore: Boolean) {
        attivi = valore
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(ATTIVI, valore).apply()
    }

    private fun suona(suono: Int) {
        if (attivi && suono != 0) pool?.play(suono, 1f, 1f, 1, 0, 1f)
    }
}
