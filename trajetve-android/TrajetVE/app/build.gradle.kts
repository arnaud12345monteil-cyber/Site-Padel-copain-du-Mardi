import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Clé de signature : la même à chaque compilation (nécessaire pour la connexion Google et les mises à jour)
val ks = Properties().apply { rootProject.file("keystore/keystore.properties").inputStream().use { load(it) } }
val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "fr.trajetve.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.trajetve.app"
        minSdk = 26
        targetSdk = 34
        versionCode = build
        versionName = "1.0.$build"
    }

    signingConfigs {
        create("trajetve") {
            storeFile = rootProject.file("keystore/trajetve.jks")
            storePassword = ks.getProperty("storePassword")
            keyAlias = ks.getProperty("keyAlias")
            keyPassword = ks.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("trajetve")
        }
        debug {
            signingConfig = signingConfigs.getByName("trajetve")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Android Auto
    implementation("androidx.car.app:app:1.4.0")
    // Écran du téléphone
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.browser:browser:1.8.0")
    // Firebase (même projet que le site)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // Connexion Google
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
