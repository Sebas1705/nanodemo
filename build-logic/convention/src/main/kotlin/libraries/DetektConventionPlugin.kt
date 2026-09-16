package libraries

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Detekt con reglas por defecto (no hay `config/detekt/detekt.yml` propio
 * todavía). No falla el build (`ignoreFailures = true`): es un análisis
 * informativo, no un gate.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            configure<DetektExtension> {
                buildUponDefaultConfig = true
                allRules = false
                parallel = true
                ignoreFailures = true
                basePath = rootDir.absolutePath
            }
        }
    }
}
