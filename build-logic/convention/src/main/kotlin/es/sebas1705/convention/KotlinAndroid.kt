package es.sebas1705.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Configura compileSdk/minSdk/Java+Kotlin target para un módulo Android.
 *
 * A diferencia del template (que aplica el plugin `org.jetbrains.kotlin.android`
 * y configura las tasks `KotlinCompile` directamente), este proyecto usa el
 * Kotlin built-in de AGP 9: al aplicar `com.android.application`/`com.android.library`,
 * AGP registra por su cuenta una extensión de proyecto llamada "kotlin"
 * (`KotlinAndroidProjectExtension`, ver `BuiltInKotlinServices.initBuiltInKotlinSupport`
 * en las fuentes de `com.android.tools.build:gradle:9.4.0`) — se configura esa
 * extensión en vez de un bloque `kotlin{}` anidado dentro de `android{}`.
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk = 37

        // AGP 9's `CommonExtension` ya no expone los overloads `defaultConfig { }` /
        // `compileOptions { }` con lambda (esos eran parte de la interfaz genérica
        // de AGP 8.x) — solo quedan las propiedades planas.
        defaultConfig.minSdk = 31

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }

    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
