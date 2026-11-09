package com.github.e0ipso.tokkl.platform

/**
 * What one module does at three moments of the app's life: when the process starts, when
 * MainActivity comes to the foreground, and when the user allows notifications. Each module
 * declares its hooks as classes of their own and binds them to this interface in its Koin module.
 * The app's composition root lists every hook in the order they run, so no module needs to know
 * the others. TokklApplication runs every [processStart] once, in that order, on the main thread;
 * MainActivity runs every [foregroundStart] on each of its starts, a rotation's too, and every
 * [notificationsAllowed] after a grant, each in order on the I/O dispatcher. A hook loads its
 * component when it first runs, so the process loads each one at its own step.
 */
interface AppHook {

    fun processStart() = Unit

    suspend fun foregroundStart() = Unit

    suspend fun notificationsAllowed() = Unit
}
