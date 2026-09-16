plugins {
    alias(libs.plugins.buildlogic.application)
}

android {
    namespace = "com.example.geminifnanodemo"

    defaultConfig {
        applicationId = "com.example.geminifnanodemo"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.feature.main)

    implementation(libs.androidx.activity.compose)
}
