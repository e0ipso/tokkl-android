package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.SourceTree.ROOT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the app's packages in layers. A package imports packages of lower layers, and a package of
 * its own layer only where [SAME_LAYER] lists it. The test reads the main sources of every Gradle
 * module: each `import com.github.e0ipso.tokkl.<package>` and each fully qualified name in code.
 * The R classes belong to no layer; ModuleLayersTest checks the modules that hold the packages.
 *
 * Two rules keep the model between the screens and the storage and wire formats below them. The
 * cache's Room rows never leave core:cache, which maps them to the model. A feature's view models
 * and composables import no core:api wire type, apart from [SERVER_STATE].
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

    @Test
    fun cacheRowsStayInTheCache() {
        val rows = cacheRows()
        assertTrue("the cache declares Room rows", "CachedTimeEntry" in rows && "PendingChange" in rows)
        val outside = mainFiles().filterNot { (module, _) -> module == ":core:cache" }.flatMap { (_, file) ->
            val code = withoutComments(file.readText())
            rows.filter { Regex("""(?<!\w)$it\b""").containsMatchIn(code) }.map { "${file.relativeTo(ROOT_DIR)}: $it" }
        }

        assertEquals("main sources outside core:cache that name a Room row", emptyList<String>(), outside)
    }

    @Test
    fun screensImportNoWireTypes() {
        val wire = wireTypes()
        assertTrue("core:api declares wire types", "ServerTimeEntry" in wire && "TimerState" in wire)
        val screens = mainFiles().filter { (module, file) -> module.startsWith(":feature:") && file.isScreen() }
        assertTrue("the features have screens", screens.size > 10)
        val imported = screens.flatMap { (_, file) ->
            API_IMPORT.findAll(file.readText()).map { it.groupValues[1] }
                .filter { it in wire && it !in SERVER_STATE }
                .map { "${file.relativeTo(ROOT_DIR)}: $it" }
        }

        assertEquals("view models and composables that import an API wire type", emptyList<String>(), imported)
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

    /** Every main source file, with the path of the module that holds it. */
    private fun mainFiles(): List<Pair<String, File>> = SourceTree.modules.flatMap { module ->
        File(module.directory, "src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { module.path to it }.toList()
    }

    /** The classes core:cache stores with Room: its entities and the rows a query reads into. */
    private fun cacheRows(): Set<String> = mainFiles().filter { it.first == ":core:cache" }.flatMap { (_, file) ->
        val text = file.readText()
        ENTITY.findAll(text).map { it.groupValues[1] } + EMBEDDING.findAll(text).map { it.groupValues[1] }
    }.toSet()

    /** The classes core:api reads from and writes to JSON. */
    private fun wireTypes(): Set<String> = mainFiles().filter { it.first == ":core:api" }
        .flatMap { (_, file) -> SERIALIZABLE.findAll(file.readText()).map { it.groupValues[1] } }
        .toSet()

    /** A file of a screen: one that declares a view model or a composable. */
    private fun File.isScreen(): Boolean = readText().let { "@Composable" in it || VIEW_MODEL.containsMatchIn(it) }

    private fun withoutComments(text: String): String =
        text.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("""//[^\n]*"""), " ")

    companion object {
        private val ROOT_DIR: File get() = SourceTree.root
        private val ENTITY = Regex("""@Entity\b[^\n]*\n(?:@[^\n]*\n)*\s*(?:data )?class (\w+)""")
        private val EMBEDDING = Regex("""(?:data )?class (\w+)\(\s*@Embedded\b""")
        private val SERIALIZABLE = Regex(
            """@Serializable\s*\n\s*(?:(?:internal|private|data|enum|sealed) )*(?:class|object|interface) (\w+)""",
        )
        private val VIEW_MODEL = Regex("""\)\s*:\s*ViewModel\(""")
        private val API_IMPORT = Regex("""^import com\.github\.e0ipso\.tokkl\.api\.(\w+)$""", RegexOption.MULTILINE)

        /**
         * The wire types a screen may import. The server owns the timer state, Pomodoro and pause
         * (docs/PRD.md T1): core:timerstate holds and stores it as the server sent it, and the
         * Pomodoro page, the widget and the timer's notices read its parts. The trash page shows
         * each deleted entry as the server lists it, for display only.
         */
        val SERVER_STATE = setOf(
            "TimerState",
            "TimerPause",
            "TimerPomodoro",
            "TimerPomodoroPhase",
            "DiscardedPomodoro",
            "PhaseKind",
            "PhaseTransition",
            "TrashEntry",
        )

        /** The layer of [pkg], counted from 0 for the lowest. */
        fun layerOf(pkg: String): Int = LAYERS.indexOfFirst { pkg in it }.also {
            check(
                it >= 0,
            ) { "$pkg has no layer" }
        }

        /**
         * The layers, lowest first. Core leaves, the model among them, import no app package. Core
         * data holds what the phone keeps for the account and what reads and changes it. Shared
         * parts are the timer state's event stream, what several features show, and the routes by
         * which they open one another's screens. The app shell starts the app and wires and navigates the features.
         */
        val LAYERS: List<Set<String>> = listOf(
            setOf("model", "api", "time", "storage", "ui", "platform"),
            setOf("credentials", "capabilities", "session", "preferences", "cache", "timerstate"),
            setOf("timersync", "catalog", "picker", "entryfields", "trust", "timernotice", "navigation"),
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
            // preferences and the cache the capabilities and the session, and the timer state the
            // cache.
            "session" to setOf("credentials", "capabilities"),
            "preferences" to setOf("capabilities", "session"),
            "cache" to setOf("capabilities", "session"),
            "timerstate" to setOf("capabilities", "session", "cache"),
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
