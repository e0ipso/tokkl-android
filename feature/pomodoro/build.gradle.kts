// The Pomodoro page, the phase alarm with its ringing service and alarm screen, and the session
// notification.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.activity.compose)
}
