package com.github.e0ipso.tokkl.di

import android.app.Application
import android.content.Context
import androidx.work.WorkerParameters
import com.github.e0ipso.tokkl.reports.ReportExportController
import com.github.e0ipso.tokkl.session.AccountRefreshListener
import com.github.e0ipso.tokkl.session.SignOutSteps
import com.github.e0ipso.tokkl.signin.SignIn
import com.github.e0ipso.tokkl.storage.ValueStore
import com.github.e0ipso.tokkl.timer.TapTrace
import com.github.e0ipso.tokkl.timerstate.TimerSyncClock
import java.io.File
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

class AppModulesTest {

    /**
     * Every class the graph builds takes, in its constructor, only types the graph defines. A
     * [Lazy] dependency counts by the type it wraps.
     *
     * The types listed here are what a definition passes itself: the Android context, the stored
     * values it loads, flows and callbacks it builds, the listeners and clock it builds for one
     * component, and the helpers it makes for one view model, such as the sign-in request and the
     * CSV exporter. A component handed over as one of its interfaces is bound to that interface,
     * so it needs no entry here. This check reads constructors only; the device's AppGraphTest
     * resolves every definition.
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
                SignOutSteps.Stores::class,
                File::class,
                ValueStore::class,
                AccountRefreshListener::class,
                TimerSyncClock::class,
                SignIn::class,
                TapTrace::class,
                ReportExportController::class,
            ),
        )
    }
}
