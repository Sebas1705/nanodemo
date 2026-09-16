package libraries

import com.android.build.api.dsl.CommonExtension
import es.sebas1705.convention.debugImplementation
import es.sebas1705.convention.implementation
import es.sebas1705.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Toolkit base de Compose (BOM + ui/tooling/animation). Los widgets de
 * Material van en [MaterialConventionPlugin] aparte.
 *
 * A diferencia del template (que fija versiones individuales de cada
 * artefacto `androidx.compose.*`), nanodemo usa el BOM
 * (`androidx.compose:compose-bom`) — ya verificado y funcionando antes de
 * esta migración; no hace falta reemplazarlo por versiones sueltas.
 */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.findByType(CommonExtension::class.java)?.let {
                it.buildFeatures.compose = true
            }

            dependencies {
                implementation(platform(libs.findLibrary("compose-bom").get()))
                implementation(libs.findLibrary("compose-ui").get())
                implementation(libs.findLibrary("compose-ui-tooling-preview").get())
                implementation(libs.findLibrary("compose-animation").get())
                debugImplementation(libs.findLibrary("compose-ui-tooling").get())
            }
        }
    }
}
