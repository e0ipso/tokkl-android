// Sign-in, and signing in again after the server refused the token.
plugins {
    alias(libs.plugins.tokkl.android.feature.screen)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:trust"))
    implementation(project(":core:navigation"))

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:session")))
}
