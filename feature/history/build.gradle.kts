plugins {
    alias(libs.plugins.buildlogic.feature)
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.domain.usescases.history)
    // Solo para DeleteConversationUseCase: el borrado pasa por IInferenceRepository, ver su comentario.
    implementation(projects.domain.usescases.inference)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
}
