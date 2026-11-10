import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * A core or feature module: an Android library with the shared Android settings, the namespace
 * its path gives (`:core:cache` is `com.github.e0ipso.tokkl.core.cache`) and the unit test libraries.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = "com.github.e0ipso.tokkl" + path.replace(':', '.')
            configureAndroid(this)
        }
        addUnitTestLibraries()
    }
}
