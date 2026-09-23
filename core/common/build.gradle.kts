plugins {
    alias(libs.plugins.buildlogic.core)
}

// BRAVE_SEARCH_API_KEY viene de Doppler (o local.properties) — igual que el resto de los secretos
// del proyecto, ver `secret` acá abajo. A diferencia de GEMINI_API_KEY, NO es obligatorio en CI (ver
// .github/actions/build-release-apk): si falta, WebSearchTool.isConfigured queda en false y la
// búsqueda emulada de los tiers locales se apaga sola (mensaje, no crash) — es un secreto que el
// usuario del template provisiona cuando quiere esta feature, no uno core del demo.
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    defaultConfig {
        buildConfigField("String", "BRAVE_SEARCH_API_KEY", "\"${secret("BRAVE_SEARCH_API_KEY", "")}\"")
    }
}
