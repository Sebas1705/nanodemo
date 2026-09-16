plugins {
    alias(libs.plugins.buildlogic.core)
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.domain.models)
}
