// The routes, the back stack's navigator, the top-level pages the features contribute and what
// the navigation host lends every feature's screens, so that a feature opens another's screen
// without naming it.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.navigation"
}

dependencies {
    // The routes carry the model's ids and the API's server address, and the host lends the
    // session's capabilities.
    api(project(":core:model"))
    api(project(":core:session"))
    // Each route is a Navigation 3 key, and each feature adds its entries to the entry provider.
    api(libs.androidx.navigation3.runtime)
    // Each top-level page carries its section hue.
    api(project(":core:ui"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
}
