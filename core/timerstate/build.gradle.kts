// The timer's state: the running entry's controls and totals, and Pause, Resume and the Pomodoro
// session of a Tokkl server with the timer API, which the server sends and the phone keeps, with
// their Koin definitions, what the timer's surfaces say when a timer action does not go through,
// and the expedited work that sends the notification's and the widget's server actions.
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
    // The server actions of the notification and the widget run as expedited work.
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
}
