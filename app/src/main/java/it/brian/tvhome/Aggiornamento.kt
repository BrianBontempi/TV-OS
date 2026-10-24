package it.brian.tvhome

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Scarica l'ultima versione pubblicata su GitHub e la passa all'installatore di Android. */
object Aggiornamento {

    private const val INDIRIZZO = "https://github.com/BrianBontempi/TV-OS/releases/download/ultima/tv-home.apk"
    const val AZIONE_ESITO = "it.brian.tvhome.ESITO_AGGIORNAMENTO"

    fun avvia(activity: Activity, avviso: (String) -> Unit) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            avviso("Attiva \"Installa app sconosciute\" per TV Home Prova, poi premi di nuovo Aggiorna app")
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }
        avviso("Scarico l'ultima versione…")
        val app = activity.applicationContext
        thread {
            try {
                val installatore = app.packageManager.packageInstaller
                val parametri = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = installatore.createSession(parametri)
                installatore.openSession(id).use { sessione ->
                    val connessione = URL(INDIRIZZO).openConnection() as HttpURLConnection
                    connessione.instanceFollowRedirects = true
                    connessione.connectTimeout = 15000
                    connessione.readTimeout = 30000
                    connessione.inputStream.use { ingresso ->
                        sessione.openWrite("tv-home.apk", 0, -1).use { uscita ->
                            ingresso.copyTo(uscita)
                            sessione.fsync(uscita)
                        }
                    }
                    val esito = Intent(app, MainActivity::class.java).setAction(AZIONE_ESITO)
                    val flag = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    sessione.commit(PendingIntent.getActivity(app, id, esito, flag).intentSender)
                }
                Registro.aggiungi(app, Registro.EVENTI, "Aggiornamento scaricato, in installazione")
            } catch (e: Exception) {
                Registro.aggiungi(app, Registro.EVENTI, "Aggiornamento non riuscito (${e.javaClass.simpleName}: ${e.message})")
                activity.runOnUiThread { avviso("Aggiornamento non riuscito: ${e.message}") }
            }
        }
    }

    /** Gestisce la risposta dell'installatore: se serve la conferma, apre la finestra di Android. */
    fun gestisciEsito(activity: Activity, intent: Intent?, avviso: (String) -> Unit) {
        if (intent?.action != AZIONE_ESITO) return
        when (val stato = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val conferma = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (conferma != null) activity.startActivity(conferma)
            }
            PackageInstaller.STATUS_SUCCESS -> avviso("App aggiornata")
            else -> {
                val messaggio = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "codice $stato"
                Registro.aggiungi(activity, Registro.EVENTI, "Installazione non riuscita: $messaggio")
                avviso("Installazione non riuscita: $messaggio")
            }
        }
    }
}
