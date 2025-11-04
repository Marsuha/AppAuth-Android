import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.kotlin.dsl.configure

extensions.findByType<BaseAppModuleExtension>()?.let { appExt ->
    with(appExt) {
        commonConfigure()
    }

    configure<ApplicationExtension> {
        defaultConfig {
            targetSdk = rootProject.extra["compileSdkVersion"] as Int
            versionCode = rootProject.extra["versionNum"] as Int
            versionName = rootProject.extra["versionName"] as String
        }
    }
}

extensions.findByType<LibraryExtension>()?.let { libExt ->
    with(libExt) {
        commonConfigure()
    }

    configure<LibraryExtension> {
        testOptions.targetSdk = rootProject.extra["compileSdkVersion"] as Int
    }
}

tasks.register<Copy>("jar") {
    dependsOn("bundleRelease")
    from("${project.layout.buildDirectory}/intermediates/bundles/release/")
    into("${project.layout.buildDirectory}/libs/")
    include("classes.jar")
    rename("classes.jar", "appauth-${rootProject.extra["versionName"]}.jar")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

private fun CommonExtension<*, *, *, *, *, *>.commonConfigure() {
    namespace = "net.openid.appauth"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("java"))
            aidl.setSrcDirs(listOf("java"))
            res.srcDir("res")
            assets.srcDir("assets")
            resources.srcDir("java")
        }

        getByName("test") {
            setRoot("javatests")
            java.setSrcDirs(listOf("javatests"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        disable += listOf(
            "InvalidPackage",
            "TrulyRandom",
            "UseCompoundDrawables",
            "GradleDependency"
        )
    }

    packaging {
        resources.excludes += listOf(
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE.txt"
        )
    }

    tasks.register<Jar>("sourcesJar") {
        dependsOn("generateReleaseSources")
        from(sourceSets["main"].java.directories)
        archiveClassifier.set("sources")
    }
}