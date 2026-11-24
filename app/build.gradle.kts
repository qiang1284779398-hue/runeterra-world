import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Decode the committed debug keystore (base64 text form) at configuration time;
// the stableDebug signing config below points at the decoded file.
val runeterraEncodedKeystore = file("debug.keystore.b64")
val runeterraDecodedKeystore = file("debug.keystore")
if (runeterraEncodedKeystore.exists() &&
    (!runeterraDecodedKeystore.exists() || runeterraEncodedKeystore.lastModified() > runeterraDecodedKeystore.lastModified())) {
    runeterraDecodedKeystore.writeBytes(Base64.getDecoder().decode(runeterraEncodedKeystore.readText().trim()))
}

android {
    namespace = "com.runeterra.local"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.runeterra.local"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    // One fixed debug signing key for every build. CI runners used to sign with
    // their own ephemeral debug keystore, so each build had a different signature
    // and Android refused to install it over the previous version. The keystore
    // itself is committed as debug.keystore.b64 (a debug-only key for this local
    // app) and decoded by the script above at configuration time.
    signingConfigs {
        create("stableDebug") {
            storeFile = file("debug.keystore")
            storePassword = "runeterra"
            keyAlias = "runeterra-debug"
            keyPassword = "runeterra"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("stableDebug") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}


dependencies {
    // Prebuilt llama.cpp JNI engine; GGUF inference runs entirely on-device.
    implementation(files("libs/llama-android-fixed.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
