plugins {
    alias(libs.plugins.buildlogic.domain)
}

dependencies {
    implementation(projects.domain.models)
    // Solo para la interfaz IInferenceRepository (ver AGENTS/plan: domain depende
    // de la interfaz de repositorio, no de su implementación concreta).
    implementation(projects.data.repositories)
}
