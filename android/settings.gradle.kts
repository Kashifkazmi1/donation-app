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
        mavenCentral()
        // Stripe Terminal SDK is published to Stripe's own Maven repository.
        maven { url = uri("https://mvn-central-stripe.stripe.com/release") }
    }
}

rootProject.name = "Donation Terminal"
include(":app")
