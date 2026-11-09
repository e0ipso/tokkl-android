import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module, `:feature:<name>`: [AndroidLibraryConventionPlugin] with the namespace
 * `com.github.e0ipso.tokkl.feature.<name>`, Koin, which every feature's module binds itself with,
 * the unit test libraries, and instrumented tests run as the app runs its own
 * ([configureInstrumentedTests]). A feature with screens applies [AndroidScreenFeatureConventionPlugin]
 * instead; one that draws only a widget, a dialog or a frame part applies Compose and names the
 * Compose libraries it uses itself. Each feature's build file names the core modules it depends on
 * itself, so that ModuleLayersTest reads the module graph from the build files.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("tokkl.android.library")
        extensions.configure<LibraryExtension> {
            namespace = "com.github.e0ipso.tokkl.feature.${name.replace('-', '_')}"
            configureInstrumentedTests(this)
        }
        dependencies {
            add("implementation", platform(libs.findLibrary("koin-bom").get()))
            add("implementation", libs.findLibrary("koin-android").get())

            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
