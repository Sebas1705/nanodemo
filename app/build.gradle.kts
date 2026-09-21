plugins {
    alias(libs.plugins.buildlogic.application)
}

android {
    namespace = "es.sebas1705.nanodemo"

    defaultConfig {
        applicationId = "es.sebas1705.nanodemo"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.feature.main)

    implementation(libs.androidx.activity.compose)
}
