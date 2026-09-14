import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Lee las credenciales del keystore desde keystore.properties (no versionado en Git)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

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
            storeFile = file(keystoreProperties["KEYSTORE_FILE"] as String? ?: "release.keystore")
            storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String?
                ?: System.getenv("KEYSTORE_PASSWORD")
            keyAlias = keystoreProperties["KEY_ALIAS"] as String?
                ?: System.getenv("KEY_ALIAS")
            keyPassword = keystoreProperties["KEY_PASSWORD"] as String?
                ?: System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
}
