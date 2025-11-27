@file:Suppress("PropertyName")

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.library)
    id("android-common")
    //id("style")
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
val POM_NAME: String by project
val POM_DESCRIPTION: String by project
val POM_PACKAGING: String by project
val POM_URL: String by project
val POM_SCM_URL: String by project
val POM_SCM_CONNECTION: String by project
val POM_SCM_DEV_CONNECTION: String by project
val POM_LICENCE_NAME: String by project
val POM_LICENCE_URL: String by project
val POM_LICENCE_DIST: String by project
val POM_DEVELOPER_ID: String by project
val POM_DEVELOPER_NAME: String by project
val POM_DEVELOPER_URL: String by project

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

            //artifact("${layout.buildDirectory}/outputs/aar/appauth-release.aar")
            artifact(sourcesJar)
            artifact(javadocJar)

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    /*publications {
        create<MavenPublication>("release") {
            groupId = GROUP
            artifactId = POM_ARTIFACT_ID
            version = rootProject.extra["versionName"] as String

            artifact("${layout.buildDirectory}/outputs/aar/$archivesBaseName-release.aar")
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set(POM_NAME)
                description.set(POM_DESCRIPTION)
                packaging = POM_PACKAGING
                url.set(POM_URL)
                licenses {
                    license {
                        name.set(POM_LICENCE_NAME)
                        url.set(POM_LICENCE_URL)
                        distribution.set(POM_LICENCE_DIST)
                    }
                }
                developers {
                    developer {
                        id.set(POM_DEVELOPER_ID)
                        name.set(POM_DEVELOPER_NAME)
                        url.set(POM_DEVELOPER_URL)
                    }
                }
                scm {
                    url.set(POM_SCM_URL)
                    connection.set(POM_SCM_CONNECTION)
                    developerConnection.set(POM_SCM_DEV_CONNECTION)
                }
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")

                    configurations["implementation"].allDependencies.forEach {
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", it.group)
                        dependencyNode.appendNode("artifactId", it.name)
                        dependencyNode.appendNode("version", it.version)
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username =
                    if (project.hasProperty("ossrhUsername")) project.property("ossrhUsername") as String else ""
                password =
                    if (project.hasProperty("ossrhPassword")) project.property("ossrhPassword") as String else ""
            }
        }
    }*/
}

signing {
    setRequired { project.hasProperty("signing.keyId") }
    sign(publishing.publications)
}