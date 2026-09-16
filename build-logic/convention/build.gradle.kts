plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        // Módulos (aplican com.android.application / com.android.library + lo que corresponda por capa)
        register("androidApplication") {
            id = "buildlogic.android.application"
            implementationClass = "AppConventionPlugin"
        }
        register("androidLibrary") {
            id = "buildlogic.android.library"
            implementationClass = "libraries.LibraryConventionPlugin"
        }
        register("androidCore") {
            id = "buildlogic.android.core"
            implementationClass = "CoreConventionPlugin"
        }
        register("androidDomain") {
            id = "buildlogic.android.domain"
            implementationClass = "DomainConventionPlugin"
        }
        register("androidData") {
            id = "buildlogic.android.data"
            implementationClass = "DataConventionPlugin"
        }
        register("androidFeature") {
            id = "buildlogic.android.feature"
            implementationClass = "FeatureConventionPlugin"
        }

        // Librerías (piezas reusables que las capas de arriba combinan)
        register("androidHilt") {
            id = "buildlogic.android.hilt"
            implementationClass = "libraries.HiltConventionPlugin"
        }
        register("androidCompose") {
            id = "buildlogic.android.compose"
            implementationClass = "libraries.ComposeConventionPlugin"
        }
        register("androidMaterial") {
            id = "buildlogic.android.material"
            implementationClass = "libraries.MaterialConventionPlugin"
        }
        register("androidLifecycle") {
            id = "buildlogic.android.lifecycle"
            implementationClass = "libraries.LifecycleConventionPlugin"
        }
        register("androidNavigation") {
            id = "buildlogic.android.navigation"
            implementationClass = "libraries.NavigationConventionPlugin"
        }
        register("androidJacoco") {
            id = "buildlogic.android.jacoco"
            implementationClass = "libraries.JacocoConventionPlugin"
        }
        register("androidDetekt") {
            id = "buildlogic.android.detekt"
            implementationClass = "libraries.DetektConventionPlugin"
        }
    }
}
