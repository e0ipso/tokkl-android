// The Tags page.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:catalog"))
    implementation(project(":core:navigation"))
}
