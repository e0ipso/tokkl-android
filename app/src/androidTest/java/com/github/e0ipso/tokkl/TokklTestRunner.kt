package com.github.e0ipso.tokkl

import android.app.Application
import android.content.Context
import com.github.e0ipso.tokkl.accessibility.AccessibilityTestRunner

/** Runs the app's instrumented tests in [TestTokklApplication]. */
class TokklTestRunner : AccessibilityTestRunner() {

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, TestTokklApplication::class.java.name, context)
}
