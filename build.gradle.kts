import java.io.FileInputStream
import java.util.Properties

// Archivo build.gradle.kts de nivel raíz.
// Declara (sin aplicar) los plugins "reales" que usan los módulos, para que
// se resuelvan una sola vez y queden disponibles para que los convention
// plugins de build-logic los apliquen por id (pluginManager.apply(...)) sin
// tener que repetir la versión en cada módulo.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

// Secretos/config que se compilan en BuildConfig (API keys, URLs de modelos, ids de Firebase).
// Los módulos data:* los piden con `secret("CLAVE", "valor por defecto")`. Orden de búsqueda:
//   1. Variable de entorno — es lo que inyecta `doppler run -- ./gradlew ...` (local y CI).
//   2. local.properties (gitignored) — lo que lee Android Studio; se genera desde Doppler con
//      scripts/doppler-sync-local-properties.sh, o se puede escribir a mano.
//   3. El valor por defecto.
// Si una clave falta en todos lados queda vacía y el tier correspondiente se marca "no disponible".
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}
extra["secret"] = { key: String, default: String ->
    System.getenv(key)?.takeIf { it.isNotBlank() } ?: localProperties.getProperty(key) ?: default
}
