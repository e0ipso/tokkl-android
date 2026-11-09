// The Timer and Pomodoro home screen widgets.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.tokkl.android.library.compose)
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
    implementation(project(":core:entryfields"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.androidx.workmanager)

    testImplementation(testFixtures(project(":core:platform")))
    testImplementation(testFixtures(project(":core:timerstate")))
}
