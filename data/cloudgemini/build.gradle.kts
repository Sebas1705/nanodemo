import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.buildlogic.data)
}

// GEMINI_API_KEY vive en local.properties (gitignored) — si no está
// configurada, este tier se marca UNAVAILABLE en vez de intentar una llamada
// que fallaría igualmente (ver CloudGeminiBackend.checkAvailability()).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

android {
    defaultConfig {
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"",
        )
    }
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)
    // Tier C llama directo al REST API de Gemini con HttpURLConnection + org.json,
    // ambos ya incluidos en el SDK de Android — sin dependencias nuevas (ver
    // CloudGeminiBackend.kt para el porqué no se usa el SDK oficial).
}
