// The signed-in account: the API token, the server and the certificate it trusts, the server's
// capabilities, the account's settings and their refresh, sign-out, and the preferences synced
// with the server.
plugins {
    alias(libs.plugins.tokkl.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.session"
}

dependencies {
    // The session's types and calls use the API's, the time helpers' and the disk stores' types.
    api(project(":core:api"))
    api(project(":core:time"))
    api(project(":core:platform"))
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
}
