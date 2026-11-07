// The parts several features show: the catalog pages' model and rows, the project and tag picker,
// the entry fields editor, the connection trust prompts and the timer notices.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.shared"
}

dependencies {
    api(project(":core:data"))
    api(project(":core:ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
}
