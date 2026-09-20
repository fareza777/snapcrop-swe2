pluginManagement {
    repositories {
        google()
        // repo1.maven.org rate-limits this network; mirror Maven Central first.
        maven("https://cache-redirector.jetbrains.com/maven-central")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://cache-redirector.jetbrains.com/maven-central")
        mavenCentral()
    }
}

rootProject.name = "ShareSafe"
include(":app")
