package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.PackageLayersTest.Companion.layerOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the Gradle modules in the layers of [PackageLayersTest]. Each package lives in one module,
 * and a module depends only on modules whose packages sit no higher than its own lowest package,
 * so no core module depends on the app and the layers stay in order. A module depends only on
 * modules its main sources use, by their packages or their R class, so core:cache, which draws
 * nothing, does not depend on core:ui.
 * A feature module depends on core modules only, never on another feature or the app, and the
 * app depends on every feature. The core modules sit in [CORE_LAYERS] and depend only on lower
 * ones, and no modules depend on each other in a cycle. The test reads settings.gradle.kts, each
 * module's build file and its main sources.
 */
class ModuleLayersTest {

    private val modules = SourceTree.modules
    private val byPath = modules.associateBy { it.path }

    @Test
    fun theTestsReadEveryModule() {
        assertTrue("the settings include the app and core modules", modules.size > 1 && ":app" in byPath)
        modules.forEach { assertTrue("${it.path} has main sources", it.packages.isNotEmpty()) }
        // The whole-app class scans read one directory or jar of classes per module.
        assertEquals(modules.size, ClassReferences.moduleLocations.toSet().size)
    }

    @Test
    fun eachPackageLivesInOneModule() {
        val split = modules.flatMap { module -> module.packages.keys.map { it to module.path } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }

        assertEquals(emptyMap<String, List<String>>(), split)
    }

    @Test
    fun noModuleDependsOnAHigherLayer() {
        val wrong = modules.flatMap { module ->
            val lowest = module.packages.keys.minOf(::layerOf)
            (module.mainDependencies + module.fixtureDependencies).mapNotNull { path ->
                val dependency = byPath.getValue(path)
                val highest = dependency.packages.keys.maxOf(::layerOf)
                "${module.path} -> $path".takeIf { highest > lowest }
            }
        }

        assertEquals("module dependencies against the layers", emptyList<String>(), wrong)
    }

    @Test
    fun everyModuleDependencyIsUsed() {
        val unused = modules.flatMap { module ->
            val used = module.packages.values.flatten().map { it.target }.toSet()
            module.mainDependencies.filter { path ->
                byPath.getValue(path).packages.keys.none { it in used } && path !in module.resourceModules
            }
                .map { "${module.path} -> $it" }
        }

        assertEquals("module dependencies no main source uses", emptyList<String>(), unused)
    }

    @Test
    fun everyModuleUsesOnlyPackagesOfItselfAndItsDependencies() {
        val outside = modules.flatMap { module ->
            val visible = visibleFrom(
                module,
            ).flatMap { byPath.getValue(it).packages.keys }.toSet() + module.packages.keys
            module.packages.flatMap { (from, uses) ->
                uses.filter { it.target !in visible }.map { "${module.path}: $from -> ${it.target} (${it.file})" }
            }
        }.distinct().sorted()

        assertEquals(emptyList<String>(), outside)
    }

    @Test
    fun featureAndCoreModulesDependOnCoreModulesOnly() {
        val wrong = modules.filter {
            it.path.startsWith(
                ":feature:",
            ) || it.path.startsWith(":core:")
        }.flatMap { module ->
            (module.mainDependencies + module.fixtureDependencies).filterNot {
                it.startsWith(
                    ":core:",
                )
            }.map { "${module.path} -> $it" }
        }

        assertEquals("a feature or core module that depends on a feature or the app", emptyList<String>(), wrong)
    }

    @Test
    fun theAppDependsOnEveryFeatureAndReachesEveryModule() {
        val app = byPath.getValue(":app")
        val features = modules.map { it.path }.filter { it.startsWith(":feature:") }
        assertTrue("the settings include feature modules", features.isNotEmpty())
        assertEquals("features the app does not depend on", emptyList<String>(), features - app.mainDependencies)

        val reached = mutableSetOf<String>()
        val queue = ArrayDeque(app.mainDependencies)
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (reached.add(path)) queue += byPath.getValue(path).mainDependencies
        }
        assertEquals("modules the app does not reach", emptyList<String>(), modules.map { it.path } - reached - ":app")
    }

    @Test
    fun everyCoreModuleHasACoreLayer() {
        val core = modules.map { it.path }.filter { it.startsWith(":core:") }.toSet()

        assertEquals(core, CORE_LAYERS.flatten().toSet())
    }

    @Test
    fun coreModulesDependOnLowerCoreLayersOnly() {
        val wrong = modules.filter { it.path.startsWith(":core:") }.flatMap { module ->
            val layer = coreLayerOf(module.path)
            (module.mainDependencies + module.fixtureDependencies)
                .filter { it.startsWith(":core:") && coreLayerOf(it) >= layer }
                .map { "${module.path} -> $it" }
        }

        assertEquals("core module dependencies against CORE_LAYERS", emptyList<String>(), wrong)
    }

    @Test
    fun noModulesDependOnEachOtherInACycle() {
        val graph = modules.associate { it.path to (it.mainDependencies + it.fixtureDependencies) }
        val cycles = mutableListOf<List<String>>()
        val done = mutableSetOf<String>()

        fun visit(path: String, trail: List<String>) {
            if (path in trail) {
                cycles += trail.drop(trail.indexOf(path)) + path
                return
            }
            if (!done.add(path)) return
            graph[path].orEmpty().forEach { visit(it, trail + path) }
        }

        graph.keys.sorted().forEach { visit(it, emptyList()) }
        assertEquals(emptyList<List<String>>(), cycles)
    }

    /** The modules whose classes [module] compiles against: its dependencies and what they expose with `api`. */
    private fun visibleFrom(module: SourceTree.Module): Set<String> {
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(module.mainDependencies)
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (seen.add(path)) queue += byPath.getValue(path).apiDependencies
        }
        return seen
    }

    private fun coreLayerOf(path: String): Int = CORE_LAYERS.indexOfFirst { path in it }.also {
        check(it >= 0) { "$path has no core layer" }
    }

    private companion object {
        /**
         * The core modules in layers, lowest first. The bottom layer needs no other core module:
         * the API client, the time helpers, the design system and the Android plumbing. Above it
         * sit the session, the cache that reads it, and the timer state that reads the cache.
         * The parts several features show and the routes come last.
         */
        val CORE_LAYERS: List<Set<String>> = listOf(
            setOf(":core:api", ":core:time", ":core:ui", ":core:platform"),
            setOf(":core:session"),
            setOf(":core:cache"),
            setOf(":core:timerstate"),
            setOf(":core:shared", ":core:navigation"),
        )
    }
}
