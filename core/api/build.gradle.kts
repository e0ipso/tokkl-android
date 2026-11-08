// The HTTP client for the Tokkl and Toggl Track APIs, its trust checks and the API's types.
plugins {
    alias(libs.plugins.tokkl.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    // Fakes that the unit tests of this module and of the modules above it share.
    `java-test-fixtures`
}

dependencies {
    // The API's types and calls use OkHttp's and kotlinx.serialization's types, and its calls suspend.
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
}
