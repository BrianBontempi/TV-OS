plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.brian.pokebonk"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.brian.pokebonk"
        // La TV è una BRAVIA XR-65A90J con Android TV 12 (API 31): minSdk e targetSdk come TV Home.
        minSdk = 28
        targetSdk = 34
        // Il numero cresce a ogni compilazione su GitHub, così ogni versione aggiorna la precedente.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0 (build $build)"
    }

    // Firma fissa: senza, ogni compilazione avrebbe una chiave nuova e Android rifiuterebbe l'aggiornamento.
    signingConfigs {
        create("fissa") {
            storeFile = file("firma.keystore")
            storePassword = "pokebonk-firma"
            keyAlias = "pokebonk"
            keyPassword = "pokebonk-firma"
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
