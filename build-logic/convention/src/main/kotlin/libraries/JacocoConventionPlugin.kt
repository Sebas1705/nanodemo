package libraries

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

/**
 * Solo aplica el plugin `jacoco` y fija su versión — a diferencia del
 * template (que registra tasks de reporte/verificación con un mínimo de
 * cobertura del 90%), nanodemo todavía no tiene tests, así que no hay nada
 * que reportar/verificar todavía. Cuando se agreguen tests, se puede ampliar
 * este plugin con las tasks `jacocoTestReport`/`jacocoTestCoverageVerification`.
 */
class JacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jacoco")
            configure<JacocoPluginExtension> {
                toolVersion = "0.8.14"
            }
        }
    }
}
