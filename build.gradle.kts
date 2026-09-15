// Archivo build.gradle.kts de nivel raíz.
// Declara (sin aplicar) los plugins que usa el módulo :app, con sus versiones.
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
