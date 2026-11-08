// The Android plumbing the layers above share: the dispatchers and the Koin definition helpers,
// the default network, WorkManager, the receivers and the intent that opens the app, one flight
// of a call at a time, and the disk store interfaces.
plugins {
    alias(libs.plugins.tokkl.android.library)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.platform"
    // The in-memory value store that the unit tests of this module and of the modules above it share.
    testFixtures.enable = true
}

dependencies {
    // The default network and the single flight are flows and suspend calls.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
}
