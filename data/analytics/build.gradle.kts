plugins {
    alias(libs.plugins.buildlogic.data)
}

// ANALYTICS_ENABLED (Doppler o local.properties; por defecto true) apaga la recolección sin tocar
// código — p. ej. en la config `dev` de Doppler. Ver `secret` en el build.gradle.kts raíz.
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    defaultConfig {
        buildConfigField("boolean", "ANALYTICS_ENABLED", secret("ANALYTICS_ENABLED", "true").toBoolean().toString())
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.data.firebasecore)

    // Firebase Analytics. Requiere la resource `google_app_id` (la genera :app; ver su build.gradle.kts).
    implementation(libs.firebase.analytics)
}
