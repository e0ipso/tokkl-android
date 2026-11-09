package com.github.e0ipso.tokkl.lifecycle

import com.github.e0ipso.tokkl.platform.AppHook

/**
 * Every module's hooks in the order the app's composition root lists them, [inOrder]. Throws
 * [IllegalArgumentException] when a hook a module binds, one of [bound], is not in that list, or
 * the list names one twice, since nothing would order it.
 */
internal class AppHooks(val inOrder: List<AppHook>, bound: List<AppHook>) {
    init {
        val unordered = bound.filter { hook -> inOrder.none { it === hook } }
        require(unordered.isEmpty()) {
            "hooks missing from the startup order: ${unordered.map { it::class.simpleName }}"
        }
        require(inOrder.distinct().size == inOrder.size) { "a hook is listed twice in the startup order" }
    }
}

/**
 * What every process does when it starts, whatever starts it: the [AppHook.processStart] of every
 * hook, in the order of [hooks]. Each hook loads its component at its own step, so the steps keep
 * their order and their threads.
 */
internal class AppStartup(private val hooks: AppHooks) {

    fun run() {
        hooks.inOrder.forEach { it.processStart() }
    }
}
