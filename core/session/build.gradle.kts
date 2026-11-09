// The signed-in account: the API token, the server and the certificate it trusts, the server's
// capabilities, the account's settings and their refresh, sign-out, and the preferences synced
// with the server, and their Koin definitions.
plugins {
    alias(libs.plugins.tokkl.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.session"
    // The fakes of the session's roles and the in-memory capabilities and account settings that
    // the unit tests of the modules above it share.
    testFixtures.enable = true
}

dependencies {
    // Session is the API client provider, and its api() gives the API.
    api(project(":core:api"))
    // The account's default workspace is a model id.
    api(project(":core:model"))
    // The account settings give the account's time.
    api(project(":core:time"))
    implementation(project(":core:platform"))
    implementation(libs.androidx.annotation)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
    testFixturesImplementation(testFixtures(project(":core:platform")))
}
