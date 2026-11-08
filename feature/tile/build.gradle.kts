// The Quick Settings tile and the controls its long press opens.
plugins {
    alias(libs.plugins.tokkl.android.feature)
}

dependencies {
    implementation(project(":core:shared"))
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(project(":core:timerstate")))
}
