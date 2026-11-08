// The Android plumbing the layers above share: the dispatchers and the Koin definition helpers,
// the default network, WorkManager, whether the app is on screen, the receivers and the intent
// that opens the app, one flight of a call at a time, and the disk store interfaces, with the
// Koin definitions of each.
plugins {
    alias(libs.plugins.tokkl.android.library)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.platform"
    // The in-memory value store and the main dispatcher rule that the unit tests of this module
    // and of the modules above it share.
    testFixtures.enable = true
}

dependencies {
    // The default network and the single flight are flows and suspend calls.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime)
    // Whether the app is on screen, from the process's lifecycle.
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.test)
}
