import org.gradle.internal.extensions.core.extra
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.application)
    id("checkstyle")
    id("android-common")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "net.openid.appauthdemo"

    defaultConfig {
        applicationId = "net.openid.appauthdemo"
        extra["archivesBaseName"] = "appauth-demoapp"
        vectorDrawables.useSupportLibrary = true

        // Make sure this is consistent with the redirect URI used in res/raw/auth_config.json,
        // or specify additional redirect URI in AndroidManifest.xml
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

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":library"))
    implementation(libs.annotation)
    implementation(libs.okio)
    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coil.compose)

    androidTestImplementation(platform(libs.compose.bom))
}
