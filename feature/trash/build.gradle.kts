// The Trash page of a Tokkl server.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:time"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:catalog"))
    implementation(project(":core:navigation"))

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
}
