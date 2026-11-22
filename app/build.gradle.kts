plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.runeterra.local"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.runeterra.local"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}


configurations.configureEach {
    resolutionStrategy.force("androidx.core:core:1.15.0")
}

dependencies {
    // AiKit bundles llama.cpp JNI and runs GGUF inference fully on-device.
    implementation("io.github.its-hazratbilal:android-ai-kit-chat:1.0.0")
}
