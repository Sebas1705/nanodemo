package libraries

import es.sebas1705.convention.implementation
import es.sebas1705.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class LifecycleConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                implementation(libs.findLibrary("lifecycle-runtime-ktx").get())
                implementation(libs.findLibrary("lifecycle-viewmodel-compose").get())
                implementation(libs.findLibrary("lifecycle-runtime-compose").get())
            }
        }
    }
}
