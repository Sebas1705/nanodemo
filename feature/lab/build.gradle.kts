plugins {
    alias(libs.plugins.buildlogic.feature)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.domain.models)
    implementation(projects.domain.usescases.inference)
}
