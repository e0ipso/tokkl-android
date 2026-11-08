// The sync status band, the notice of discarded changes and the changes the server refused.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:navigation"))

    testImplementation(testFixtures(project(":core:platform")))
}
