plugins {
    alias(libs.plugins.buildlogic.data)
}

dependencies {
    implementation(projects.core.common)

    // Búsqueda semántica local del historial: TextEmbedder es un paquete de MediaPipe Tasks
    // separado de tasks-genai (Tier B) — mismo proveedor, dependencia propia.
    implementation(libs.mediapipe.tasks.text)
}
