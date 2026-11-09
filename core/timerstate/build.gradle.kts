// The timer's state: the running entry's controls and totals, and Pause, Resume and the Pomodoro
// session of a Tokkl server with the timer API, which the server sends and the phone keeps, with
// their Koin definitions.
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
    // The held timer state, its controls and its targets carry the model's types.
    api(project(":core:model"))
    implementation(project(":core:api"))
    implementation(project(":core:time"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    // The fixtures build the server's timer states, the API's wire types.
    testFixturesApi(project(":core:api"))
    testImplementation(testFixtures(project(":core:platform")))
}
