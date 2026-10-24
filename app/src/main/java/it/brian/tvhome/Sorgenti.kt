package it.brian.tvhome

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Un ingresso da mostrare nella Home: una console riconosciuta via HDMI-CEC, una porta o la TV. */
data class Sorgente(val nome: String, val porta: String?, val accesa: Boolean, val id: String, val tv: Boolean = false)

/** Costruisce l'elenco delle sorgenti usando i nomi che le console comunicano alla TV. */
object Sorgenti {

    private val NUMERO_PORTA = Regex("HDMI\\s*(\\d)")
    private const val FILE_CONSOLE = "console"
    private const val APP_TV_SONY = "com.sony.dtv.tvx"

    fun elenco(context: Context): List<Sorgente> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val tutti = Sonda.ingressi(context)
        val figli = tutti.filter { it.parentId != null }.groupBy { it.parentId }
        val risultato = mutableListOf<Sorgente>()

        // La TV ha un ingresso per ogni tipo di antenna: basta un solo pulsante.
        tutti.firstOrNull { it.type == TvInputInfo.TYPE_TUNER && it.parentId == null }?.let {
            risultato += Sorgente("TV", null, true, it.id, tv = true)
        }
        val memoria = context.getSharedPreferences(FILE_CONSOLE, Context.MODE_PRIVATE)
        for (info in tutti.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }) {
            val porta = NUMERO_PORTA.find(Sonda.nomeIngresso(context, info))?.let { "HDMI ${it.groupValues[1]}" }
            val console = figli[info.id].orEmpty()
            if (console.isNotEmpty()) {
                for (figlio in console) {
                    val nome = nomeConsole(context, figlio)
                    memoria.edit().putString(info.id, nome).apply()
                    risultato += Sorgente(nome, porta, statoAcceso(manager, figlio.id), figlio.id)
                }
            } else if (manager.getInputState(info.id) != TvInputManager.INPUT_STATE_DISCONNECTED) {
                // Console in riposo: la TV non ne sa più il nome, usiamo quello visto l'ultima volta.
                val ricordato = memoria.getString(info.id, null)
                risultato += if (ricordato != null) {
                    Sorgente(ricordato, porta, false, info.id)
                } else {
                    Sorgente(porta ?: Sonda.nomeIngresso(context, info), null, statoAcceso(manager, info.id), info.id)
                }
            }
        }
        return risultato
    }

    fun apri(context: Context, sorgente: Sorgente) {
        try {
            val intent = if (sorgente.tv) {
                context.packageManager.getLeanbackLaunchIntentForPackage(APP_TV_SONY)
                    ?: context.packageManager.getLaunchIntentForPackage(APP_TV_SONY)
                    ?: Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(sorgente.id))
            } else {
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(sorgente.id))
            }
            context.startActivity(intent)
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
