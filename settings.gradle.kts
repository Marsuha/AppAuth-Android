pluginManagement {
    repositories {
        google()  // Добавляем репозиторий Google для AGP
        gradlePluginPortal()  // Основной репозиторий Gradle
        mavenCentral()  // Дополнительный репозиторий для других зависимостей
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppAuth-Android"  // Замените на имя вашего проекта
include(":app")  // Добавьте модули вашего проекта, если они есть (например, :library)
include(":library")