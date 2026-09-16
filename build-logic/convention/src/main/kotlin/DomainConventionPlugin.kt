import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `domain:*`: library + Hilt + Jacoco. Sin Compose/Material — el dominio no depende de UI. */
class DomainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("buildlogic.android.library")
                apply("buildlogic.android.hilt")
                apply("buildlogic.android.jacoco")
            }

            extensions.configure<LibraryExtension> {
                buildFeatures.buildConfig = true
            }
        }
    }
}
