import org.gradle.internal.extensions.core.extra
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.application)
    id("checkstyle")
    id("android-common")
    id("style")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.openid.appauthdemo"

    defaultConfig {
        applicationId = "net.openid.appauthdemo"
        extra["archivesBaseName"] = "appauth-demoapp"
        vectorDrawables.useSupportLibrary = true

        // Make sure this is consistent with the redirect URI used in res/raw/auth_config.json,
        // or specify additional redirect URIs in AndroidManifest.xml
        manifestPlaceholders["appAuthRedirectScheme"] = "net.openid.appauthdemo"
    }

    signingConfigs {
        create("debugAndRelease") {
            storeFile = file("$rootDir/appauth.keystore")
            storePassword = "appauth"
            keyAlias = "appauth"
            keyPassword = "appauth"
        }
    }

    lint {
        lintConfig = file("$projectDir/lint.xml")
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debugAndRelease")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("debugAndRelease")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":library"))
    api(libs.bundles.coroutines)
    implementation(libs.appcompat)
    implementation(libs.annotation)
    implementation(libs.material)
    implementation(libs.glide)
    implementation(libs.okio)
    implementation(libs.joda.time)
    implementation(libs.core.ktx)

    annotationProcessor(libs.compiler.glide)
}
