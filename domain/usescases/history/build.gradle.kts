plugins {
    alias(libs.plugins.buildlogic.domain)
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.data.history)
}
