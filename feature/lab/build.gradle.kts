plugins {
    alias(libs.plugins.buildlogic.feature)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.domain.models)
    implementation(projects.domain.usescases.inference)
    // On-device text embedder (MediaPipe) for the RAG experiment; it has no domain use case, so the Lab
    // injects the service directly, like data:history does.
    implementation(projects.data.embeddings)
}
