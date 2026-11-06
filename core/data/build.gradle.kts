// What the phone keeps for the account and what reads and changes it: the API token, the server's
// capabilities, the session and its refresh, the Room cache with the change queue, and the timer
// state. Also the disk store interfaces and the Android plumbing the layers above share.
plugins {
    alias(libs.plugins.tokkl.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.data"
    // Fakes that the unit tests of this module and of the modules above it share.
    testFixtures.enable = true
}

// Room writes the schema of each database version here. The files are tracked,
// so a later version can be checked against them and migrated.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // The data types and calls of this module use the API's and the time helpers' types.
    api(project(":core:api"))
    api(project(":core:time"))

    // CacheDatabase is a RoomDatabase, and the widgets watch its invalidation tracker.
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
}
