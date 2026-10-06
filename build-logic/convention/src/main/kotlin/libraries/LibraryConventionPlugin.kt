package libraries

import com.android.build.api.dsl.LibraryExtension
import es.sebas1705.convention.configureKotlinAndroid
import es.sebas1705.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Configuración común para módulos Android library de nanodemo.
 *
 * Deliberadamente NO aplica `org.jetbrains.kotlin.android`: el proyecto usa
 * el Kotlin built-in de AGP 9 (ver [configureKotlinAndroid]), y ese plugin
 * clásico es incompatible con built-in Kotlin desde AGP 9.
 */
class LibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("buildlogic.android.detekt")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // Los tests unitarios corren en la JVM con un android.jar de stubs: sin esto, cualquier
                // `Log.d` del código bajo prueba lanza "not mocked" en vez de ser un no-op.
                testOptions.unitTests.isReturnDefaultValues = true
                // Cada módulo de librería usa un namespace propio derivado de su path
                // (ej. ":core:common" -> "es.sebas1705.nanodemo.core.common").
                namespace = "es.sebas1705.nanodemo" +
                    path.trim(':').replace(':', '.').let { if (it.isEmpty()) "" else ".$it" }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
            }
        }
    }
}
