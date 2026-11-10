import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.project

/**
 * Runs a library module's instrumented tests the way the app runs its own: through the runner of
 * :core:testing, which turns on the accessibility checks and keeps the steps of a restart test in
 * one shard, each test in a process of its own under the test orchestrator, in a test APK that
 * targets the app's API level. The module's androidTest sources get :core:testing.
 */
internal fun Project.configureInstrumentedTests(android: LibraryExtension) {
    android.defaultConfig.testInstrumentationRunner = "com.github.e0ipso.tokkl.accessibility.AccessibilityTestRunner"
    android.testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"
    android.testOptions.targetSdk = TARGET_SDK
    dependencies.add("androidTestImplementation", dependencies.project(":core:testing"))
    dependencies.add("androidTestUtil", libs.findLibrary("androidx-test-orchestrator").get())
}
