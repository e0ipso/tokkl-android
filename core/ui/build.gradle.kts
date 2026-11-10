// The design system: Tokkl's theme, the composables several screens share, and the strings,
// icons, brand images and font they use.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
}

android {
    // The WCAG contrast check, for the theme's tests here and the app's.
    testFixtures.enable = true
}

dependencies {
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.ui)

    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
}
