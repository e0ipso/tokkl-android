import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A core module without Android code: a Kotlin/JVM library with the Java 17 of the Android
 * modules, Android lint, detekt and an Android API check. It compiles against the JDK, not
 * android.jar, and Android lint has no minSdk for a JVM module, so its NewApi check stays silent
 * there. Animal Sniffer checks the module's classes against the Android 12 (API 31) signature of
 * gummy-bears instead, in `check` and in `lint`.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("com.android.lint")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
        keepLintAnalysisToThisCheckout()
        configureDetekt()
        configureAndroidApiCheck()
    }
}

/**
 * Fails the build when the main classes call a JDK API that Android 12 lacks. Classes of the
 * module's dependencies count as present; the tests run on the JVM only, so they are not checked.
 */
private fun Project.configureAndroidApiCheck() {
    pluginManager.apply("ru.vyarus.animalsniffer")
    val signature = libs.findLibrary("gummy-bears-api31").get().get()
    dependencies.add("signature", "${signature.module}:${signature.versionConstraint.requiredVersion}@signature")
    tasks.named("lint").configure { dependsOn("animalsnifferMain") }
}
