package com.github.e0ipso.tokkl

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the app's packages in layers, so each layer can become a Gradle module. A package imports
 * packages of lower layers, and a package of its own layer only where [SAME_LAYER] lists it. The
 * test reads the main sources: each `import com.github.e0ipso.tokkl.<package>` and each fully
 * qualified name in code. `R` belongs to no layer yet.
 */
class PackageLayersTest {

    private val imports: Map<String, Set<Use>> by lazy { readSources(File("src/main/java/com/github/e0ipso/tokkl")) }

    @Test
    fun everyPackageHasALayer() {
        assertEquals(emptySet<String>(), imports.keys - LAYERS.flatten().toSet())
    }

    @Test
    fun noPackageImportsAHigherLayerOrAnUnlistedNeighbour() {
        val forbidden = imports.flatMap { (from, uses) ->
            uses.filter { !allowed(from, it.target) }.map { "$from -> ${it.target} (${it.file})" }
        }.sorted()

        assertEquals("imports outside the layers", emptyList<String>(), forbidden)
    }

    @Test
    fun noPackagesImportEachOtherInACycle() {
        val graph = imports.mapValues { (from, uses) -> uses.map { it.target }.filter { it != from }.toSet() }

        assertEquals(emptyList<List<String>>(), cycles(graph))
    }

    private fun allowed(from: String, to: String): Boolean {
        if (from == to) return true
        val fromLayer = layerOf(from)
        val toLayer = layerOf(to)
        return toLayer < fromLayer || (toLayer == fromLayer && to in SAME_LAYER[from].orEmpty())
    }

    private fun layerOf(pkg: String): Int = LAYERS.indexOfFirst { pkg in it }.also { check(it >= 0) { "$pkg has no layer" } }

    /** One import or qualified name of [target], an app package, in [file]. */
    private data class Use(val target: String, val file: String)

    private fun readSources(root: File): Map<String, Set<Use>> {
        assertTrue("main sources at ${root.absolutePath}", root.isDirectory)
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

    /** The first package below the app's, or [ROOT] for a class of the app's package. Null for `R`. */
    private fun topPackage(name: String): String? {
        val first = name.removePrefix("$APP.").substringBefore('.')
        return when {
            name == APP -> ROOT
            first == "R" -> null
            first.first().isUpperCase() -> ROOT
            else -> first
        }
    }

    private fun withoutCommentsAndStrings(text: String): String =
        text.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, " ").replace(STRING, "\"\"")

    /** The groups of packages that import one another round, each sorted, found by Tarjan's algorithm. */
    private fun cycles(graph: Map<String, Set<String>>): List<List<String>> {
        val index = mutableMapOf<String, Int>()
        val low = mutableMapOf<String, Int>()
        val stack = ArrayDeque<String>()
        val found = mutableListOf<List<String>>()

        fun visit(node: String) {
            index[node] = index.size
            low[node] = index.getValue(node)
            stack.addLast(node)
            for (next in graph[node].orEmpty()) {
                if (next !in index) {
                    visit(next)
                    low[node] = minOf(low.getValue(node), low.getValue(next))
                } else if (next in stack) {
                    low[node] = minOf(low.getValue(node), index.getValue(next))
                }
            }
            if (low[node] == index[node]) {
                val group = mutableListOf<String>()
                do group += stack.removeLast() while (group.last() != node)
                if (group.size > 1) found += group.sorted()
            }
        }

        graph.keys.sorted().forEach { if (it !in index) visit(it) }
        return found
    }

    private companion object {
        const val APP = "com.github.e0ipso.tokkl"
        const val ROOT = "<root>"

        val PACKAGE = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
        val APP_NAME = Regex("""(?<![\w.])(com\.github\.e0ipso\.tokkl(?:\.\w+)?)""")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val STRING = Regex(""""(?:\\.|[^"\\\n])*"""")

        /**
         * The layers, lowest first. Core leaves import no app package. Core data holds what the
         * phone keeps for the account and what reads and changes it. Shared parts are what several
         * features show. The app shell starts the app and wires and navigates the features.
         */
        val LAYERS: List<Set<String>> = listOf(
            setOf("api", "time", "storage", "ui", "platform"),
            setOf("credentials", "capabilities", "session", "cache", "timerstate", "account"),
            setOf("catalog", "picker", "entryfields", "trust", "timernotice"),
            setOf(
                "calendar", "clients", "entry", "favorites", "history", "licences", "notification", "pomodoro",
                "projects", "reminder", "reports", "settings", "shortcuts", "signin", "sync", "tags", "tile",
                "timer", "trash", "widget",
            ),
            setOf(ROOT, "di", "navigation", "lifecycle"),
        )

        /** The imports allowed between packages of one layer. */
        val SAME_LAYER: Map<String, Set<String>> = mapOf(
            // Core data, in the order it builds up: the session reads the capabilities, the cache
            // the session, the timer state the cache, and the account's refresh and reads all four.
            "session" to setOf("credentials", "capabilities"),
            "cache" to setOf("capabilities", "session"),
            "timerstate" to setOf("capabilities", "session", "cache"),
            "account" to setOf("capabilities", "session", "cache", "timerstate"),
            // The entry fields pick projects and tags; a timer notice reads as a catalog problem.
            "entryfields" to setOf("picker"),
            "timernotice" to setOf("catalog"),
            // The Timer page and the history pin entries as timer templates. Settings shows the
            // Pomodoro defaults and the reminder. The Pomodoro widget starts sessions with the
            // Pomodoro defaults and rings through the Pomodoro ringing service.
            "timer" to setOf("favorites"),
            "history" to setOf("favorites"),
            "settings" to setOf("pomodoro", "reminder"),
            "widget" to setOf("pomodoro"),
            // TokklApplication starts Koin; Koin builds the lifecycle observers and the navigation.
            ROOT to setOf("di", "lifecycle", "navigation"),
            "di" to setOf("lifecycle", "navigation"),
        )
    }
}
