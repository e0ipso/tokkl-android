// Jetpack Macrobenchmark for the app (docs/PRD.md, Speed): cold start with StartupTimingMetric,
// and tap-to-state on the Timer page with TraceSectionMetric around the app's TimerStart and
// TimerStop trace sections. It measures the app's benchmark build, which is the release build
// signed with the debug key and profileable.
//
// The benchmarks sign in to the Tokkl server that scripts/instrumented-tests.sh starts, so run
// them through it; README.md, "Benchmarks", has the command.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.github.e0ipso.tokkl.benchmark"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The benchmarks run on the test emulator. Macrobenchmark refuses an emulator unless
        // told that its numbers are only compared with other runs on the same emulator.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        // Matches the app's benchmark build type. The test APK itself may be debuggable.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    // The test APK runs in a process of its own, so the app starts cold.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    // Only the benchmark variant: a debug app is too slow to measure, and without a debug
    // variant scripts/instrumented-tests.sh's connectedDebugAndroidTest leaves this module out.
    beforeVariants { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}

// The app's instrumented tests and the benchmarks install different builds of the same app, and
// a benchmark measures nothing else running on the device, so they never run at once.
tasks.matching { it.name == "connectedBenchmarkAndroidTest" }.configureEach {
    mustRunAfter(":app:connectedDebugAndroidTest")
}
