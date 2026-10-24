plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.brian.tvhome"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.brian.tvhome"
        minSdk = 28
        targetSdk = 34
        // Il numero cresce a ogni compilazione su GitHub, così ogni versione aggiorna la precedente.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 3
        versionCode = build
        versionName = "0.3-prova (build $build)"
    }

    // Firma fissa: senza, ogni compilazione avrebbe una chiave nuova e Android rifiuterebbe l'aggiornamento.
    signingConfigs {
        create("fissa") {
            storeFile = file("firma.keystore")
            storePassword = "tvhome-firma"
            keyAlias = "tvhome"
            keyPassword = "tvhome-firma"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fissa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
