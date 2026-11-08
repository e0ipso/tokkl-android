// Settings, and the Licences screen it opens.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:catalog"))
    implementation(project(":core:navigation"))

    // PomodoroDefaultsUiTest draws edge to edge through androidx.core's WindowCompat, which
    // comes with activity.
    androidTestImplementation(libs.androidx.activity.compose)
}
