import com.android.build.api.dsl.ApplicationExtension
import es.sebas1705.convention.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `:app`: com.android.application + Hilt + Compose + Material + Lifecycle + Jacoco + Detekt.
 * Sin flavors ni Firebase (nanodemo no los usa — ver plan de migración).
 */
class AppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("com.android.application")
                apply("buildlogic.android.hilt")
                apply("buildlogic.android.compose")
                apply("buildlogic.android.material")
                apply("buildlogic.android.lifecycle")
                apply("buildlogic.android.jacoco")
                apply("buildlogic.android.detekt")
            }

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 36
            }
        }
    }
}
