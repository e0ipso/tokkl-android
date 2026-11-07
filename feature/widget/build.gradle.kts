// The Timer and Pomodoro home screen widgets.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)

    testImplementation(testFixtures(project(":core:data")))
}
