package com.github.e0ipso.tokkl.lifecycle

import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.platform.inStartupOrder

/**
 * What every process does when it starts, whatever starts it: the [AppHook.processStart] of every
 * hook the modules bind, in the order of their [AppHook.phase]. Each hook loads its component at its own step, so the
 * steps keep their order and their threads.
 */
internal class AppStartup(private val hooks: List<AppHook>) {

    fun run() {
        hooks.inStartupOrder().forEach { it.processStart() }
    }
}
