// The Tags page.
plugins {
    alias(libs.plugins.tokkl.android.feature.screen)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:catalog"))
    implementation(project(":core:navigation"))
    // The route's saved state keeps the open question as JSON.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
    testImplementation(testFixtures(project(":core:session")))
}
