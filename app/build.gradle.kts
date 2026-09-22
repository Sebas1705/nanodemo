plugins {
    alias(libs.plugins.buildlogic.application)
}

// CI (ver .github/actions/build-release-apk) inyecta estas variables; en local no existen y el build
// sigue igual que siempre (versionCode 1, release firmado con la key de debug).
//  - VERSION_CODE: sube en cada build para que Android acepte instalar la versión nueva encima.
//  - VERSION_NAME: solo la fija la vía de GitHub Release (versión del tag); vacío = 1.0.<VERSION_CODE>.
//  - SIGNING_*: keystore FIJO. Si CI compilara con la key de debug, cada runner generaría una
//    distinta y los testers no podrían actualizar sin desinstalar (la firma no coincidiría).
val ciVersionCode: Int? = System.getenv("VERSION_CODE")?.toIntOrNull()
val ciVersionName: String? = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
val ciKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

// Config de Firebase (Doppler o local.properties — ver `secret` en el build.gradle.kts raíz).
@Suppress("UNCHECKED_CAST")
val secret = rootProject.extra["secret"] as (String, String) -> String

android {
    namespace = "es.sebas1705.nanodemo"

    defaultConfig {
        applicationId = "es.sebas1705.nanodemo"
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: if (ciVersionCode != null) "1.0.$ciVersionCode" else "1.0"

        // Lo mismo que generaría el plugin com.google.gms.google-services a partir del JSON, pero desde
        // los secretos del build. Firebase Analytics lo exige: lee `google_app_id` de las resources y
        // NO de FirebaseOptions (verificado en el bytecode de play-services-measurement), así que la
        // inicialización manual de data:firebasecore por sí sola no le basta. Además, con estas tres
        // resources FirebaseInitProvider crea la FirebaseApp antes de Application.onCreate(), y
        // Crashlytics captura también los crashes del arranque. Si falta la config quedan vacías y
        // Firebase simplemente no se inicializa.
        resValue("string", "google_app_id", secret("FIREBASE_APP_ID", ""))
        resValue("string", "google_api_key", secret("FIREBASE_API_KEY", ""))
        resValue("string", "project_id", secret("FIREBASE_PROJECT_ID", ""))
    }

    // Desde AGP 8 las resources generadas con resValue vienen desactivadas por defecto.
    buildFeatures {
        resValues = true
    }

    signingConfigs {
        if (ciKeystorePath != null) {
            create("ci") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Sin keystore de CI (build local) se firma con la de debug, así `assembleRelease`
            // funciona en cualquier máquina sin configurar nada.
            signingConfig = signingConfigs.getByName(if (ciKeystorePath != null) "ci" else "debug")
        }
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.common)
    implementation(projects.feature.main)
    // Proveedores de ErrorReporter (Crashlytics) y AnalyticsTracker (Firebase Analytics): :app los
    // enlaza al grafo de Hilt. Para cambiar de proveedor basta con sustituir el módulo.
    implementation(projects.data.crashlytics)
    implementation(projects.data.analytics)

    implementation(libs.androidx.activity.compose)
}
