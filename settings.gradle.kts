pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}


dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppAuth-Android"
include(":app")
include(":library")