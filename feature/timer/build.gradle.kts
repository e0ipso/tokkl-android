// The Timer page with its history and week calendar, the fullscreen timer, the entry form and
// the timer templates.
plugins {
    alias(libs.plugins.tokkl.android.feature.screen)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:time"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:timerstate"))
    implementation(project(":core:timersync"))
    implementation(project(":core:catalog"))
    implementation(project(":core:entryfields"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.activity.compose)
    // The route's saved state keeps the entry form as JSON.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
    testImplementation(testFixtures(project(":core:session")))
    androidTestImplementation(testFixtures(project(":core:session")))
}
