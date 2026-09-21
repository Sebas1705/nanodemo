plugins {
    alias(libs.plugins.buildlogic.data)
}

// GEMINI_API_KEY viene de Doppler (variable de entorno) o de local.properties — ver `secret` en el
// build.gradle.kts raíz. Si no está, este tier se marca UNAVAILABLE en vez de intentar una
// llamada que fallaría igualmente (ver CloudGeminiBackend.checkAvailability()).
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    defaultConfig {
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY", "")}\"")
    }
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)
    // Tier C llama directo al REST API de Gemini con HttpURLConnection + org.json,
    // ambos ya incluidos en el SDK de Android — sin dependencias nuevas (ver
    // CloudGeminiBackend.kt para el porqué no se usa el SDK oficial).
}
