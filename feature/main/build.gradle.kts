plugins {
    alias(libs.plugins.buildlogic.feature)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.domain.models)
    // Solo para el chip de tier activo de la barra superior compartida (ver MainViewModel/AppNav).
    implementation(projects.domain.usescases.inference)

    implementation(projects.feature.chat)
    implementation(projects.feature.compare)
    implementation(projects.feature.config)
    implementation(projects.feature.device)
    implementation(projects.feature.stats)
    implementation(projects.feature.history)
}
