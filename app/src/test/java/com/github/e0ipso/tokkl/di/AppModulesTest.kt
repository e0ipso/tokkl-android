package com.github.e0ipso.tokkl.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.notification.RunningNotice
import com.github.e0ipso.tokkl.pomodoro.PomodoroNotificationMemory
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionEdits
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionPoster
import com.github.e0ipso.tokkl.reminder.ReminderNotice
import com.github.e0ipso.tokkl.reports.ReportExportController
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.shortcuts.PublishedShortcuts
import com.github.e0ipso.tokkl.signin.SignIn
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.io.File
import java.time.Instant
import kotlin.coroutines.CoroutineContext

class AppModulesTest {

    /**
     * Every class the graph builds takes, in its constructor, only types the graph defines. A
     * [Lazy] dependency counts by the type it wraps.
     *
     * The types listed here are what a definition passes itself: the Android context, the stored
     * values it loads, flows and callbacks it builds, the listeners and clock it builds for one
     * component, the helpers it makes for one view model, such as the sign-in request, the CSV
     * exporter and the Pomodoro page's session commands, and the top-level pages and the routes
     * of the badges the features name. A component handed over as one of its interfaces is bound
     * to that interface, so it needs no entry here. This check reads constructors only; the
     * device's AppGraphTest resolves every definition.
     */
    @Test
    fun everyDefinitionsDependenciesAreDefined() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                WorkerParameters::class,
                // A view model's saved state, which Koin takes from the store that makes the view model.
                SavedStateHandle::class,
                CoroutineContext::class,
                Boolean::class,
                Flow::class,
                StateFlow::class,
                // A function a definition passes, such as a method reference. A suspend function
                // takes its continuation as one more parameter.
                Function0::class,
                Function1::class,
                Function2::class,
                Function3::class,
                Instant::class,
                File::class,
                ValueStore::class,
                ClearableStore::class,
                SignOutPhase::class,
                Int::class,
                SignIn::class,
                ReportExportController::class,
                PomodoroSessionEdits::class,
                TopLevelDestination::class,
                Route::class,
                // The collaborators a definition makes for one component.
                PomodoroNotificationMemory::class,
                PomodoroSessionPoster::class,
                RunningNotice::class,
                ReminderNotice::class,
                PublishedShortcuts::class,
            ),
        )
    }

    /**
     * Sign-out runs one step for each [AccountDataOwner] the graph binds, so this names every one
     * of them: a component that keeps account data and binds no owner would leave its data behind.
     * Nothing is made here; the device's AppGraphTest checks the phase each step runs in.
     */
    @OptIn(KoinInternalApi::class)
    @Test
    fun theGraphBindsTheSignOutStepOfEveryOwner() {
        val koin = koinApplication { modules(appModules) }.koin
        val owners = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
            .filter { AccountDataOwner::class in it.secondaryTypes }
            .map { definition -> definition.primaryType.java.simpleName }

        assertEquals(SIGN_OUT_OWNERS.sorted(), owners.sorted())
    }

    /**
     * Each core module and each feature defines its own components in a Koin module of its own.
     * The app's modules define only the app's classes, and the few components that join every
     * module's or every feature's parts, listed in [JOINED] with why.
     */
    @OptIn(KoinInternalApi::class)
    @Test
    fun theAppDefinesOnlyWhatJoinsTheModules() {
        val koin = koinApplication { modules(lifecycleModule, startupModule, navigationModule) }.koin
        val outside = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
            .map { it.primaryType.java }
            .filterNot { type -> appPackageOf(type.`package`.name) in APP_PACKAGES }
            .map { it.name }
            .filterNot { it in JOINED }

        assertEquals("core or feature classes the app's Koin modules define", emptyList<String>(), outside)
    }

    /** The app's package that [name] is or lies in, such as "di", "" for the root, or null outside the app. */
    private fun appPackageOf(name: String): String? = when {
        name == APP_PACKAGE -> ""
        name.startsWith("$APP_PACKAGE.") -> name.removePrefix("$APP_PACKAGE.").substringBefore('.')
        else -> null
    }

    private companion object {
        const val APP_PACKAGE = "com.github.e0ipso.tokkl"

        /** The app's packages, under [APP_PACKAGE]: its root, di, navhost and lifecycle. */
        val APP_PACKAGES = setOf("", "di", "navhost", "lifecycle")

        /** The classes of other modules that the app defines, since each joins what every module or feature binds. */
        val JOINED = setOf(
            // Every store's load at process start, which MainActivity's first frame waits for.
            "com.github.e0ipso.tokkl.storage.StartupLoads",
            // Every feature's top-level pages and screens, and what the features give the navigation.
            "com.github.e0ipso.tokkl.navigation.TopLevelPages",
            "com.github.e0ipso.tokkl.navigation.FeatureNavigation",
            "java.util.List",
        )

        /** Each owner by its class. */
        val SIGN_OUT_OWNERS = listOf(
            "Session", "AccountTimerStates", "PomodoroSignOut", "TimerSyncFollower", "BackgroundSend",
            "RunningCheck", "CallsSignOut", "AccountRefresh", "ChangeSender", "AccountCache",
            "TimerStateStore", "PomodoroWidgetFieldsStore", "ReminderSync", "ReminderSetting",
            "RunningNotificationSync", "FavoriteShortcutSync", "AccountSettingsStore", "CapabilitiesSignOut",
            "TimerViewSetting", "PomodoroDefaultsSetting", "ServerTrustStore", "SessionTrust", "LastAnswer",
            "DiscardedChanges", "ExportFilesCleanup", "SessionEntries",
        )
    }
}
