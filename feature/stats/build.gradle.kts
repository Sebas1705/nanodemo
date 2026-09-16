plugins {
    alias(libs.plugins.buildlogic.feature)
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.domain.usescases.inference)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
}
