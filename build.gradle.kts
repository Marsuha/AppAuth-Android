import org.ajoberstar.grgit.Grgit

plugins {
    alias(libs.plugins.grgit)
    jacoco
    id("keystore")
}

runCatching {
    val grgit = Grgit.open(mapOf("currentDir" to project.rootDir))
    val lastCommit = grgit.head()

    project.extra["versionNum"] = grgit.log(mapOf("includes" to listOf("HEAD"))).size
    project.extra["versionName"] = grgit.describe() ?: "DEV"
    project.extra["versionDate"] = lastCommit.dateTime
}.onFailure { e ->
    logger.error("Grgit error: ${e.message}")
    project.extra["versionNum"] = 1
    project.extra["versionName"] = "DEV"
    project.extra["versionDate"] = java.time.LocalDateTime.now()
}

project.extra.apply {
    set("minSdkVersion", 21)
    set("compileSdkVersion", 36)
}

keystore {
    verifyKeystore()
}

tasks.register("showVersion") {
    doLast {
        logger.lifecycle("Version ID: ${project.extra["versionNum"]}")
        logger.lifecycle("Version Name: ${project.extra["versionName"]}")
        logger.lifecycle("Version Date: ${project.extra["versionDate"]}")
    }
}