package com.github.e0ipso.tokkl.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.definition.BeanDefinition
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

/**
 * Resolves every definition of the app's Koin graph in the app's own process, as the app resolves
 * them, so a definition that asks for a type the graph does not bind fails here instead of in a
 * user's hands. The JVM AppModulesTest checks constructor types only; it cannot see what a
 * definition's lambda asks for, such as a `get()` whose inferred type has no binding.
 *
 * It resolves from the graph TokklApplication started. A second graph built from the same modules
 * would share their singletons. What it makes has no lasting effect:
 *
 * - No singleton starts work when it is made. The ones the app has not made yet are made once, as
 *   their first use would make them. The theme settings apply the stored night mode, as every
 *   MainActivity start does.
 * - View models launch their collections on the main thread. They are made while the main thread
 *   waits, then cleared, which cancels those coroutines before any of them runs.
 * - Workers are only constructed. Nothing calls doWork.
 * - A component that takes [Lazy] parameters in its constructor, as AppStartup takes Koin's
 *   inject(), gets each lazy it holds unread read, since inject() resolves only then. The lazies
 *   other components make for themselves, such as `by lazy` properties, stay unread.
 *
 * Lookups inside a definition's deferred lambdas, such as a flow that calls `get()` when it is
 * collected, and `by inject()` in activities, services and receivers, stay outside this test.
 */
@OptIn(KoinInternalApi::class)
class AppGraphTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin: Koin = GlobalContext.get()
    private val definitions: List<BeanDefinition<*>> =
        koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

    @Test
    fun everyDefinitionResolves() {
        val (models, rest) = definitions.partition { ViewModel::class.java.isAssignableFrom(it.primaryType.java) }
        val (workers, components) = rest.partition { ListenableWorker::class.java.isAssignableFrom(it.primaryType.java) }
        assertTrue("the graph defines view models", models.isNotEmpty())
        assertTrue("the graph defines workers", workers.isNotEmpty())

        val failures = components.mapNotNull { definition -> failureOf(definition) { resolve(definition) } } +
            workers.mapNotNull { definition -> failureOf(definition) { buildWorker(definition) } } +
            whileMainWaits { models.mapNotNull { definition -> failureOf(definition) { makeAndClear(definition) } } }

        assertEquals("definitions that do not resolve", emptyList<String>(), failures)
    }

    /** Sign-out runs one step for each [SignOutOrder], so each has exactly one owner in the graph. */
    @Test
    fun eachSignOutStepHasOneOwner() {
        val orders = koin.getAll<AccountDataOwner>().map { it.signOutOrder }.sorted()

        assertEquals(SignOutOrder.entries.toList(), orders)
    }

    private fun resolve(definition: BeanDefinition<*>) {
        val instance: Any = koin.get(definition.primaryType, definition.qualifier)
        readInjectedLazies(instance)
    }

    /** Makes the view model, then clears it in a store of its own, which cancels its scope. */
    private fun makeAndClear(definition: BeanDefinition<*>) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                koin.get(definition.primaryType, definition.qualifier)
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val model = ViewModelProvider(store, factory)[definition.primaryType.java as Class<ViewModel>]
            readInjectedLazies(model)
        } finally {
            store.clear()
        }
    }

    /**
     * Builds the worker as WorkManager does, through the name KoinWorkerFactory looks it up by,
     * with real WorkerParameters.
     */
    private fun buildWorker(definition: BeanDefinition<*>) {
        val name = definition.primaryType.java.name
        var made: ListenableWorker? = null
        var failure: Throwable? = null
        val workers = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                try {
                    koin.get<ListenableWorker>(ListenableWorker::class, named(workerClassName)) { parametersOf(workerParameters) }.also { made = it }
                } catch (e: Throwable) {
                    failure = e
                    throw e
                }
        }
        try {
            @Suppress("UNCHECKED_CAST")
            TestListenableWorkerBuilder.from(context, definition.primaryType.java as Class<ListenableWorker>)
                .setWorkerFactory(workers)
                .build()
        } catch (e: Throwable) {
            throw failure ?: e
        }
        failure?.let { throw it }
        assertEquals("the worker Koin makes for $name", name, made?.javaClass?.name)
    }

    /**
     * Reads each unread [Lazy] that [instance] holds when its class takes lazies in its
     * constructor, where a definition passes Koin's inject(). A class that takes none keeps its
     * lazies unread.
     */
    private fun readInjectedLazies(instance: Any) {
        val type = instance.javaClass
        val takesLazies = type.declaredConstructors.any { constructor ->
            constructor.parameterTypes.any { Lazy::class.java.isAssignableFrom(it) }
        }
        if (!takesLazies) return
        for (field in type.declaredFields) {
            if (!Lazy::class.java.isAssignableFrom(field.type)) continue
            field.isAccessible = true
            val lazy = field.get(instance) as? Lazy<*> ?: continue
            if (!lazy.isInitialized()) lazy.value
        }
    }

    private fun failureOf(definition: BeanDefinition<*>, resolve: () -> Unit): String? =
        try {
            resolve()
            null
        } catch (e: Throwable) {
            "${definition.primaryType.qualifiedName} ${definition.qualifier?.value.orEmpty()}: ${generateSequence(e) { it.cause }.last()}"
        }

    /** Runs [block] on this thread while the main thread waits, so nothing posted to it meanwhile runs. */
    private fun <T> whileMainWaits(block: () -> T): T {
        val waiting = CountDownLatch(1)
        val release = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            waiting.countDown()
            release.await(WAIT_SECONDS, TimeUnit.SECONDS)
        }
        assertTrue("the main thread did not stop", waiting.await(WAIT_SECONDS, TimeUnit.SECONDS))
        try {
            return block()
        } finally {
            release.countDown()
        }
    }

    private companion object {
        const val WAIT_SECONDS = 30L
    }
}
