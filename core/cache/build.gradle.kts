// The account's data in Room and the queue of changes made on the phone, which it sends to the
// server in order, from WorkManager too when the network comes back, and the check of a cached
// running entry.
plugins {
    alias(libs.plugins.tokkl.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.cache"
}

// Room writes the schema of each database version here. The files are tracked,
// so a later version can be checked against them and migrated.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // The cache's types and calls use the session's types, and through it the API's.
    api(project(":core:session"))
    // CacheDatabase is a RoomDatabase, and the widgets watch its invalidation tracker.
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
}
