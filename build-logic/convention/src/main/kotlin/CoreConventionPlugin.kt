import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `core:*`: library + Hilt + Jacoco + Compose + Material. */
class CoreConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("buildlogic.android.library")
                apply("buildlogic.android.hilt")
                apply("buildlogic.android.jacoco")
                apply("buildlogic.android.compose")
                apply("buildlogic.android.material")
            }

            extensions.configure<LibraryExtension> {
                buildFeatures.buildConfig = true
            }
        }
    }
}
