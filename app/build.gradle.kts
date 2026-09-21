plugins {
    alias(libs.plugins.buildlogic.application)
}

// CI (ver .github/workflows/distribute.yml) inyecta estas variables; en local no existen y el build
// sigue igual que siempre (versionCode 1, release firmado con la key de debug).
//  - VERSION_CODE: sube en cada build para que Android acepte instalar la versión nueva encima.
//  - SIGNING_*: keystore FIJO. Si CI compilara con la key de debug, cada runner generaría una
//    distinta y los testers no podrían actualizar sin desinstalar (la firma no coincidiría).
val ciVersionCode: Int? = System.getenv("VERSION_CODE")?.toIntOrNull()
val ciKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "es.sebas1705.nanodemo"

    defaultConfig {
        applicationId = "es.sebas1705.nanodemo"
        versionCode = ciVersionCode ?: 1
        versionName = if (ciVersionCode != null) "1.0.$ciVersionCode" else "1.0"
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
    implementation(projects.feature.main)

    implementation(libs.androidx.activity.compose)
}
