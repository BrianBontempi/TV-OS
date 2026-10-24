package it.brian.tvhome

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Un ingresso da mostrare nella Home: una console riconosciuta via HDMI-CEC, una porta o la TV. */
data class Sorgente(val nome: String, val porta: String?, val accesa: Boolean, val id: String)

/** Costruisce l'elenco delle sorgenti usando i nomi che le console comunicano alla TV. */
object Sorgenti {

    private val NUMERO_PORTA = Regex("HDMI\\s*(\\d)")

    fun elenco(context: Context): List<Sorgente> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val tutti = Sonda.ingressi(context)
        val figli = tutti.filter { it.parentId != null }.groupBy { it.parentId }
        val risultato = mutableListOf<Sorgente>()

        for (info in tutti.filter { it.type == TvInputInfo.TYPE_TUNER && it.parentId == null }) {
            risultato += Sorgente("TV", null, true, info.id)
        }
        for (info in tutti.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }) {
            val porta = NUMERO_PORTA.find(Sonda.nomeIngresso(context, info))?.let { "HDMI ${it.groupValues[1]}" }
            val console = figli[info.id].orEmpty()
            if (console.isNotEmpty()) {
                for (figlio in console) {
                    risultato += Sorgente(nomeConsole(context, figlio), porta, statoAcceso(manager, figlio.id), figlio.id)
                }
            } else if (manager.getInputState(info.id) != TvInputManager.INPUT_STATE_DISCONNECTED) {
                risultato += Sorgente(porta ?: Sonda.nomeIngresso(context, info), null, statoAcceso(manager, info.id), info.id)
            }
        }
        return risultato
    }

    fun apri(context: Context, sorgente: Sorgente) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(sorgente.id)))
            Registro.aggiungi(context, Registro.EVENTI, "Aperto: ingresso ${sorgente.nome}")
        } catch (e: Exception) {
            Registro.aggiungi(context, Registro.EVENTI, "Non apribile: ingresso ${sorgente.nome} (${e.javaClass.simpleName})")
        }
    }

    private fun statoAcceso(manager: TvInputManager, id: String) =
        manager.getInputState(id) == TvInputManager.INPUT_STATE_CONNECTED

    /** "XBOX" diventa "Xbox"; gli altri nomi restano come li manda la console. */
    private fun nomeConsole(context: Context, info: TvInputInfo): String {
        val nome = try { info.loadLabel(context).toString() } catch (e: Exception) { info.id }
        return if (nome.length > 2 && nome == nome.uppercase()) nome.lowercase().replaceFirstChar { it.uppercase() } else nome
    }
}
