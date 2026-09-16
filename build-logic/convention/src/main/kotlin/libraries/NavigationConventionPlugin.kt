package libraries

import es.sebas1705.convention.implementation
import es.sebas1705.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Navigation 3 (reemplaza a Navigation Compose 2.8.9, que usaba nanodemo antes de esta migración). */
class NavigationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                implementation(libs.findLibrary("navigation3-runtime").get())
                implementation(libs.findLibrary("navigation3-ui").get())
                implementation(libs.findLibrary("lifecycle-viewmodel-navigation3").get())
                // hiltViewModel() para pantallas en un NavEntry de Navigation 3
                // (distinto del hiltViewModel() de Navigation 2 — ver libs.versions.toml).
                implementation(libs.findLibrary("hilt-lifecycle-viewmodel-compose").get())
            }
        }
    }
}
