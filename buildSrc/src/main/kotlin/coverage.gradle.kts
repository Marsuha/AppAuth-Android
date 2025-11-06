plugins {
    id("com.android.library") apply false
    jacoco
}

android {
    // Configure manifest placeholders for unit test variants
    unitTestVariants.all {
        mergedFlavor.manifestPlaceholders["appAuthRedirectScheme"] = "net.openid.appauthdemo"
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "Reporting"
    description = "Generate Jacoco coverage reports after running tests."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // Class R is used, but usage will not be covered, so ignore this class from report
    afterEvaluate {
        classDirectories.setFrom(
            fileTree(
                mapOf(
                    "dir" to "build/intermediates/javac/debug/classes",
                    "excludes" to listOf("**/BuildConfig.class", "jdk.internal.*")
                )
            )
        )
        sourceDirectories.setFrom(
            files("java")
        )
        executionData.setFrom(
            files("build/jacoco/testDebugUnitTest.exec")
        )
    }
}