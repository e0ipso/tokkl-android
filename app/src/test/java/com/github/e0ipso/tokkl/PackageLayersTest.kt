package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.SourceTree.ROOT
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keeps the app's packages in layers. A package imports packages of lower layers, and a package of
 * its own layer only where [SAME_LAYER] lists it. The test reads the main sources of every Gradle
 * module: each `import com.github.e0ipso.tokkl.<package>` and each fully qualified name in code.
 * The R classes belong to no layer; ModuleLayersTest checks the modules that hold the packages.
 */
class PackageLayersTest {

    private val imports: Map<String, Set<SourceTree.Use>> by lazy {
        SourceTree.modules.map { it.packages }.reduce { all, module ->
            (all.keys + module.keys).associateWith { all[it].orEmpty() + module[it].orEmpty() }
        }
    }

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

    companion object {
        /** The layer of [pkg], counted from 0 for the lowest. */
        fun layerOf(pkg: String): Int = LAYERS.indexOfFirst { pkg in it }.also {
            check(
                it >= 0,
            ) { "$pkg has no layer" }
        }

        /**
         * The layers, lowest first. Core leaves import no app package. Core data holds what the
         * phone keeps for the account and what reads and changes it. Shared parts are what several
         * features show, and the routes by which they open one another's screens. The app shell
         * starts the app and wires and navigates the features.
         */
        val LAYERS: List<Set<String>> = listOf(
            setOf("api", "time", "storage", "ui", "platform"),
            setOf("credentials", "capabilities", "session", "preferences", "cache", "timerstate", "account"),
            setOf("catalog", "picker", "entryfields", "trust", "timernotice", "navigation"),
            setOf(
                "calendar", "clients", "entry", "favorites", "history", "licences", "notification", "pomodoro",
                "projects", "reminder", "reports", "settings", "shortcuts", "signin", "sync", "tags", "tile",
                "timer", "trash", "widget",
            ),
            setOf(ROOT, "di", "navhost", "lifecycle"),
        )

        /** The imports allowed between packages of one layer. */
        val SAME_LAYER: Map<String, Set<String>> = mapOf(
            // Core data, in the order it builds up: the session reads the capabilities, the shared
            // preferences and the cache the capabilities and the session, the timer state the
            // cache, and the account's refresh and reads the capabilities, the session, the cache
            // and the timer state.
            "session" to setOf("credentials", "capabilities"),
            "preferences" to setOf("capabilities", "session"),
            "cache" to setOf("capabilities", "session"),
            "timerstate" to setOf("capabilities", "session", "cache"),
            "account" to setOf("capabilities", "session", "cache", "timerstate"),
            // The entry fields pick projects and tags; a timer notice reads as a catalog problem.
            "entryfields" to setOf("picker"),
            "timernotice" to setOf("catalog"),
            // The Timer page shows the history, the calendar and the timer templates and opens the
            // entry form, and the history pins entries as timer templates. Settings opens Licences.
            // The notification feature's Koin module defines the reminder's notification too.
            "timer" to setOf("favorites", "history", "calendar", "entry"),
            "history" to setOf("favorites"),
            "settings" to setOf("licences"),
            "notification" to setOf("reminder"),
            // TokklApplication starts Koin; Koin builds the lifecycle observers and the navigation host.
            ROOT to setOf("di", "lifecycle", "navhost"),
            "di" to setOf("lifecycle", "navhost"),
        )
    }
}
