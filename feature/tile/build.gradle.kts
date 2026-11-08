// The Quick Settings tile and the controls its long press opens.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:cache"))
    implementation(project(":core:timerstate"))
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(project(":core:timerstate")))
}
