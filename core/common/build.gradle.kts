plugins {
    alias(libs.plugins.buildlogic.core)
}

// GOOGLE_CSE_API_KEY/GOOGLE_CSE_ENGINE_ID vienen de Doppler (o local.properties) — igual que el
// resto de los secretos del proyecto, ver `secret` acá abajo. A diferencia de GEMINI_API_KEY, NO
// son obligatorios en CI (ver .github/actions/build-release-apk): si faltan, WebSearchTool.isConfigured
// queda en false y la búsqueda emulada de los tiers locales se apaga sola (mensaje, no crash) — es
// un secreto que el usuario del template provisiona cuando quiere esta feature, no uno core del demo.
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    defaultConfig {
        buildConfigField("String", "GOOGLE_CSE_API_KEY", "\"${secret("GOOGLE_CSE_API_KEY", "")}\"")
        buildConfigField("String", "GOOGLE_CSE_ENGINE_ID", "\"${secret("GOOGLE_CSE_ENGINE_ID", "")}\"")
    }
}
