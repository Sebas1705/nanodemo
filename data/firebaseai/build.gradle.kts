plugins {
    alias(libs.plugins.buildlogic.data)
}

// La configuración de Firebase (FIREBASE_API_KEY / APP_ID / PROJECT_ID) y la inicialización manual
// de FirebaseApp viven ahora en data:firebasecore, compartidas con Crashlytics. Este módulo solo
// usa la FirebaseApp ya creada y, si falta configuración, el tier queda UNAVAILABLE.
dependencies {
    implementation(projects.domain.models)
    implementation(projects.core.common)
    implementation(projects.data.firebasecore)

    // Tier E: Firebase AI Logic (Gemini vía Firebase). `firebase-common` (FirebaseApp/
    // FirebaseOptions) llega transitivo como dependencia de compilación de firebase-ai.
    implementation(libs.firebase.ai)
}
