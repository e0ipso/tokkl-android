// The timer event stream of a Tokkl server with the timer API, the foreground service that holds
// it in the background, and what decides whether the two run; the timer state's Android delivery:
// the expedited work that sends the notification's and the widget's server actions, what the
// timer's surfaces say when one does not go through, the timer frame the navigation host reads,
// and the Pomodoro alarm, ring and widget redraw contracts the Pomodoro and widget features share.
plugins {
    alias(libs.plugins.tokkl.android.library)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.timersync"
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:timerstate"))
    // The service's notification icon.
    implementation(project(":core:ui"))
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
    testImplementation(testFixtures(project(":core:timerstate")))
}
