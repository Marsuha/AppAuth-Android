@file:Suppress("PropertyName")

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.library)
    id("android-common")
    id("coverage")
    id("testdeps")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    id("dokka")
    `maven-publish`
    signing
}

val GROUP: String by project
val POM_ARTIFACT_ID: String by project

group = GROUP
version = rootProject.extra["versionName"] as String
base.archivesName = "appauth"

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        singleVariant("release")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(libs.browser)
    implementation(libs.annotation)
    implementation(libs.core.ktx)
    api(libs.serializationJson)
}

val sourcesJar: Jar by tasks
val javadocJar: Jar by tasks

artifacts {
    add("archives", sourcesJar)
    add("archives", javadocJar)
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Marsuha/AppAuth-Android")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }

    publications {
        register<MavenPublication>("GitHubPackagesRelease") {
            groupId = GROUP
            artifactId = POM_ARTIFACT_ID
            version = rootProject.extra["versionName"] as String

            artifact(sourcesJar)
            artifact(javadocJar)

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

signing {
    setRequired { project.hasProperty("signing.keyId") }
    sign(publishing.publications)
}