plugins {
    alias(libs.plugins.buildlogic.data)
}

dependencies {
    implementation(projects.domain.models)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
