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
