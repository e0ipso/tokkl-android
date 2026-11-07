// Sign-in, and signing in again after the server refused the token.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(project(":core:navigation"))

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
}
