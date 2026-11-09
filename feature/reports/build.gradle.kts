// Reports: the summary, detailed and weekly reports, their filters and the CSV export.
plugins {
    alias(libs.plugins.tokkl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:time"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    implementation(project(":core:cache"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
    testImplementation(testFixtures(project(":core:session")))
}
