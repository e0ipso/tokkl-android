import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The app's targetSdk, the API level of the test emulator tokkl-api36. Raise it together with the
 * emulator image. The feature modules' test APKs target it too, so their tests draw the way the
 * app does, edge to edge included.
 */
internal const val TARGET_SDK = 36

/** The settings every Android module of the app shares, the app included. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.apply {
        // Compose UI 1.12 and androidx.core 1.19 need compileSdk 37.
        compileSdk = 37
        // Pinned so that F-Droid builds with the same build-tools as CI, which
        // installs this version. 36.0.0 is both the default and the minimum of AGP
        // 9.4.1, so check this pin whenever AGP changes.
        buildToolsVersion = "36.0.0"

        defaultConfig.apply {
            // Android 12 (docs/PRD.md, Compatibility).
            minSdk = 31
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    keepLintAnalysisToThisCheckout()
    configureDetekt()
}

/**
 * Turns on Compose for an Android module and adds the Compose BOM and the preview tooling. The
 * previews are private functions that nothing calls, so R8 drops them from the release build, and
 * the tooling that renders them in the IDE is in debug builds only.
 */
internal fun Project.configureCompose(android: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    android.buildFeatures.compose = true
    val bom = dependencies.platform(libs.findLibrary("androidx-compose-bom").get())
    dependencies.add("implementation", bom)
    dependencies.add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
    dependencies.add("debugImplementation", bom)
    dependencies.add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Lint's analysis stores the files of a module relative to path variables, but
// files outside it, such as gradle/libs.versions.toml and
// gradle/wrapper/gradle-wrapper.properties, by absolute path: lint has no
// variable for the root project, and AGP 9.4.1 passes it no --path-variables.
// From the build cache, one checkout's analysis made another checkout's lint
// report cite the first checkout's files. With the checkout's path as an
// input, the analysis comes from the cache only for the checkout that ran it.
//
// The analysis tasks are found by name, lintAnalyze<Variant> and
// lintVitalAnalyze<Variant>, as AGP names them: their class is internal to AGP
// and its public API has no hook for lint's tasks. With AGP 9.4.1 the names
// match the same 167 tasks as the class did.
internal fun Project.keepLintAnalysisToThisCheckout() {
    val checkoutDir: String = rootDir.absolutePath
    tasks.named { LINT_ANALYSIS.matches(it) }.configureEach {
        inputs.property("checkoutDir", checkoutDir)
    }
}

private val LINT_ANALYSIS = Regex("lint(Vital)?Analyze.*")
