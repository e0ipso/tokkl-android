// The Tags page.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(project(":core:navigation"))
}
