// The Pomodoro page, the phase alarm with its ringing service and alarm screen, and the session
// notification.
plugins {
    alias(libs.plugins.tokkl.android.feature)
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
}
