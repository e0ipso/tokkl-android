package com.github.e0ipso.tokkl.navhost

import androidx.navigation3.runtime.entryProvider
import com.github.e0ipso.tokkl.navigation.EntryFormOpenings
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.FeatureRoutes
import com.github.e0ipso.tokkl.navigation.NavigationHost
import com.github.e0ipso.tokkl.navigation.Navigator
import com.github.e0ipso.tokkl.navigation.NotificationPrompts
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.routeSerializers
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.modules.polymorphic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/**
 * The navigation as MainActivity assembles it from the app's graph: every route a feature
 * declares, by registering its serializer or by naming it as a top-level page, has a serializer
 * the saved back stack reads it with and an entry the host shows it with. A route that a feature
 * forgets to give either fails here instead of when it opens.
 */
class FeatureRoutesTest {

    private val features = appFeatures.routes
    private val serializers = routeSerializers(features)
    private val host = NavigationHost(Navigator(mutableListOf(), EntryFormOpenings()), NotificationPrompts())
    private val entries = entryProvider<Route> { features.forEach { feature -> with(feature) { addEntries(host) } } }

    @Test
    fun everyRouteAFeatureRegistersHasAnEntry() {
        val declared = features.flatMap(::registeredRoutes)
        assertTrue("the features register their routes", declared.isNotEmpty())
        val missing = declared.map(::defaultOf).filterNot(::hasEntry)

        assertEquals("routes without an entry", emptyList<Route>(), missing)
    }

    @Test
    fun everyTopLevelPageHasASerializerAndAnEntry() {
        for (route in features.flatMap { it.pages }.map { it.route }) {
            assertNotNull("$route has no serializer", serializers.getPolymorphic(Route::class, route))
            assertTrue("$route has no entry", hasEntry(route))
        }
    }

    @Test
    fun theRoutesOfCoreNavigationHaveEntries() {
        assertTrue(hasEntry(Route.Timer))
        assertTrue(hasEntry(Route.Entry(EntryOpening.New(), id = "a")))
    }

    private fun hasEntry(route: Route): Boolean = runCatching { entries(route) }.isSuccess

    /** The route [serializer] reads from an empty object: the object itself, or a class with its defaults. */
    private fun defaultOf(serializer: KSerializer<out Route>): Route = Json.decodeFromString(serializer, "{}")

    /** The serializers of the routes [feature] registers, as the saved back stack gets them. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun registeredRoutes(feature: FeatureRoutes): List<KSerializer<out Route>> {
        val found = mutableListOf<KSerializer<out Route>>()
        val module = SerializersModule { polymorphic(Route::class) { with(feature) { registerRoutes() } } }
        module.dumpTo(
            object : SerializersModuleCollector {
                override fun <T : Any> contextual(
                    kClass: KClass<T>,
                    provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
                ) = Unit

                @Suppress("UNCHECKED_CAST")
                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    if (baseClass == Route::class) found += actualSerializer as KSerializer<out Route>
                }

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
                ) = Unit

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
                ) = Unit
            },
        )
        return found
    }
}
