pluginManagement {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BoostyVP"

include(":app")
include(":core:model")
include(":core:domain")
include(":core:network")
include(":core:auth")
include(":core:data")
include(":core:ui")
include(":localization:api", ":localization:ru", ":localization:en", ":core:localization")
include(":feature:pairing")
include(":feature:home")
include(":feature:blog")
include(":feature:player")
