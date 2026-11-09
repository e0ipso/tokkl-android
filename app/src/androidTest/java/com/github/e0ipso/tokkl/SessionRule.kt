package com.github.e0ipso.tokkl

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.components.accountRefresh
import com.github.e0ipso.tokkl.components.cache
import com.github.e0ipso.tokkl.components.capabilities
import com.github.e0ipso.tokkl.components.initializeTestWorkManager
import com.github.e0ipso.tokkl.components.renewTimerState
import com.github.e0ipso.tokkl.notification.NotificationPermissionStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsStore
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.SignInCooldownStore
import com.github.e0ipso.tokkl.sync.LastAnswerStore
import com.github.e0ipso.tokkl.timer.TimerViewStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** A test, or every test in a class, that starts signed out. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SignedOut

/**
 * Sets the stored session before the activity starts: signed in to the Tokkl
 * test server with its API token, or signed out for a test marked [SignedOut].
 * The test server is plain http, so signing in also records the user's
 * confirmation of its URL, as the sign-in screen does. Every test process
 * shares the app's data, so each test that launches MainActivity sets the
 * state it needs. The notification permission counts as asked, so the first timer start of a run
 * shows no dialog. Order it before the compose rule.
 */
class SessionRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val signedOut = description.getAnnotation(SignedOut::class.java) != null ||
                description.testClass?.isAnnotationPresent(SignedOut::class.java) == true
            signOut()
            // A timer start in a test never brings up the system's permission dialog over
            // the app. NotificationPermissionPromptTest asks with a record of its own.
            runBlocking { NotificationPermissionStore(context, Dispatchers.IO).markAsked() }
            if (!signedOut) {
                ServerTrustStore(context, Dispatchers.IO).confirmCleartext(TokklTestServer.url)
                Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
            }
            base.evaluate()
        }
    }

    companion object {
        private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

        /**
         * Deletes the stored server, the token, its Keystore key, the account settings, the server's capabilities, the
         * held timer state, the offline cache, the Timer view, the Pomodoro defaults, trust and the sign-in cooldowns
         * and the time the server last answered.
         * Also forgets account settings and capabilities already loaded by process-start observers.
         * A cooldown is kept by server origin, and every test signs in to the same few, so one
         * left by a test that ended early would block sign-in in every later test of the run.
         *
         * The queue's background work goes with the cache: a test WorkManager, empty and kept in
         * memory, takes WorkManager's place in this process. Its work runs only when a test's
         * TestDriver says the network is there, so work one test schedules never sends another
         * test's queue, in this process or a later one.
         */
        fun signOut() {
            initializeTestWorkManager(context)
            // Through the app's Session, which keeps the signed-in client in memory.
            Session(context).clear()
            val app = context.applicationContext as TokklApplication
            // Process-start widget and shortcut observers can load these before this rule.
            // Forget the loaded account too, waiting out a refresh before clearing its stores.
            runBlocking { app.accountRefresh.forget() }
            runBlocking { AccountSettingsStore(context, Dispatchers.IO).clear() }
            runBlocking { app.capabilities.forget() }
            runBlocking { CapabilitiesStore(context, Dispatchers.IO).clear() }
            // The timer state of the account the last test signed in as. Its revision is higher
            // than a new account's, so the holder would drop the new account's states.
            app.renewTimerState()
            app.cache.clear()
            runBlocking { TimerViewStore(context, Dispatchers.IO).clear() }
            runBlocking { PomodoroDefaultsStore(context, Dispatchers.IO).clear() }
            ServerTrustStore(context, Dispatchers.IO).clear()
            runBlocking { SignInCooldownStore(context, Dispatchers.IO).run { load().keys.forEach { clear(it) } } }
            runBlocking { LastAnswerStore(context, Dispatchers.IO).save(null) }
        }
    }
}
