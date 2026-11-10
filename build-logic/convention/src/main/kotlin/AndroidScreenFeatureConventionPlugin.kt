import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module with screens, routes whose view models Koin makes: [AndroidFeatureConventionPlugin]
 * with Compose ([AndroidLibraryComposeConventionPlugin]) and the libraries every such screen uses,
 * Material 3, Compose UI, the view model's Compose integration and Koin's.
 */
class AndroidScreenFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("tokkl.android.feature")
        pluginManager.apply("tokkl.android.library.compose")
        dependencies {
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("koin-androidx-compose").get())
        }
    }
}
