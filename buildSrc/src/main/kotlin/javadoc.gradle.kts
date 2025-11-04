import com.android.build.gradle.LibraryExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import java.io.File

configurations {
    create("mdDoclet")
}

dependencies {
    "mdDoclet"("org.jdrupes.mdoclet:doclet:3.1.0")
}

val android = extensions.getByType<LibraryExtension>()
val destinationDir: File = project.layout.buildDirectory.dir("docs/javadoc").get().asFile

tasks.register<JavaExec>("androidJavadoc") {
    jvmArgs = listOf(
        "--add-exports=jdk.javadoc/jdk.javadoc.internal.tool=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
    )
    classpath = project.files(android.bootClasspath)
    mainClass.set("jdk.javadoc.internal.tool.Main")
    args = listOf(
        "-doctitle", "AppAuth for Android",
        "-use",
        "-linksource",
        "-link", "https://developer.android.com/reference/",
        "-linkoffline", "https://developer.android.com/reference/", "https://developer.android.com/reference/androidx/",
        "-doclet", "org.jdrupes.mdoclet.MDoclet",
        "-docletpath", configurations["mdDoclet"].files.joinToString(":"),
        "-source", "8",
        "-d", destinationDir.toString(),
        "-Xdoclint:none"
    ) + android.sourceSets.getByName("main").java.getSourceFiles().files.toString()
    mustRunAfter(":library:assembleRelease", ":library:assembleDebug")
}

tasks.register<Jar>("javadocJar") {
    dependsOn("androidJavadoc")
    archiveClassifier.set("javadoc")
    from(destinationDir)
}

afterEvaluate {
    tasks.named<JavaExec>("androidJavadoc") {
        classpath += project.files(android.libraryVariants.map { variant ->
            variant.javaCompileProvider.get().classpath.files
        })
    }
}