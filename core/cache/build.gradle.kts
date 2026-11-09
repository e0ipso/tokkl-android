// The account's data in Room and the queue of changes made on the phone, which it sends to the
// server in order, from WorkManager too when the network comes back, and the check of a cached
// running entry, with their Koin definitions.
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
    // Every read and change of the account's data takes and gives the model's types.
    api(project(":core:model"))
    implementation(project(":core:api"))
    implementation(project(":core:time"))
    implementation(project(":core:platform"))
    implementation(project(":core:session"))
    // CacheDatabase is a RoomDatabase. No module above names it: they read the cache's flows.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    // The worker-thread contract of the current-entry reads.
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver3)
    testImplementation(testFixtures(project(":core:api")))
    testImplementation(testFixtures(project(":core:platform")))
}
