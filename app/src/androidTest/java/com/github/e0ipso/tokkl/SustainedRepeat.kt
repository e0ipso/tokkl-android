package com.github.e0ipso.tokkl

/**
 * Repeats one scenario many times so a race a single run missed can show up.
 * [com.github.e0ipso.tokkl.accessibility.AccessibilityTestRunner] leaves these classes out of an
 * unfiltered run, because the suite already runs the scenario once. Select the class, or pass
 * `includeSustainedRepeat=true`, to run them.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SustainedRepeat
