plugins {
    alias(libs.plugins.buildlogic.data)
}

dependencies {
    implementation(projects.domain.models)
    // Búsqueda semántica del historial (LocalEmbeddingService, ver appendMessages/searchConversations).
    implementation(projects.data.embeddings)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
