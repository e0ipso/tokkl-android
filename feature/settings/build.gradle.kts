// Settings, and the Licences screen it opens.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(project(":core:navigation"))

    // PomodoroDefaultsUiTest draws edge to edge through androidx.core's WindowCompat, which
    // comes with activity.
    androidTestImplementation(libs.androidx.activity.compose)
}
