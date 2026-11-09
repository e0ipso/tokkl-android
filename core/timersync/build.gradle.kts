// The timer event stream of a Tokkl server with the timer API, the foreground service that holds
// it in the background, and what decides whether the two run.
plugins {
    alias(libs.plugins.tokkl.android.library)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.timersync"
}

dependencies {
    // The stream feeds the account's timer state, and its types carry the timer state's.
    api(project(":core:timerstate"))
    // The service's notification icon.
    implementation(project(":core:ui"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
    testImplementation(testFixtures(project(":core:timerstate")))
}
