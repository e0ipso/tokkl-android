// The running timer's notification, the notification permission prompt and the reminder of a
// long-running timer.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(project(":core:data")))
}
