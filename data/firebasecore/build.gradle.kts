plugins {
    alias(libs.plugins.buildlogic.data)
}

// Base común de todo lo que usa Firebase (Firebase AI Logic, Crashlytics): inicializa la app
// "default" de FirebaseApp a mano con FirebaseOptions, sin el plugin com.google.gms.google-services
// ni google-services.json — así el proyecto compila igual aunque nadie haya creado un proyecto
// de Firebase, y CI puede inyectar los valores desde Doppler (variables de entorno) sin
// commitear el JSON. Los tres valores salen de la consola de Firebase (Configuración del
// proyecto -> tu app Android) y se leen de Doppler o de local.properties — ver `secret` en el
// build.gradle.kts raíz. Si falta alguno, Firebase queda sin inicializar y sus consumidores se
// desactivan solos (el tier E queda UNAVAILABLE, Crashlytics no arranca) en vez de crashear.
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    defaultConfig {
        listOf("FIREBASE_API_KEY", "FIREBASE_APP_ID", "FIREBASE_PROJECT_ID").forEach { key ->
            buildConfigField("String", key, "\"${secret(key, "")}\"")
        }
    }
}

dependencies {
    // FirebaseApp / FirebaseOptions. Misma versión que arrastran firebase-ai y firebase-crashlytics.
    implementation(libs.firebase.common)
}
