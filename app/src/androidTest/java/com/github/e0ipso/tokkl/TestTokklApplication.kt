package com.github.e0ipso.tokkl

import java.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The app as the instrumented tests run it: [TokklApplication] with a [TestClock] as the app's
 * [Clock]. [TokklTestRunner] starts it in place of the manifest's application in every test
 * process. Until a test sets the clock, the app behaves as it does outside the tests.
 */
class TestTokklApplication : TokklApplication() {

    override fun koinModules(): List<Module> = super.koinModules() + testModule

    private val testModule = module {
        single<Clock> { TestClock() }
    }
}
