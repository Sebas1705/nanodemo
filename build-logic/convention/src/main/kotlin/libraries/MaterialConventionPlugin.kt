package libraries

import es.sebas1705.convention.implementation
import es.sebas1705.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Material3 + íconos extendidos (verificados en el AAR real de la versión
 * que fija el BOM). No se incluye Material clásico (`com.google.android.material`)
 * ni el adaptive suite del template: nanodemo maneja la adaptabilidad a mano
 * con `ScreenSizeClass` (ver `core:designsystem`), no los necesita.
 */
class MaterialConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                implementation(libs.findLibrary("compose-material3").get())
                implementation(libs.findLibrary("compose-material-icons-extended").get())
            }
        }
    }
}
