package libraries

import es.sebas1705.convention.implementation
import es.sebas1705.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt puro con KSP (sin kapt, ver [LibraryConventionPlugin]). El plugin de
 * Gradle de Hilt se aplica a todo módulo Android (no solo `:app`) porque
 * cada capa puede aportar sus propios `@Module`/`@Binds`.
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            dependencies {
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }

            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies {
                    implementation(libs.findLibrary("hilt-android").get())
                }
            }
        }
    }
}
