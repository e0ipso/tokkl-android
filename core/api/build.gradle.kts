// The HTTP client for the Tokkl and Toggl Track APIs, its trust checks and the API's types.
plugins {
    alias(libs.plugins.tokkl.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    // Fakes that the unit tests of this module and of the modules above it share.
    `java-test-fixtures`
}

dependencies {
    // The API's calls take the model's typed ids, and its answers carry them.
    api(project(":core:model"))
    // The API's answers and bodies are kotlinx.serialization types, which ApiJson reads and writes
    // as text, and its calls suspend.
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.coroutines.core)
    // The transport and the JSON trees stay inside: no public signature carries them, except the
    // app summary's tree, whose unknown fields the report shows, so its one reader names JSON too.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Tests that send requests the app does not send build their bodies and clients with these.
    testFixturesApi(libs.okhttp)
    testFixturesApi(libs.kotlinx.serialization.json)

    testImplementation(libs.mockwebserver3)
}
