// The frame the catalog pages share, Projects, Clients, Tags and Trash: the page and its rows,
// its model with the loads and the single create, and the confirm dialog.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.catalog"
}

dependencies {
    // The model carries the API's errors, and the pages draw with the design system.
    api(project(":core:api"))
    // Why the server failed, which the model and the pages say in the session's words.
    api(project(":core:session"))
    // The pages manage one workspace, by its model id.
    api(project(":core:model"))
    api(project(":core:ui"))
    // Its model loads as the pages' view models share their state.
    api(project(":core:platform"))

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core:session")))
}
