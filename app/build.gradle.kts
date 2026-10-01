import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Lee las credenciales del keystore desde keystore.properties (no versionado en Git) o variables de entorno
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val keystorePath = keystoreProperties["KEYSTORE_FILE"] as String? ?: System.getenv("KEYSTORE_FILE") ?: "app/release.keystore"
val keystorePass = keystoreProperties["KEYSTORE_PASSWORD"] as String? ?: System.getenv("KEYSTORE_PASSWORD")
val keyAliasName = keystoreProperties["KEY_ALIAS"] as String? ?: System.getenv("KEY_ALIAS")
val keyPass = keystoreProperties["KEY_PASSWORD"] as String? ?: System.getenv("KEY_PASSWORD")

val isSigningConfigured = !keystorePass.isNullOrBlank() && !keyAliasName.isNullOrBlank()

android {
    namespace = "com.domotica.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.domotica.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystorePath)
            storePassword = keystorePass
            keyAlias = keyAliasName
            keyPassword = keyPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Firma estándar de depuración por defecto
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // OkHttp for WebSocket and REST client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase Cloud Messaging (FCM)
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // WireGuard Android Tunnel SDK
    implementation("com.wireguard.android:tunnel:1.0.20230706")

    // QR Code Scanning (ZXing Embedded)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
