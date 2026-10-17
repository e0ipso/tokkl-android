plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl"
    // Compose UI 1.12 and androidx.core 1.19 need compileSdk 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.e0ipso.tokkl"
        minSdk = 26
        // The API level of the test emulator.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Puts the merged manifest's path on the unit test classpath, in
        // com/android/tools/test_config.properties, for BackupRulesTest.
        unitTests.isIncludeAndroidResources = true
        // Runs each instrumented test in a new app process, which
        // ApiTokenStoreRestartTest needs to restart the process between steps.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
}
