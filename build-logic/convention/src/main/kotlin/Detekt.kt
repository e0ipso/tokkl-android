import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Static analysis for a module: detekt with ktlint's formatting rules and the Compose rules, over
 * every Kotlin file under the module's src directory, with config/detekt/detekt.yml on top of
 * detekt's defaults and the module's detekt-baseline.xml, if it has one. `check` runs it.
 */
internal fun Project.configureDetekt() {
    pluginManager.apply("dev.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(layout.settingsDirectory.file("config/detekt/detekt.yml"))
        source.setFrom(layout.projectDirectory.dir("src"))
        basePath.set(layout.settingsDirectory)
        // Only the findings whose fix would change behaviour, each with its reason.
        val baselineFile = layout.projectDirectory.file("detekt-baseline.xml")
        if (baselineFile.asFile.exists()) baseline.set(baselineFile)
    }
    dependencies.add("detektPlugins", libs.findLibrary("detekt-rules-ktlint").get())
    dependencies.add("detektPlugins", libs.findLibrary("compose-rules-detekt").get())
}
