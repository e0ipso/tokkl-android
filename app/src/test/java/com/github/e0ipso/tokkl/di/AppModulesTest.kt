package com.github.e0ipso.tokkl.di

import android.app.Application
import android.content.Context
import androidx.work.WorkerParameters
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.cache.ChangeStore
import com.github.e0ipso.tokkl.cache.EntryChanges
import com.github.e0ipso.tokkl.pomodoro.PomodoroRinging
import com.github.e0ipso.tokkl.pomodoro.StoredPomodoroDefaults
import com.github.e0ipso.tokkl.reminder.StoredReminderPreferences
import com.github.e0ipso.tokkl.session.SignOutSteps
import com.github.e0ipso.tokkl.timer.StoredTimerView
import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerSyncClock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

class AppModulesTest {

    /**
     * Every class the graph builds takes, in its constructor, only types the graph defines, so no
     * screen, service or worker meets a missing definition at run time. A [Lazy] dependency counts
     * by the type it wraps.
     *
     * The types listed here are what a definition passes itself: the Android context, the stored
     * values it loads, flows and callbacks it builds, and components it hands over as one of
     * their interfaces.
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
                Instant::class,
                StoredTimerView::class,
                StoredPomodoroDefaults::class,
                StoredReminderPreferences::class,
                SignOutSteps.Stores::class,
                ChangeStore::class,
                TimerSyncClock::class,
                ServerTrust::class,
                EntryChanges::class,
                PauseActions::class,
                PomodoroRinging::class,
            ),
        )
    }
}
