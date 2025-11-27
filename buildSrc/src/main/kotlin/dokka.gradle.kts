import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URI

val destinationDir = project.layout.buildDirectory.dir("docs/javadoc").get().asFile
val android = project.extensions.getByType<LibraryExtension>()

tasks.named("dokkaHtml", DokkaTask::class) {
    outputDirectory.set(destinationDir)
    moduleName.set("AppAuth for Android")

    dokkaSourceSets.configureEach {
        val mainSourceSet = android.sourceSets.getByName("main")

        @Suppress("UnstableApiUsage")
        sourceRoots.setFrom(project.files(mainSourceSet.java.directories))

        externalDocumentationLink {
            url.set(URI("https://developer.android.com/reference/").toURL())
            packageListUrl.set(URI("https://developer.android.com/reference/package-list").toURL())
        }

        failOnWarning = false
    }
}

tasks.register("androidJavadoc") {
    dependsOn("dokkaHtml")
    group = "documentation"
    description = "Generates documentation using Dokka (KDoc)."
}

tasks.register<Jar>("javadocJar") {
    dependsOn("androidJavadoc")
    archiveClassifier.set("javadoc")
    from(destinationDir)
}