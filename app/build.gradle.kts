plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.geminifnanodemo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.geminifnanodemo"
        // El Prompt API en sí solo pide API 26+, pero Gemini Nano solo estará
        // realmente disponible (checkStatus() == AVAILABLE/DOWNLOADABLE) en
        // dispositivos flagship 2026 con >=12GB de RAM (Pixel 10, Galaxy S26...).
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ML Kit GenAI Prompt API (acceso a Gemini Nano on-device).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
