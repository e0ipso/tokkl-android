// The timer templates as launcher shortcuts.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
}
