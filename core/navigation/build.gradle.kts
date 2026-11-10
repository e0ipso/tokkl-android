// The routes, the back stack's navigator, the top-level pages, taps from outside the app and frame
// parts the features contribute, and what the navigation host lends every feature's screens, so
// that a feature opens another's screen without naming it and the host names no feature.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // The entry form's route carries the model's ids.
    api(project(":core:model"))
    // Each route is a Navigation 3 key, and each feature adds its entries to the entry provider.
    api(libs.androidx.navigation3.runtime)
    // Each top-level page carries its section hue.
    api(project(":core:ui"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // A feature's notice in the signed-in frame takes the frame's modifier.
    implementation(libs.androidx.compose.ui)
}
