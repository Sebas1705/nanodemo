plugins {
    alias(libs.plugins.buildlogic.data)
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)

    // Tier A: ML Kit GenAI Prompt API (Gemini Nano on-device vía AICore).
    implementation(libs.mlkit.genai.prompt)
}
