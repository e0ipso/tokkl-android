// The running timer's notification, the notification permission prompt and the reminder of a
// long-running timer.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.tokkl.android.library.compose)
}

dependencies {
    implementation(project(":core:time"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:timerstate"))
    implementation(project(":core:entryfields"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(testFixtures(project(":core:platform")))
    testImplementation(testFixtures(project(":core:timerstate")))
}
