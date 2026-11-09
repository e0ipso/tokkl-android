// The prompts that ask the user to trust a server's certificate or a plain HTTP server, during
// sign-in and while signed in, and the session's trust state behind them.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.trust"
}

dependencies {
    // The prompts show the API's refused requests, and the session stores what the user trusts.
    implementation(project(":core:api"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:ui"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
}
