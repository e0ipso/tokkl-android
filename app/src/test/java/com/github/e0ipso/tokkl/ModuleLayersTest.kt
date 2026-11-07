package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.PackageLayersTest.Companion.layerOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the Gradle modules in the layers of [PackageLayersTest]. Each package lives in one module,
 * and a module depends only on modules whose packages sit no higher than its own lowest package,
 * so no core module depends on the app and the layers stay in order. A module depends only on
 * modules its main sources use, by their packages or their R class, so core:data, which draws
 * nothing, does not depend on core:ui.
 * The test reads settings.gradle.kts, each module's build file and its main sources.
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
            val visible = visibleFrom(module).flatMap { byPath.getValue(it).packages.keys }.toSet() + module.packages.keys
            module.packages.flatMap { (from, uses) ->
                uses.filter { it.target !in visible }.map { "${module.path}: $from -> ${it.target} (${it.file})" }
            }
        }.distinct().sorted()

        assertEquals(emptyList<String>(), outside)
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
}
