plugins {
    alias(libs.plugins.buildlogic.data)
}

// CRASHLYTICS_ENABLED (Doppler o local.properties; por defecto true) apaga la recolección sin
// tocar código — p. ej. en la config `dev` de Doppler para no ensuciar la consola con crashes
// de desarrollo. Ver `secret` en el build.gradle.kts raíz.
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    defaultConfig {
        buildConfigField("boolean", "CRASHLYTICS_ENABLED", secret("CRASHLYTICS_ENABLED", "true").toBoolean().toString())
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.data.firebasecore)

    // Crashlytics SDK. No se aplica el plugin de Gradle de Crashlytics (ver CrashlyticsErrorReporter).
    implementation(libs.firebase.crashlytics)
}
