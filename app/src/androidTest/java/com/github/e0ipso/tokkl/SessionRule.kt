package com.github.e0ipso.tokkl

import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.credentials.ApiTokenStore
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.SignInCooldownStore
import com.github.e0ipso.tokkl.timer.TimerViewStore
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
 * state it needs. Order it before the compose rule.
 */
class SessionRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val signedOut = description.getAnnotation(SignedOut::class.java) != null ||
                    description.testClass?.isAnnotationPresent(SignedOut::class.java) == true
                signOut()
                if (!signedOut) {
                    ServerTrustStore(context).confirmCleartext(TokklTestServer.url)
                    Session(context).save(TokklTestServer.url, TokklTestServer.apiToken)
                }
                base.evaluate()
            }
        }

    companion object {
        private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

        /**
         * Deletes the stored server, the token, its Keystore key, the account settings, the
         * server's capabilities, the offline cache, the Timer page's view, the trust decisions and
         * the sign-in cooldowns.
         * A cooldown is kept by server origin, and every test signs in to the same few, so one
         * left by a test that ended early would block sign-in in every later test of the run.
         */
        fun signOut() {
            ServerConfigStore(context).clear()
            ApiTokenStore(context).clear()
            AccountSettingsStore(context).clear()
            CapabilitiesStore(context).clear()
            (context.applicationContext as TokklApplication).cache.clear()
            TimerViewStore(context).clear()
            ServerTrustStore(context).clear()
            SignInCooldownStore(context).run { load().keys.forEach(::clear) }
        }
    }
}
