import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module, `:feature:<name>`: [AndroidLibraryComposeConventionPlugin] with the namespace
 * `com.github.e0ipso.tokkl.feature.<name>`, and the libraries every feature's screens and Koin
 * module use. Each feature's build file names the core modules it depends on itself, so that
 * ModuleLayersTest reads the module graph from the build files.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("tokkl.android.library.compose")
        extensions.configure<LibraryExtension> {
            namespace = "com.github.e0ipso.tokkl.feature.${name.replace('-', '_')}"
        }
        dependencies {
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", platform(libs.findLibrary("koin-bom").get()))
            add("implementation", libs.findLibrary("koin-android").get())
            add("implementation", libs.findLibrary("koin-androidx-compose").get())

            add("testImplementation", libs.findLibrary("junit").get())
        }
    }
}
