import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.plugins.quality.Checkstyle

plugins {
    id("checkstyle")
}

checkstyle {
    configFile = project.file("../config/checkstyle/checkstyle.xml")
    configProperties = mapOf("checkstyle.java.header" to project.file("../config/checkstyle/java.header").absolutePath)
    toolVersion = "10.12.0" // Обновлено до последней версии
}

tasks.register<Checkstyle>("checkAllSource") {
    afterEvaluate {
        // Источник: все Java-файлы из main sourceSet
        source = (project.extensions.getByType<SourceSetContainer>().getByName(SourceSet.MAIN_SOURCE_SET_NAME).allJava)
        include("**/*.java")
        classpath = files() // Пустой classpath, так как проверяется только стиль
    }

    reports {
        xml.apply {
            isEnabled = true
            outputLocation.set(file("build/reports/checkstyle/checkAllSource.xml"))
        }
    }
}

tasks.register("failOnCheckstyleWarning") {
    val checkstyleWarningsFile = file("build/reports/checkstyle/checkAllSource.xml")
    doLast {
        if (checkstyleWarningsFile.exists() && checkstyleWarningsFile.readText().contains("<error ")) {
            logger.error("Checkstyle warnings found in ${checkstyleWarningsFile.absolutePath}")
            throw GradleException("There were Checkstyle warnings. For details, check: ${checkstyleWarningsFile.absolutePath}")
        } else if (!checkstyleWarningsFile.exists()) {
            logger.warn("Checkstyle report not found at ${checkstyleWarningsFile.absolutePath}")
        } else {
            logger.info("No Checkstyle warnings found")
        }
    }
}

// Настройка зависимостей задач
tasks.named("failOnCheckstyleWarning") {
    dependsOn(tasks.named("checkAllSource"))
}

tasks.named("check") {
    dependsOn(tasks.named("failOnCheckstyleWarning"))
}
