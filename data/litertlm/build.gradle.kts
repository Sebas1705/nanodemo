plugins {
    alias(libs.plugins.buildlogic.data)
}

// Gemma 4 E2B en formato .litertlm (Apache 2.0): el repo de Hugging Face es público
// (se verificó con una descarga sin credenciales), así que a diferencia del Tier B
// no hace falta token ni un asset propio en GitHub Releases. Igual se puede
// sobrescribir la URL (LITERTLM_MODEL_URL, por Doppler o local.properties) para
// apuntar a otro modelo/espejo — ver `secret` en el build.gradle.kts raíz.
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String
val defaultModelUrl =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

android {
    defaultConfig {
        buildConfigField("String", "LITERTLM_MODEL_URL", "\"${secret("LITERTLM_MODEL_URL", defaultModelUrl)}\"")
    }
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)

    // Tier D: LiteRT-LM (runtime de LLMs de Google AI Edge, sucesor de MediaPipe LLM Inference).
    implementation(libs.litertlm.android)
}
