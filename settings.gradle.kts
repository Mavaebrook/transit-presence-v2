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

rootProject.name = "TransitPresence"

// Core
include(":core:core-model")
include(":core:core-fsm")
include(":core:core-common")

// Data
include(":data:data-gtfs")
include(":data:data-gtfsrt")
include(":data:data-location")

// Features
include(":feature:feature-map")
include(":feature:feature-riding")
include(":feature:feature-settings")

// Service
include(":service:service-tracking")

// App shell
include(":app")
