plugins {
    alias(libs.plugins.buildlogic.data)
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.data.mlkitnano)
    implementation(projects.data.mediapipe)
    implementation(projects.data.cloudgemini)
    implementation(projects.data.litertlm)
    implementation(projects.data.firebaseai)
    implementation(projects.data.history)

    // FileProvider (compartir el CSV de estadísticas exportado).
    implementation(libs.androidx.core.ktx)
}
