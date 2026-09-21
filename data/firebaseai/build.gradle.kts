import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.buildlogic.data)
}

// Firebase AI Logic se inicializa a mano con FirebaseOptions (ver FirebaseAiBackend), sin el
// plugin com.google.gms.google-services ni google-services.json — así el proyecto sigue
// compilando aunque nadie haya creado un proyecto de Firebase. Los tres valores salen de
// la consola de Firebase (Configuración del proyecto -> tu app Android) y van en
// local.properties; si falta alguno, el tier queda UNAVAILABLE en vez de crashear.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

android {
    defaultConfig {
        listOf("FIREBASE_API_KEY", "FIREBASE_APP_ID", "FIREBASE_PROJECT_ID").forEach { key ->
            buildConfigField("String", key, "\"${localProperties.getProperty(key, "")}\"")
        }
    }
}

dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)

    // Tier E: Firebase AI Logic (Gemini vía Firebase). `firebase-common` (FirebaseApp/
    // FirebaseOptions) llega transitivo como dependencia de compilación de firebase-ai.
    implementation(libs.firebase.ai)
}
