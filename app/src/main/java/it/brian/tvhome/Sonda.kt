package it.brian.tvhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Raccoglie in sola lettura quello che la TV mette a disposizione delle app. */
object Sonda {

    private val PACCHETTI_INTERESSANTI = Regex("sony|settings|dtv|livetv|launcher|hdmi|cec", RegexOption.IGNORE_CASE)
    private val PROPRIETA_INTERESSANTI = Regex("model|product|sony|bravia|hdmi|cec|vrr|allm|panel|brand", RegexOption.IGNORE_CASE)

    fun ingressi(context: Context): List<TvInputInfo> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return try {
            manager.tvInputList
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun nomeIngresso(context: Context, info: TvInputInfo): String {
        val personalizzato = try { info.loadCustomLabel(context)?.toString() } catch (e: Exception) { null }
        val nome = try { info.loadLabel(context).toString() } catch (e: Exception) { info.id }
        return if (!personalizzato.isNullOrBlank() && personalizzato != nome) "$personalizzato ($nome)" else nome
    }

    fun rapporto(context: Context): String = buildString {
        appendLine("TV HOME · RAPPORTO DI PROVA")
        appendLine("Creato: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALY).format(Date())}")
        appendLine()
        sezioneDispositivo(this)
        sezioneHome(this, context)
        sezioneIngressi(this, context)
        sezioneRegistro(this, context, "TASTI DEL TELECOMANDO (servizio accessibilità)", Registro.TASTI)
        sezioneRegistro(this, context, "SCHERMATE APERTE (servizio accessibilità)", Registro.SCHERMATE)
        sezioneRegistro(this, context, "EVENTI", Registro.EVENTI)
        sezioneProprieta(this)
        sezioneSchermate(this, context)
    }

    private fun sezioneDispositivo(sb: StringBuilder) {
        sb.appendLine("== DISPOSITIVO ==")
        sb.appendLine("Produttore: ${Build.MANUFACTURER}")
        sb.appendLine("Modello: ${Build.MODEL}")
        sb.appendLine("Prodotto: ${Build.PRODUCT}")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine("Board: ${Build.BOARD} · Hardware: ${Build.HARDWARE}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Build: ${Build.DISPLAY}")
        sb.appendLine()
    }

    private fun sezioneHome(sb: StringBuilder, context: Context) {
        val pm = context.packageManager
        val intentHome = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        sb.appendLine("== SCHERMATA HOME ==")
        val predefinita = pm.resolveActivity(intentHome, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        sb.appendLine("Predefinita: ${predefinita?.packageName ?: "nessuna"} / ${predefinita?.name ?: "-"}")
        pm.queryIntentActivities(intentHome, 0).forEach {
            sb.appendLine("Disponibile: ${it.activityInfo.packageName} / ${it.activityInfo.name}")
        }
        sb.appendLine("Intercetta HOME attivo: ${if (Registro.intercettaHome(context)) "sì" else "no"}")
        sb.appendLine()
    }

    private fun sezioneIngressi(sb: StringBuilder, context: Context) {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val lista = ingressi(context)
        sb.appendLine("== INGRESSI (${lista.size}) ==")
        if (lista.isEmpty()) sb.appendLine("Nessun ingresso visibile all'app.")
        for (info in lista) {
            sb.appendLine("- ${nomeIngresso(context, info)}")
            sb.appendLine("  id: ${info.id}")
            sb.appendLine("  tipo: ${tipo(info.type)} · passthrough: ${info.isPassthroughInput} · nascosto: ${nascosto(context, info)}")
            sb.appendLine("  padre: ${info.parentId ?: "-"} · stato: ${stato(manager, info.id)}")
            sb.appendLine("  hdmi: ${infoHdmi(info)}")
        }
        sb.appendLine()
    }

    private fun sezioneRegistro(sb: StringBuilder, context: Context, titolo: String, tipo: String) {
        sb.appendLine("== $titolo ==")
        sb.appendLine(Registro.leggi(context, tipo).ifBlank { "(vuoto)" })
        sb.appendLine()
    }

    private fun sezioneProprieta(sb: StringBuilder) {
        sb.appendLine("== PROPRIETÀ DI SISTEMA (filtrate) ==")
        try {
            val processo = Runtime.getRuntime().exec(arrayOf("getprop"))
            val righe = processo.inputStream.bufferedReader().use { it.readLines() }
            val utili = righe.filter { PROPRIETA_INTERESSANTI.containsMatchIn(it) }
            sb.appendLine(if (utili.isEmpty()) "(nessuna leggibile)" else utili.joinToString("\n"))
        } catch (e: Exception) {
            sb.appendLine("Non leggibili: ${e.message}")
        }
        sb.appendLine()
    }

    private fun sezioneSchermate(sb: StringBuilder, context: Context) {
        val pm = context.packageManager
        val pacchetti = try {
            pm.getInstalledPackages(0).map { it.packageName }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
        sb.appendLine("== APP INSTALLATE (${pacchetti.size}) ==")
        sb.appendLine(pacchetti.joinToString("\n"))
        sb.appendLine()
        sb.appendLine("== SCHERMATE APRIBILI DALLE APP DI SISTEMA ==")
        for (nome in pacchetti.filter { PACCHETTI_INTERESSANTI.containsMatchIn(it) }) {
            val attivita = try {
                pm.getPackageInfo(nome, PackageManager.GET_ACTIVITIES).activities.orEmpty().filter { it.exported }
            } catch (e: Exception) {
                emptyList()
            }
            if (attivita.isEmpty()) continue
            sb.appendLine("[$nome]")
            attivita.forEach { sb.appendLine("  ${it.name}") }
        }
    }

    private fun tipo(valore: Int): String = when (valore) {
        TvInputInfo.TYPE_TUNER -> "sintonizzatore"
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_COMPOSITE -> "composito"
        TvInputInfo.TYPE_COMPONENT -> "component"
        TvInputInfo.TYPE_OTHER -> "altro"
        else -> "tipo $valore"
    }

    private fun stato(manager: TvInputManager?, id: String): String = when (manager?.getInputState(id)) {
        TvInputManager.INPUT_STATE_CONNECTED -> "collegato"
        TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "collegato in standby"
        TvInputManager.INPUT_STATE_DISCONNECTED -> "scollegato"
        else -> "sconosciuto"
    }

    private fun nascosto(context: Context, info: TvInputInfo): Boolean =
        try { info.isHidden(context) } catch (e: Exception) { false }

    private fun infoHdmi(info: TvInputInfo): String = try {
        info.javaClass.getMethod("getHdmiDeviceInfo").invoke(info)?.toString() ?: "nessuna"
    } catch (e: Throwable) {
        "non accessibile (${e.javaClass.simpleName})"
    }
}
