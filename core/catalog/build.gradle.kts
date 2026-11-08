// The frame the catalog pages share, Projects, Clients, Tags and Trash: the page and its rows,
// its model with the reloads and the single create, the confirm dialog, and why the server
// failed, which the other pages say in the same words.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.catalog"
}

dependencies {
    // The model and the problems carry the API's errors, and the pages draw with the design system.
    api(project(":core:api"))
    // The pages manage one workspace, by its model id.
    api(project(":core:model"))
    api(project(":core:ui"))
    implementation(project(":core:session"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
}
