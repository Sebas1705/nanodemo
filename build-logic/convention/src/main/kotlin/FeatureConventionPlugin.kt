import com.android.build.api.dsl.LibraryExtension
import es.sebas1705.convention.implementation
import es.sebas1705.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `feature:*`: library + Hilt + Jacoco + Compose + Material + Lifecycle +
 * Navigation. `kotlinx.serialization` se aplica acá (no solo en
 * `feature:main`, que es el único que de verdad declara rutas `@Serializable`)
 * por simplicidad — es gratis para las demás features y evita un plugin
 * más para un solo módulo.
 */
class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("buildlogic.android.library")
                apply("buildlogic.android.hilt")
                apply("buildlogic.android.jacoco")
                apply("buildlogic.android.compose")
                apply("buildlogic.android.material")
                apply("buildlogic.android.lifecycle")
                apply("buildlogic.android.navigation")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            extensions.configure<LibraryExtension> {
                buildFeatures.buildConfig = true
            }

            dependencies {
                implementation(libs.findLibrary("kotlinx-serialization-json").get())
            }
        }
    }
}
