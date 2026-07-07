pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Stripe Terminal SDK (stripeterminal-core) is published directly to Maven Central.
        mavenCentral()
    }
}

rootProject.name = "Donation Terminal"
include(":app")
