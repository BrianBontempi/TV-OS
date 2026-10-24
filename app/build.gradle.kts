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
        versionCode = 1
        versionName = "0.1-prova"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
