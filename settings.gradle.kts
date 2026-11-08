pluginManagement {
    // Convention plugins with the Android settings the modules share.
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
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tokkl-android"
include(":app")
include(":core:model")
include(":core:api")
include(":core:time")
include(":core:ui")
include(":core:platform")
include(":core:session")
include(":core:cache")
include(":core:timerstate")
include(":core:timersync")
include(":core:catalog")
include(":core:entryfields")
include(":core:trust")
include(":core:timernotice")
include(":core:navigation")
include(":core:testing")
include(":feature:signin")
include(":feature:timer")
include(":feature:pomodoro")
include(":feature:reports")
include(":feature:projects")
include(":feature:clients")
include(":feature:tags")
include(":feature:trash")
include(":feature:settings")
include(":feature:sync")
include(":feature:notification")
include(":feature:shortcuts")
include(":feature:tile")
include(":feature:widget")
