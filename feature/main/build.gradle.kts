plugins {
    alias(libs.plugins.buildlogic.feature)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)

    implementation(projects.feature.chat)
    implementation(projects.feature.compare)
    implementation(projects.feature.config)
    implementation(projects.feature.device)
    implementation(projects.feature.stats)
    implementation(projects.feature.history)
}
