// The Trash page of a Tokkl server.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(project(":core:navigation"))

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
}
