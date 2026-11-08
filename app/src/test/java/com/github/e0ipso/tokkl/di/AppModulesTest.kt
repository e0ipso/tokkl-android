package com.github.e0ipso.tokkl.di

import android.app.Application
import android.content.Context
import androidx.work.WorkerParameters
import com.github.e0ipso.tokkl.cache.ChangeSenderEvents
import com.github.e0ipso.tokkl.navigation.TopLevelDestination
import com.github.e0ipso.tokkl.notification.RunningNotice
import com.github.e0ipso.tokkl.platform.AppHookOrder
import com.github.e0ipso.tokkl.pomodoro.PomodoroNotificationMemory
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionPoster
import com.github.e0ipso.tokkl.reminder.ReminderNotice
import com.github.e0ipso.tokkl.reports.ReportExportController
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.SignOutPhase
import com.github.e0ipso.tokkl.shortcuts.PublishedShortcuts
import com.github.e0ipso.tokkl.signin.SignIn
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.ValueStore
import com.github.e0ipso.tokkl.timersync.TimerSyncConnections
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
     * component, the helpers it makes for one view model, such as the sign-in request and the CSV
     * exporter, and the top-level pages the features name. A component handed over as
     * one of its interfaces is bound to that interface, so it needs no entry here. This check
     * reads constructors only; the device's AppGraphTest resolves every definition.
     */
    @Test
    fun everyDefinitionsDependenciesAreDefined() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                WorkerParameters::class,
                CoroutineContext::class,
                Boolean::class,
                Function0::class,
                Function1::class,
                Function2::class,
                Function3::class,
                Flow::class,
                StateFlow::class,
                Instant::class,
                File::class,
                ValueStore::class,
                ClearableStore::class,
                SignOutPhase::class,
                AppHookOrder::class,
                SignIn::class,
                ReportExportController::class,
                TopLevelDestination::class,
                // The named callbacks a definition builds as a lambda or an object, and the
                // collaborators it makes for one component.
                ChangeSenderEvents::class,
                TimerSyncConnections::class,
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
            .map { definition -> definition.qualifier?.value ?: definition.primaryType.java.simpleName }

        assertEquals(SIGN_OUT_OWNERS.sorted(), owners.sorted())
    }

    private companion object {
        /** Each owner by its class, or by its Koin name for a step of no single component. */
        val SIGN_OUT_OWNERS = listOf(
            "Session", "AccountTimerStates", "PomodoroSignOut", "TimerSyncFollower", "BackgroundSend",
            "RunningCheck", "sign-out-http-calls", "AccountRefresh", "ChangeSender", "AccountCache",
            "TimerStateStore", "PomodoroWidgetFieldsStore", "ReminderSync", "ReminderSetting",
            "RunningNotificationSync", "FavoriteShortcutSync", "AccountSettingsStore", "sign-out-capabilities",
            "TimerViewSetting", "PomodoroDefaultsSetting", "ServerTrustStore", "SessionTrust", "LastAnswer",
            "DiscardedChanges", "ExportAccount",
        )
    }
}
