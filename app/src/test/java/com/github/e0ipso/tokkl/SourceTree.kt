package com.github.e0ipso.tokkl

import java.io.File

/**
 * The Gradle modules of the checkout, read from settings.gradle.kts and each module's build file,
 * and the app packages their main sources declare and use. Gradle runs the app module's unit tests
 * in the app module's directory, so the checkout is the nearest directory above it with
 * settings.gradle.kts. The test support modules ([TEST_SUPPORT]) hold no app code: only
 * androidTest source sets depend on them, so they stay out of the module graph.
 */
internal object SourceTree {

    const val APP = "com.github.e0ipso.tokkl"
    const val ROOT = "<root>"

    /** The modules whose main sources only the instrumented tests of other modules use. */
    val TEST_SUPPORT = setOf(":core:testing")

    /** A use of [target], an app package, in [file]. */
    data class Use(val target: String, val file: String)

    /** One Gradle module: its path, such as `:core:data`, and what its build file and sources hold. */
    class Module(val path: String, val directory: File) {

        private val buildFile: String by lazy { File(directory, "build.gradle.kts").readText() }

        /** The modules its main sources compile against, from `api(project(...))` and `implementation(project(...))`. */
        val mainDependencies: Set<String> by lazy { MAIN_DEPENDENCY.findAll(buildFile).map { it.groupValues[1] }.toSet() }

        /** The modules whose test fixtures its tests use, from `testFixtures(project(...))`. */
        val fixtureDependencies: Set<String> by lazy { FIXTURE_DEPENDENCY.findAll(buildFile).map { it.groupValues[1] }.toSet() }

        /** The modules it exposes to its dependents, from `api(project(...))`. */
        val apiDependencies: Set<String> by lazy { API_DEPENDENCY.findAll(buildFile).map { it.groupValues[1] }.toSet() }

        /** Each package its main sources declare, with the app packages those sources use. */
        val packages: Map<String, Set<Use>> by lazy { readSources(File(directory, "src/main/java/com/github/e0ipso/tokkl")) }

        /** The modules whose R class its main sources name, such as `:core:ui` for `com.github.e0ipso.tokkl.core.ui.R`. */
        val resourceModules: Set<String> by lazy { readResourceModules(File(directory, "src/main/java/com/github/e0ipso/tokkl")) }
    }

    val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    }

    val modules: List<Module> by lazy {
        INCLUDE.findAll(File(root, "settings.gradle.kts").readText())
            .map { it.groupValues[1] }
            .filterNot { it in TEST_SUPPORT }
            .map { path -> Module(path, File(root, path.removePrefix(":").replace(':', '/'))) }
            .toList()
    }

    /** The first package below the app's, or [ROOT] for a class of the app's package. Null for an R class. */
    fun topPackage(name: String): String? {
        val parts = name.removePrefix(APP).removePrefix(".").split('.')
        val first = parts.first()
        return when {
            name == APP -> ROOT
            first == "R" -> null
            // The R class of a core or feature module, such as com.github.e0ipso.tokkl.core.ui.R.
            (first == "core" || first == "feature") && parts.getOrNull(2) == "R" -> null
            first.first().isUpperCase() -> ROOT
            else -> first
        }
    }

    private fun readSources(root: File): Map<String, Set<Use>> {
        check(root.isDirectory) { "no main sources at ${root.absolutePath}" }
        val uses = mutableMapOf<String, MutableSet<Use>>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            val from = PACKAGE.find(text)?.groupValues?.get(1)?.let(::topPackage) ?: error("no package in $file")
            val code = withoutCommentsAndStrings(text).lineSequence().filterNot { it.startsWith("package ") }.joinToString("\n")
            val found = uses.getOrPut(from) { mutableSetOf() }
            for (match in APP_NAME.findAll(code)) {
                val target = topPackage(match.groupValues[1]) ?: continue
                found += Use(target, file.relativeTo(root).path)
            }
        }
        return uses
    }

    private fun readResourceModules(root: File): Set<String> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { MODULE_R.findAll(withoutCommentsAndStrings(it.readText())) }
            .map { ":${it.groupValues[1]}:${it.groupValues[2]}" }
            .toSet()

    private fun withoutCommentsAndStrings(text: String): String =
        text.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, " ").replace(STRING, "\"\"")

    private val INCLUDE = Regex("""include\("(:[\w:-]+)"\)""")
    private val MAIN_DEPENDENCY = Regex("""(?<![\w.])(?:api|implementation)\(project\("(:[\w:-]+)"\)\)""")
    private val API_DEPENDENCY = Regex("""(?<![\w.])api\(project\("(:[\w:-]+)"\)\)""")
    private val FIXTURE_DEPENDENCY = Regex("""testFixtures\(project\("(:[\w:-]+)"\)\)""")
    private val MODULE_R = Regex("""(?<![\w.])com\.github\.e0ipso\.tokkl\.(core|feature)\.(\w+)\.R\b""")
    private val PACKAGE = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val APP_NAME = Regex("""(?<![\w.])(com\.github\.e0ipso\.tokkl(?:\.\w+){0,3})""")
    private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT = Regex("""//[^\n]*""")
    private val STRING = Regex(""""(?:\\.|[^"\\\n])*"""")
}
