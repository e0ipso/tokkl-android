// The timer's state: the running entry's controls and totals, Pause, Resume and the Pomodoro
// session of a Tokkl server with the timer API, its event stream and the foreground service that
// holds it, and the steps of each account refresh, which join the session, the cache and the
// timer state.
plugins {
    alias(libs.plugins.tokkl.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.timerstate"
    // The server timer states that the unit tests of this module and of the modules above it share.
    testFixtures.enable = true
}

dependencies {
    // The timer state's types and calls use the cache's types, and through it the session's.
    api(project(":core:cache"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
}
