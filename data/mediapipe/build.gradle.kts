plugins {
    alias(libs.plugins.buildlogic.data)
}

// URLs/token vienen de Doppler (variables de entorno) o de local.properties — ver `secret` en el
// build.gradle.kts raíz. Si a un modelo le falta alguna, queda vacía y ese modelo se marca
// UNAVAILABLE en vez de crashear (ver MediaPipeBackend.checkAvailability()).
// MEDIAPIPE_MODEL_URL (sin sufijo) es la del modelo original (Gemma 3 1B) — se mantiene por
// compatibilidad con el local.properties que ya tenía cada quien configurado.
// Gemma 2 2B (2.71GB) supera el límite de 2GB por asset de GitHub Releases,
// así que se subió partido en 2 (PART1/PART2) — MediaPipeBackend descarga
// ambas partes y las concatena en un solo .task antes de cargarlo. El token
// es el mismo para todos (mismo repo/release de GitHub).
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    defaultConfig {
        buildConfigField("String", "MEDIAPIPE_MODEL_URL_GEMMA_3_1B", "\"${secret("MEDIAPIPE_MODEL_URL", "")}\"")
        buildConfigField(
            "String",
            "MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART1",
            "\"${secret("MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART1", "")}\"",
        )
        buildConfigField(
            "String",
            "MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART2",
            "\"${secret("MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART2", "")}\"",
        )
        buildConfigField("String", "MEDIAPIPE_MODEL_TOKEN", "\"${secret("MEDIAPIPE_MODEL_TOKEN", "")}\"")
    }
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)

    // Tier B: MediaPipe LLM Inference (Gemma local vía LiteRT). Google marca esta
    // API como "maintenance-only" recomendando migrar a LiteRT-LM a futuro, pero
    // es la más documentada y estable hoy — ver README para el link de migración.
    implementation(libs.mediapipe.tasks.genai)
}
