package com.github.e0ipso.tokkl.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.github.e0ipso.tokkl.entry.EntryFormViewModel
import com.github.e0ipso.tokkl.navigation.EntryFormRequest
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.reports.ReportChoices
import com.github.e0ipso.tokkl.reports.ReportsViewModel
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.definition.BeanDefinition
import org.koin.core.parameter.ParametersHolder
import org.koin.core.parameter.emptyParametersHolder
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
 *
 * Lookups inside a definition's deferred lambdas, such as a flow that calls `get()` when it is
 * collected or an app hook's `inject()`, and `by inject()` in activities, services and receivers,
 * stay outside this test. Each component a hook loads is a definition of its own, which this test
 * resolves.
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
        val (workers, components) = rest.partition {
            ListenableWorker::class.java.isAssignableFrom(
                it.primaryType.java,
            )
        }
        assertTrue("the graph defines view models", models.isNotEmpty())
        assertTrue("the graph defines workers", workers.isNotEmpty())

        val failures = components.mapNotNull { definition -> failureOf(definition) { resolve(definition) } } +
            workers.mapNotNull { definition -> failureOf(definition) { buildWorker(definition) } } +
            whileMainWaits { models.mapNotNull { definition -> failureOf(definition) { makeAndClear(definition) } } }

        assertEquals("definitions that do not resolve", emptyList<String>(), failures)
    }

    /**
     * Each sign-out step runs in the phase its order needs (docs/PRD.md S6): the session first,
     * then the timer state, then what still runs stops, the refresh ends before the drain it can
     * start, and the cache clears before the rest, which follows it. The JVM AppModulesTest checks
     * that the graph binds these owners and no others.
     */
    @Test
    fun eachSignOutStepRunsInItsPhase() {
        val phases = definitions.filter { AccountDataOwner::class in it.secondaryTypes }.associate { definition ->
            val owner: AccountDataOwner = koin.get(definition.primaryType, definition.qualifier)
            (definition.qualifier?.value ?: definition.primaryType.java.simpleName) to owner.signOutPhase
        }

        assertEquals(SIGN_OUT_PHASES, phases)
    }

    private fun resolve(definition: BeanDefinition<*>) {
        koin.get<Any>(definition.primaryType, definition.qualifier)
    }

    /**
     * Makes the view model, with what its screen hands it, then clears it in a store of its own,
     * which cancels its scope.
     */
    private fun makeAndClear(definition: BeanDefinition<*>) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                koin.get(definition.primaryType, definition.qualifier) { screenParameters(definition) }
        }
        try {
            @Suppress("UNCHECKED_CAST")
            ViewModelProvider(store, factory)[definition.primaryType.java as Class<ViewModel>]
        } finally {
            store.clear()
        }
    }

    /**
     * What a view model's screen hands its definition: the entry form's route request and the
     * Reports session choices, and the saved state that the view model's store gives through Koin.
     */
    private fun screenParameters(definition: BeanDefinition<*>): ParametersHolder = when (definition.primaryType) {
        EntryFormViewModel::class ->
            parametersOf(EntryFormRequest(EntryOpening.New(), older = null), SavedStateHandle())

        ReportsViewModel::class -> parametersOf(ReportChoices(), SavedStateHandle())

        else -> emptyParametersHolder()
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
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = try {
                koin.get<ListenableWorker>(
                    ListenableWorker::class,
                    named(workerClassName),
                ) { parametersOf(workerParameters) }.also { made = it }
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

    private fun failureOf(definition: BeanDefinition<*>, resolve: () -> Unit): String? = try {
        resolve()
        null
    } catch (e: Throwable) {
        "${definition.primaryType.qualifiedName} ${definition.qualifier?.value.orEmpty()}: ${generateSequence(
            e,
        ) { it.cause }.last()}"
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

        val SIGN_OUT_PHASES = mapOf(
            "Session" to SignOutPhase.FORGET_SESSION,
            "ExportFilesCleanup" to SignOutPhase.FORGET_SESSION,
            "AccountTimerStates" to SignOutPhase.RETIRE_STATE,
            "PomodoroSignOut" to SignOutPhase.STOP_WORK,
            "TimerSyncFollower" to SignOutPhase.STOP_WORK,
            "BackgroundSend" to SignOutPhase.STOP_WORK,
            "RunningCheck" to SignOutPhase.STOP_WORK,
            "sign-out-http-calls" to SignOutPhase.STOP_WORK,
            "AccountRefresh" to SignOutPhase.FINISH_REFRESH,
            "ChangeSender" to SignOutPhase.FINISH_SENDING,
            "AccountCache" to SignOutPhase.CLEAR_CACHE,
            "TimerStateStore" to SignOutPhase.CLEAR_DATA,
            "PomodoroWidgetFieldsStore" to SignOutPhase.CLEAR_DATA,
            "ReminderSync" to SignOutPhase.CLEAR_DATA,
            "ReminderSetting" to SignOutPhase.CLEAR_DATA,
            "RunningNotificationSync" to SignOutPhase.CLEAR_DATA,
            "FavoriteShortcutSync" to SignOutPhase.CLEAR_DATA,
            "AccountSettingsStore" to SignOutPhase.CLEAR_DATA,
            "CapabilitiesSignOut" to SignOutPhase.CLEAR_DATA,
            "TimerViewSetting" to SignOutPhase.CLEAR_DATA,
            "PomodoroDefaultsSetting" to SignOutPhase.CLEAR_DATA,
            "ServerTrustStore" to SignOutPhase.CLEAR_DATA,
            "SessionTrust" to SignOutPhase.CLEAR_DATA,
            "LastAnswer" to SignOutPhase.CLEAR_DATA,
            "DiscardedChanges" to SignOutPhase.CLEAR_DATA,
            "SessionEntries" to SignOutPhase.CLEAR_DATA,
        )
    }
}
