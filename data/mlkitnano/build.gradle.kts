plugins {
    alias(libs.plugins.buildlogic.data)
}

dependencies {
    implementation(projects.domain.models)

    // Tier A: ML Kit GenAI Prompt API (Gemini Nano on-device vía AICore).
    implementation(libs.mlkit.genai.prompt)
}
