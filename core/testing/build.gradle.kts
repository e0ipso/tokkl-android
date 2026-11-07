// What the instrumented tests of the app and of the feature modules share: the test runner with
// its accessibility checks and step-aware sharding, the compose rules that run those checks, the
// device shell, the font scale and display size rules, and the Pomodoro length card matchers.
// Only androidTest source sets depend on it.
plugins {
    alias(libs.plugins.tokkl.android.library)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.testing"
}

dependencies {
    // The length card matchers find the stepper cards of the design system by their test tag.
    implementation(project(":core:ui"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.compose.ui.test.junit4.accessibility)
    api(libs.androidx.test.espresso.accessibility)
    api(libs.androidx.test.runner)
    api(libs.junit)
    // Declares the empty activity that createComposeRule() starts, in the test APKs.
    implementation(libs.androidx.compose.ui.test.manifest)
}
