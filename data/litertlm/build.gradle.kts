import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.buildlogic.data)
}

// Gemma 4 E2B en formato .litertlm (Apache 2.0): el repo de Hugging Face es público
// (se verificó con una descarga sin credenciales), así que a diferencia del Tier B
// no hace falta token ni un asset propio en GitHub Releases. Igual se puede
// sobrescribir la URL desde local.properties (LITERTLM_MODEL_URL) para apuntar a
// otro modelo/espejo.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}
val defaultModelUrl =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

android {
    defaultConfig {
        buildConfigField(
            "String",
            "LITERTLM_MODEL_URL",
            "\"${localProperties.getProperty("LITERTLM_MODEL_URL", defaultModelUrl)}\"",
        )
    }
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)

    // Tier D: LiteRT-LM (runtime de LLMs de Google AI Edge, sucesor de MediaPipe LLM Inference).
    implementation(libs.litertlm.android)
}
