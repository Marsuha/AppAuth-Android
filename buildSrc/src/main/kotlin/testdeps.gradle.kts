plugins {
    id("com.android.library") apply false
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(libs.findLibrary("junit").get())
    testImplementation(libs.findLibrary("hamcrest").get())
    testImplementation(libs.findLibrary("mockito").get())
    testImplementation(libs.findLibrary("mockitoKotlin").get())
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("assertj").get())
    testImplementation(libs.findLibrary("junitExt").get())
    testImplementation(libs.findLibrary("truthExt").get())
    testImplementation(libs.findLibrary("truth").get())
    testImplementation(libs.findLibrary("xmlpull").get())
    testImplementation(libs.findLibrary("coroutinesTest").get())
}
