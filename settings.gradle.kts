pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GeminiNanoDemo"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:common")
include(":core:ui")
include(":core:designsystem")

include(":domain:models")
include(":domain:usescases:inference")
include(":domain:usescases:history")

include(":data:mlkitnano")
include(":data:mediapipe")
include(":data:cloudgemini")
include(":data:litertlm")
include(":data:firebaseai")
include(":data:firebasecore")
include(":data:crashlytics")
include(":data:repositories")
include(":data:history")

include(":feature:chat")
include(":feature:compare")
include(":feature:config")
include(":feature:device")
include(":feature:stats")
include(":feature:history")
include(":feature:main")
