// The Projects page, and a project's form with its tasks.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:ui")))
}
